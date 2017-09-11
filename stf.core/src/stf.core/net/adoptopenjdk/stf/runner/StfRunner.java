/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.stf.runner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.codeGeneration.Stage;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.PlatformFinder;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.OrderedProperties.PropertyFileDetails;
import net.adoptopenjdk.stf.extensions.Stf;
import net.adoptopenjdk.stf.extensions.StfExtensionBase;
import net.adoptopenjdk.stf.extensions.interfaces.StfExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginRootInterface;
import net.adoptopenjdk.stf.processes.definitions.JlinkDefinition;
import net.adoptopenjdk.stf.processes.definitions.JmodDefinition;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;
import net.adoptopenjdk.stf.runner.modes.PluginList;


/**
 * This class is the entry point for running STF.
 */
public class StfRunner {
    private static final Logger logger = LogManager.getLogger(StfRunner.class.getName());

	private String[] args;
	private String propertiesList = null;
	private ArrayList<PropertyFileDetails> initialPropertyFiles = null;
	private String testDir = null;
	
	
	public static void main(String[] args) throws StfException {
		// Run STF to generate the perl code
		try {
			StfRunner runner = new StfRunner(args);
			runner.executeTest();
			System.exit(0);
		} catch (StfError e) {
			String boldStart = PlatformFinder.isLinux() ? "\u001B[1m" : "";
			String boldEnd   = PlatformFinder.isLinux() ? "\u001B[0m" : "";
			logger.fatal(boldStart + "** ERROR **  " + e.getMessage() + boldEnd);
			System.exit(1);
		}
	}
	

	public StfRunner(String[] args) throws StfException {
		this.args = args;
		
        int i = 0;
        while (i < args.length) {
        	String arg = args[i++];
        	
			if (arg.equals("-properties")) {
	    		propertiesList = getNextArg(args, i++, "properties");

			} else if (arg.equals("-testDir")) {
	    		testDir = getNextArg(args, i++, "testDir");

			} else {
    			throw new StfException("Unknown argument: " + arg);
    		}
		}
        
    	if (propertiesList == null) throw new StfException("No argument supplied for 'propertiesList'");
    	if (testDir == null) throw new StfException("No argument supplied for 'testDir'");
	}
	
	
	private String getNextArg(String[] args, int i, String propertyName) throws StfException {
		if (i >= args.length) {
			throw new StfException("No value supplied for final property called: '" + propertyName + "'");
		}
        
		String value = args[i];
		if (value.startsWith("-")) { 
			throw new StfException("No value supplied for the '" + propertyName + "' property.");
		}
		
		return value;
	}

	
	/**
	 * This is the key method for starting a STF based test.
	 *  
	 * It is responsible for: 
	 *   - creating an instance of the plugin class.
	 *   - validating the interface of the plugin object.
	 *   - Running the generation methods of the plugin.
	 *   
	 * @throws StfException if anything goes wrong.
	 */
	private void executeTest() throws StfException {
		// Convert the comma separated list of property files into File objects
		// This won't be the final list of properties but it's enough to bootstrap the plugin.
		initialPropertyFiles = new ArrayList<PropertyFileDetails>();
		for (String fileName : propertiesList.split(",")) {
			if (fileName.trim().isEmpty()) {
				continue;
			}
			File propertyFile = new File(fileName.trim());
			initialPropertyFiles.add(new PropertyFileDetails(propertyFile, true));
		}
		
		// Create objects to represent environmental data
		StfEnvironmentCore environmentCore = new StfEnvironmentCore(initialPropertyFiles, buildFullArgumentList(null), testDir);

		// Logging detail set at runtime. So tell log4j what level we want
		if (environmentCore.isSuperVerboseSet()) {
			setLog4jLevel(Level.TRACE);
		} else if (environmentCore.isVerboseSet()) {
			setLog4jLevel(Level.DEBUG);
		}

		// Log the invocation arguments
		if (logger.isDebugEnabled()) {
			logger.debug("StfRunner.java started. Arguments:");
			int i = 0;
			while (i < args.length) {
				logger.debug("  " + i + ") " + args[i++]);
			}
		}

		// Log the property files being used
		if (logger.isDebugEnabled()) {
			logger.debug("Bootstrapping property files:");
			for (String fileName : propertiesList.split(",")) {
				if (fileName.trim().isEmpty()) {
					continue;
				}
				File propertyFile = new File(fileName.trim());
				logger.debug("  " + propertyFile.getAbsolutePath());
			}
		}

		// Get hold test-root, ie, the directory containing test cases. 
		// Fail if it's not set to at a valid looking value. 
		//File testRoot = new File(environmentCore.getProperty(Stf.ARG_TEST_ROOT));
		//logger.debug("Test-root=" + testRoot.getAbsolutePath());
		//if (!testRoot.exists()) {
		//	throw new StfError("Invalid test root. Directory does not exist: " + testRoot.getAbsolutePath());
		//}
		//if (!testRoot.isDirectory()) {
		//	throw new StfError("Invalid test root. Not a directory: " + testRoot.getAbsolutePath());
		//}
		
		// If the '-list' argument is used then search for all test cases in the workspace
		if (environmentCore.getBooleanProperty(Stf.ARG_LIST_TESTS) == true) {
			PluginList pluginList = new PluginList();
			pluginList.searchAndListTests(environmentCore, environmentCore.getTestRoots());
			return;  // All done
		}
		
		// Create the plugin class. Part 1.
		// As part of the bootstrapping we need an instance of the plugin class to 
		// find out which extension classes it's using.
		String testName = environmentCore.getProperty(Stf.ARG_TEST);
		PluginFinder pluginFinder = new PluginFinder(environmentCore.getTestRoots(), testName);
        
		// Setup the STF class loader to use the dependencies used by the plugins project
		ClassPathConfigurator.configureClassLoader(environmentCore, pluginFinder.getProjectName());

		// Create the plugin class. Part 2.
		// Now that the classpath has been configured to use the dependencies which the plugin 
		// requires we can create and use an instance of the plugin class.
		StfPluginRootInterface plugin = createTestObject(pluginFinder.getPluginClassName());
		
		// Get hold of the key methods from the plugin class
		Method helpMethod = null;
		Method pluginInitMethod = null;
		Method setupMethod = null;
		ArrayList<Method> executeMethods = new ArrayList<Method>();
		Method lastExecuteMethod = null;
		Method teardownMethod = null;
		Class<? extends StfPluginRootInterface> pluginClass = plugin.getClass();
		String pluginName = pluginClass.getName();
		for (Method method : pluginClass.getMethods()) {
			if (method.getName().equals("help")) {
				helpMethod = method;
			} else if (method.getName().equals(Stage.INITIALISATION.getMethodName())) {
				pluginInitMethod = method;
			} else if (method.getName().equals(Stage.SETUP.getMethodName())) {
				setupMethod = method;
			} else if (method.getName().startsWith(Stage.EXECUTE.getMethodName())) {
				executeMethods.add(method);
				lastExecuteMethod = method;
			} else if (method.getName().equals(Stage.TEARDOWN.getMethodName())) {
				teardownMethod = method;
			}
		}
	
		// Verify that the plugin contains the expected methods
		validateMethod(pluginName, helpMethod, "help");
		validateMethod(pluginName, pluginInitMethod, Stage.INITIALISATION.getMethodName());
		validateMethod(pluginName, setupMethod, Stage.SETUP.getMethodName());
		validateMethod(pluginName, lastExecuteMethod, Stage.EXECUTE.getMethodName());  // ie, at least 1 execute method
		validateMethod(pluginName, teardownMethod, Stage.TEARDOWN.getMethodName());

		// Verify that all of the plugin init/setUp/execute/tearDown methods take an identical set of parameters
		Class<?>[] pluginMethodParameters = pluginInitMethod.getParameterTypes();
		validateParametersMatch(pluginName, pluginMethodParameters, setupMethod);
		for (Method executeMethod : executeMethods) {
			validateParametersMatch(pluginName, pluginMethodParameters, executeMethod);
		}
		validateParametersMatch(pluginName, pluginMethodParameters, teardownMethod);

		// Find the longest plugin method name.
		// This allows neat formatting of the command summary table.
		int longestStageName = teardownMethod.getName().length();
		for (Method executeMethod : executeMethods) {
			longestStageName = Math.max(longestStageName, executeMethod.getName().length());
		}
		PerlCodeGenerator.setStageNameLength(longestStageName);
		
		// Now that the extensions used by the plugin are known we can build a full list of 
		// all the property files that are used. 
		// This is the first time that we can pull in properties/configuration for each extension  
		ArrayList<PropertyFileDetails> propertyFiles = buildFullPropertyFileList(pluginMethodParameters);
		ArrayList<Argument> allArguments = buildFullArgumentList(pluginMethodParameters);

		// Initial bootstrapping has been completed.
		// Now that the plugin class has been loaded and examined we know what extensions it requires.
		// So now rebuild the environment object with the full set of property files.
		environmentCore = new StfEnvironmentCore(propertyFiles, allArguments, testDir);

		// Tell significant STF objects about the environmentCore object.
		// The reference to environment core is set as a class static value to 
		// prevent it being accessible to test code.
		JmodDefinition.setEnvironmentCore(environmentCore);
		JlinkDefinition.setEnvironmentCore(environmentCore);
		
		// To allow cross platform development optionally force the platform setting
		String platformOverride = environmentCore.getProperty(Stf.ARG_PLATFORM);
		if (platformOverride != "") {
			String originalPlatform = PlatformFinder.getPlatformAsString();
			PlatformFinder.forcePlatform(platformOverride);
			logger.info("Forcing platform to '" + PlatformFinder.getPlatformAsString() + "' instead of '" + originalPlatform + "'");
		}
		
		// Now that the full set of properties has been loaded we can dump and verify them
		environmentCore.dumpAndCheckAllProperties(allArguments);
		
		// If run with '-help' then provide help on all of the extensions in use
		if (environmentCore.getBooleanProperty(Stf.ARG_HELP) == true) {
			generateHelpText(plugin, helpMethod, pluginInitMethod.getParameterTypes());
			return;
		} 
		
		// Summarise what we are doing
		logger.debug("Running script generation for:");
		logger.debug("  test:      '" + testName + "'");
		logger.debug("  test-args: '" + environmentCore.getProperty(Stf.ARG_TEST_ARGS) + "'");
		logger.debug("  platform:  '" + PlatformFinder.getPlatformAsString() + "'");

		// Run the code in the plugin methods to produce the perl files
		logger.debug("Generating test scripts");
		ArrayList<PerlCodeGenerator> generators = new ArrayList<PerlCodeGenerator>();
		runStage(plugin, pluginInitMethod, environmentCore, Stage.INITIALISATION, true);
		generators.add(runStage(plugin, setupMethod, environmentCore, Stage.SETUP, true));
		int executeRepeatCount = environmentCore.getPropertyAsInt(Stf.ARG_REPEAT_COUNT);
		for (Method executeMethod : executeMethods) {
			for (int i=0; i<executeRepeatCount; i++) {
				generators.add(runStage(plugin, executeMethod, environmentCore, Stage.EXECUTE, i==executeRepeatCount-1));
			}
		}
		generators.add(runStage(plugin, teardownMethod, environmentCore, Stage.TEARDOWN, true));
		
		// Build a string which lists all of the execute scripts
		StringBuilder executeMethodNames = new StringBuilder();
		for (Method executeMethod : executeMethods) {
			executeMethodNames.append(executeMethod.getName() + "\n");
		}
		
		// Create a file for stf.pl which lists all execute stages
		DirectoryRef testDir = environmentCore.getResultsDir().getParent();
		FileRef excuteStagesFile = testDir.childFile("executeStages.txt");
		writeFile(excuteStagesFile.asJavaFile(), executeMethodNames.toString());
		
		// Produce summary of the commands generated. 
		// This gives a high level view of what the test case is up to.
		logger.info("");
		logger.info("Test command summary:");
		logger.info(String.format("  %s  %-" + (longestStageName-1) + "s %s           %s", "Step", "Stage", "Command", "Description"));
		StringBuilder stageFiller = new StringBuilder();
		for (int i=0; i<longestStageName; i++) { 
			stageFiller.append("-");
		}
		logger.info(" -----+" + stageFiller.toString() + "+-----------------+------------");
		for (PerlCodeGenerator generator : generators) {
			generator.summariseGeneratedCommands();
		}
		
		// Fail the run if the test has been invoked with unused test specific args
		environmentCore.verifyTestArgsUsed();
	}


	/**
	 * This method takes a list of the classes used by the plugins setup/execute/etc methods and 
	 * builds a full list of all the property files which need to be loaded.
	 * 
	 * @param pluginMethodParameters is the classes used by all of the plugin methods.
	 * @return an ArrayList containing details on all property files, ordered from most to least important.
	 * @throws StfException if a mandatory property file does not exist.
	 */
	private ArrayList<PropertyFileDetails> buildFullPropertyFileList(Class<?>[] pluginMethodParameters) throws StfException {
		ArrayList<PropertyFileDetails> propertyFiles = new ArrayList<PropertyFileDetails>();
		
		// The first property file contains the command line values from stf.pl
		PropertyFileDetails customisationProperties = initialPropertyFiles.get(0);
		propertyFiles.add(customisationProperties);

		// Add in the users personal property files for each extension
		File homeDirectory = new File(System.getProperty("user.home"));
		for (Class<?> c : pluginMethodParameters) { 
	        String propertyFileName = convertExtensionNameToPropertyFileName(c.getSimpleName());
	        PropertyFileDetails propFile = resolvePropertyFile(homeDirectory, "." + propertyFileName, false);
			propertyFiles.add(propFile);
		}

		// Add in personal STF property file
		propertyFiles.add(resolvePropertyFile(homeDirectory, ".stf.properties", false));

		// Add in the mandatory property file for each extension (from the workspace)
		for (Class<?> extensionClass : pluginMethodParameters) {
			// Reference the property file for the current extension in the projects config directory.
			propertyFiles.add(resolvePropertyFile(extensionClass, true));
		}
		
		// Add in final level of properties - the mandatory STF property file (again from the workspace)
		propertyFiles.add(resolvePropertyFile(Stf.class, true));

		// Log the details of all property files that are to be used.
		if (logger.isDebugEnabled()) {
			logger.debug("Property files used by test:");
			for (PropertyFileDetails p : propertyFiles) {
				String fileStatus = p.file.exists() ? "   exists" : "does-not-exist";
				logger.debug(String.format("  %-80s %7s", p.file.getAbsolutePath(), fileStatus));
			}
		}
		
		return propertyFiles;
	}


	/**
	 * This method returns all of the arguments supported by a set of STF extensions.
	 * 
	 * @param extensionClasses is an array of Extension classes.
	 * @return an arrayList of all arguments.
	 * @throws StfException
	 */
	private ArrayList<Argument> buildFullArgumentList(Class<?>[] extensionClasses) throws StfException {
		ArrayList<Argument> allArguments = new ArrayList<Argument>();
		
		// Add arguments supported by the listed extension classes
		if (extensionClasses != null) {
			for (Class<?> extensionClass : extensionClasses) { 
				StfExtension extension = createExtension(extensionClass);
				Argument[] extensionArguments = extension.getSupportedArguments();
				if (extensionArguments != null) { 
					allArguments.addAll(Arrays.asList(extensionArguments));
				}
			}
		}
		
		// Finally add the arguments used by the mandatory STF extension
		Argument[] stfArguments = new Stf().getSupportedArguments();
		allArguments.addAll(Arrays.asList(stfArguments));
		
		return allArguments;
	}
	
	
	/**
	 * Returns information about a property file.
	 * 
	 * @param extensionClass is the extension class whose property file we want to find.
	 * @param propertyFileName is the name of the property file.
	 * @param mustExist is set to true if the file is mandatory.
	 * @return a File object for the property file.
	 * @throws StfException if the directory does not exist or a mandatory property file does not exist.
	 */
	private PropertyFileDetails resolvePropertyFile(Class<?> extensionClass, boolean mustExist) throws StfException {
		// Work out the config directory for the current extension
		String projectForClass = StfClassLoader.getProjectName(extensionClass.getName());
		File projectDir = new File(projectForClass).getParentFile();
		File configDir = new File(projectDir, "config");

		// The property file for the current extension is expected to live in the config 
		// directory for the classes project. So build path to the expected location.
		String propertyFileName = convertExtensionNameToPropertyFileName(extensionClass.getSimpleName());
		
		return resolvePropertyFile(configDir, propertyFileName, true);
	}

	
	/**
	 * Returns information about a property file.
	 * 
	 * @param extensionClass is the extension class whose property file we want to find.
	 * @param propertyFileName is the name of the property file.
	 * @param mustExist is set to true if the file is mandatory.
	 * @return a File object for the property file.
	 * @throws StfException if the directory does not exist or a mandatory property file does not exist.
	 */
	private PropertyFileDetails resolvePropertyFile(File configDir, String propertyFileName, boolean mustExist) throws StfException {
		if (!configDir.exists()) {
			throw new StfException("Directory does not exist: " + configDir.getAbsolutePath());
		}
		
		File propertyFile = new File(configDir, propertyFileName);
		if (mustExist && !propertyFile.exists()) {
			throw new StfException("Unable to find property file at expected location: " + propertyFile.getAbsolutePath());
		}
		
		return new PropertyFileDetails(propertyFile, mustExist);
	}

	
	/** 
	 * Converts the classname of an extension into the expected property file name.
	 * The property file name follows the Eclipse project naming convention and uses a 
	 * '.' character between words with no camel case.
	 * eg. A name of 'StfSharedClasses' is converted to 'stf.shared.classes.properties'
	 * 
	 * @param extensionName is the simple name of the extension class.
	 * @return The name of the corresponding property file.
	 */
	private String convertExtensionNameToPropertyFileName(String extensionName) {
		StringBuilder propertyFileName = new StringBuilder();
		for (int i=0; i<extensionName.length(); i++) { 
			char c = extensionName.charAt(i);
			if (Character.isUpperCase(c) && i!=0) {
				propertyFileName.append('.');
			}
			propertyFileName.append(Character.toLowerCase(c));
		}

		propertyFileName.append(".properties");
		String fileName = propertyFileName.toString();
		
		// Allow the extension classes to end in 'Extension' but not have this in their property file name.
		fileName = fileName.replace(".extension.properties", ".properties");

		return fileName;
	}

	
	// Verify that 2 sets of parameters are identical
	private void validateParametersMatch(String pluginName, Class<?>[] expectedParameters, Method otherMethod) throws StfException {
		String expected = Arrays.toString(expectedParameters);
		String other    = Arrays.toString(otherMethod.getParameterTypes());
		
		if (!other.equals(expected)) {
			throw new StfException("Parameters for the 'pluginInit' and the '" + otherMethod.getName() + "' method do not match: " 
						+ " " + expected + " vs. " + other 
						+ " For plugin: " + pluginName);
		}	
	}


	/**
	 * Verifies that a method has been found.
	 * 
	 * @param pluginName is the name of the plugin class we are running.
	 * @param method is one of the plugins expected methods.
	 * @param methodName is the name of the method we expect the plugin to have.
	 * @throws StfException if the plugin class does not contain the expected method.
	 */
	private void validateMethod(String pluginName, Method method, String methodName) throws StfException {
		if (method == null) {
			throw new StfException("STF plugin does not contain a mandatory method. Plugin: " + pluginName + " Method: " + methodName);
		}

		// Verify that the generation method doesn't return anything
		if (!method.getReturnType().equals(Void.TYPE)) {
			throw new StfException("Method return type must be void. Plugin: " + pluginName + " Method: " + method.getName());
		}
	}


	/**
	 * This method executes one of the plugins mandatory methods - pluginInit, setup, execute or teardown.
	 *  
	 * @param pluginObject is an instance of a plugin test object.
	 * @param targetMethod is a reference to the method to run
	 * @param environmentCore contains environmental details.
	 * @param stage allows different perl code to be generated for say setup or execute steps. 
	 * @param doExitCommand set to true if the exit command needs to be added to the end of the script.
	 * @throws StfException
	 */
	public PerlCodeGenerator runStage(StfPluginRootInterface pluginObject, Method targetMethod, StfEnvironmentCore environmentCore, Stage stage, boolean doExitCommand) throws StfException {
		environmentCore.setStage(stage);
		
		// Create the perl output file
		FileRef perlFile = null;
		if (stage != Stage.INITIALISATION) {
			String outputFileName = targetMethod.getName() + ".pl";
			perlFile = environmentCore.createDirectoryRef(testDir).childFile(outputFileName);
		}
		PerlCodeGenerator generator = new PerlCodeGenerator(environmentCore, targetMethod.getName(), stage, perlFile);
		StfExtensionBase extensionBase = new StfExtensionBase(environmentCore, generator);

		// Create the extension objects. These are needed by the plugin method
		Class<?>[] extensionClasses = targetMethod.getParameterTypes();
		StfExtension[] extensionInstances = new StfExtension[extensionClasses.length];
		for (int i=0; i<extensionClasses.length; i++) { 
			extensionInstances[i] = createExtension(extensionClasses[i]);
			extensionInstances[i].initialise(environmentCore, extensionBase, generator);
		}
		
		// Run the plugin method, allowing it to generate perl code.
		try {
			targetMethod.invoke(pluginObject, (Object[]) extensionInstances);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof StfException) {
				throw (StfException) cause;
			} else if (cause instanceof RuntimeException) { 
				throw (RuntimeException) cause;
			} else {
				throw new StfException("Generation failed for " + targetMethod.getName() + "(): " + cause, e);
			}
		} catch (IllegalAccessException e) {
			throw new StfException("Failed to call plugin method: " + targetMethod.getName(), e);
		} catch (IllegalArgumentException e) {
			throw new StfException("Failed to call plugin method: " + targetMethod.getName(), e);
		}
		
		if (generator != null) {
			generator.closeOutput(doExitCommand);
		}

		// Prevent orphan processes.
		// Check that all processes started in the current stage have code generated to
		// make sure that they have either completed naturally or have been killed.
		String sourceFileName = pluginObject.getClass().getSimpleName() + ".java";
		extensionBase.verifyNoOrphanChildProcesses(sourceFileName);
		
		return generator;
	}

	
	/**
	 * Create and initialise an extension class.
	 * 
	 * @param extensionClass is the class to be created.
	 * @param environmentCore provides access to environmental data.
	 * @param generator is an object which can be used to generate perl code.
	 * @return an instance of a StfExtension
	 * @throws StfException if anything goes wrong.
	 */
	private StfExtension createExtension(Class<?> extensionClass) throws StfException {
		// Try to create an instance of the extension class.
		Object newObject;
		try {
			newObject = extensionClass.newInstance();		
		} catch (IllegalAccessException e) {
			throw new StfException("Failed to create instance of extension class: " + extensionClass.getName(), e);
		} catch (InstantiationException e) {
			throw new StfException("Failed to create instance of extension class: " + extensionClass.getName(), e);
		}

		// Verify that we have created an extension object
		if (!(newObject instanceof StfExtension)) {
			throw new StfException("Extension class '" + extensionClass.getName() + "' does not implement " + StfExtension.class.getName());
		}
		StfExtension extension = (StfExtension) newObject;

		return extension;
	}
	
	
	/**
	 * This method creates an instance of the test plugin.
	 * ie. it creates an object described by the -test property.
	 * 
	 * @param testClassName is the full class name of the test to create.
	 * @return an instance of the test's plugin.
	 * @throws StfException
	 */
	private StfPluginRootInterface createTestObject(String testClassName) throws StfException {
		// Load the class for the plugin
		Class<?> pluginClass;
		try {
			pluginClass = Class.forName(testClassName);
		} catch (ClassNotFoundException e) {
			throw new StfException("Test class is not on classpath: '" + testClassName + "'", e);
		}
		
		// Verify that the plugin is really a test plugin
		if (!(StfPluginRootInterface.class.isAssignableFrom(pluginClass))) {
			throw new StfError("The '" + testClassName + "' class is not a STF test case. It does not implement '" + StfPluginRootInterface.class.getSimpleName() + "'");
		}

		// Create an instance of the plugin class
		Object pluginObject;
		try {
			pluginObject = pluginClass.newInstance();
		} catch (IllegalAccessException e) {
			throw new StfException("Failed to create instance of test class: '" + testClassName + "'", e);
		} catch (InstantiationException e) {
			throw new StfException("Failed to create instance of test class: '" + testClassName + "'", e);
		}
		
		return (StfPluginRootInterface) pluginObject;
	}
	

	// Nasty log4j setup code to force logging to the level we actually want.
	//
	// The log4j config file sets the default level at runtime because it is the 
	// stf user that needs to decide how much to see. 
	private void setLog4jLevel(Level level) {
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		LoggerConfig loggerConfig = ctx.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
		loggerConfig.setLevel(level);
		ctx.updateLoggers();
	}

	
	// Creates a file with the supplied string
    private void writeFile(File outputFile, String content) throws StfException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(outputFile));
            output.write(content);
        } catch (IOException e) {
            throw new StfException("Failed to write to file: " + outputFile.getAbsolutePath(), e);
        } finally {
            if (output != null) {
            	try {
					output.close();
				} catch (IOException e) {
					throw new StfException("Failed to close file: " + outputFile.getAbsolutePath(), e);
				}
            }
        }
    }


	private void generateHelpText(StfPluginRootInterface pluginObject, Method helpMethod, Class<?>[] pluginExtensionClasses) throws StfException {
		HelpTextGenerator help = new HelpTextGenerator(PlatformFinder.isLinux());
		
		help.outputLine();
		help.outputHeading("NAME");
		help.outputText("stf - generates and executes system test perl scripts");

		help.outputHeading("SYNOPSIS");
		help.outputText("stf.pl -workspace-root=<directory> -test=id [OPTIONS]");
		
		help.outputHeading("DESCRIPTION");
		help.outputText("stf (System Test Framework) generates and executes system tests. "
                      + "The test name is used to locate Java code containing a test plugin. Stf runs the "
                      + "plugins setup, execute and teardown methods to create platform specific scripts "
                      + "for running that test.\n"
                      + "The generated perl scripts are then executed unless '-dry-run' is specified.\n"
                      + "A return code of '0' indicates success");
		
		help.outputHeading("OPTION VALUES");
		help.outputSection("Option layering");
		help.outputText("All options must have a value, but this can generally be the default value. "
					  + "The search order for an options value is:\n"
					  + ":space: - command line arguments\n"
					  + ":space: - user specific extension properties, eg. ~/.stf.runtimes.properties\n"
					  + ":space: - user specific stf properties, eg ~/.stf.properties\n"
					  + ":space: - extensions defaults, eg. stf.runtimes/config/stf.runtimes.properties\n"
					  + ":space: - default stf properties file, eg. stf/config/stf.properties\n");

		help.outputSection("Option evaluation");
		help.outputText("There are special rules for parameter values which start with the following characters:\n"
					  + ":space: '$' - The value is replaced with the contents of corresponding environment variable. "
					  + "For example, 'javahome-execute=$JAVA_HOME'\n"
					  + ":space: '*' - Refers to the contents of another parameter. Resolution of the referenced "
					  + "parameter starts at the highest level.");


		// Build list of extensions used by the current test case
		ArrayList<Class<?>> extensionClasses = new ArrayList<Class<?>>();
		extensionClasses.addAll(Arrays.asList(pluginExtensionClasses));
		extensionClasses.add(Stf.class);
		
		// Get each extension to describe its options
		help.outputHeading("OPTIONS");
		Collections.reverse(extensionClasses);
		for (Class<?> extensionClass : extensionClasses) {
			StfExtension extension = createExtension(extensionClass);
			extension.help(help);
		}
		
		// Call the help method for the current test
		try {
			helpMethod.invoke(pluginObject, help);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof StfError) {
				throw (StfError) e.getCause();
			}
			throw new StfException("Help text generation failed for " + helpMethod.getName() + "(): " + e.getCause(), e);
		} catch (IllegalAccessException e) {
			throw new StfException("Failed to call plugin method: " + helpMethod.getName(), e);
		} catch (IllegalArgumentException e) {
			throw new StfException("Failed to call plugin method: " + helpMethod.getName(), e);
		}

		help.outputHeading("NOTES");
		help.outputSection("Development environment");
		help.outputText("To simplify the repeated execution of stf.pl it might be worth making a "
				      + "couple of changes to your development environment: \n"
				      + "1) Simplify command lines by putting unchanging options, such as 'test-root' "
				      + "or 'systemtest_prereqs', into a '$HOME/.stf.properties' file.\n"
				      + "2) On linux machines set an alias to stf.pl. for example "
				      + "'alias stf=\"$HOME/workspaces/java-testing/stf.core/scripts/stf.pl\"'. On Windows machines "
				      + "add the directory containing Stf.pl to the system path.");
		
		help.outputSection("Example commands");
		help.outputText("$ stf -test-root=$HOME/git/openjdk-systemtest -test=LambdaLoadTest");
		help.outputText("$ stf -test-root=$HOME/git/openjdk-systemtest -test=MathLoadTest -test-args=\"workload=autoSimd\"");
		help.outputText("$ stf -test-root=$HOME/git/openjdk-systemtest -test=MathLoadTest -test-args=\"workload=bigDecimal\" -dry-run");
		
		help.outputLine();
	}
}