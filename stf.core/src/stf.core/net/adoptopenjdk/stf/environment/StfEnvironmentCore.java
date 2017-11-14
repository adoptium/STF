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

package net.adoptopenjdk.stf.environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.codeGeneration.Stage;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.LayeredProperties;
import net.adoptopenjdk.stf.environment.properties.OrderedProperties;
import net.adoptopenjdk.stf.environment.properties.OrderedProperties.PropertyFileDetails;
import net.adoptopenjdk.stf.extensions.Stf;


/** 
 * This class provides full access to environmental data.
 * 
 * It is expected that this class is used by STF itself and also by STF 
 * extensions, but not by test plugin code.
 * If this class can be accessed by plugins then there is the risk that 
 * plugins will make use of the facilities with the risk that tests become 
 * excessively complex and fragile.
 * 
 * This is a superset of the information provided by the StfEnvironment class, which 
 * provides restricted information to plugins.
 */
public class StfEnvironmentCore {
	// Reference to the workspaces containing tests which use STF, 
	// plus the workspace containing STF itself (always the final element).
	private ArrayList<DirectoryRef> testRoots = new ArrayList<DirectoryRef>();
	// Reference to the directories containing the test prereqs
	private ArrayList<DirectoryRef> prereqRoots = new ArrayList<DirectoryRef>();
	
	private LayeredProperties properties;
	
	private DirectoryRef tmpDir;
	private DirectoryRef testDir;
	private DirectoryRef resultsDir;
	private DirectoryRef modulesDir;
	private DirectoryRef debugDir;
	
	private StfTestArguments testArgs = null;

	private Stage stage;
	
    private static final Logger logger = LogManager.getLogger(StfEnvironmentCore.class.getName());
	
	
	public StfEnvironmentCore(ArrayList<PropertyFileDetails> propertyFileDetails, ArrayList<Argument> supportedArguments,
			String testDir) throws StfException {

		ArrayList<OrderedProperties> allPropertyData = new ArrayList<OrderedProperties>();
		OrderedProperties invocationProperties = new OrderedProperties("<<invocation arguments>>");
//TODO tmpdir, etc, ??		invocationProperties.put("test", testName);  // Are there really invocation arguments that need their own layer.
		allPropertyData.add(invocationProperties);

		// Read in contents of all property files
		for (PropertyFileDetails propertyFile : propertyFileDetails) {
			OrderedProperties propertyData = OrderedProperties.loadFromFile(propertyFile);
			allPropertyData.add(propertyData);
		}

		this.properties = new LayeredProperties(allPropertyData, supportedArguments);

		// Make sure the stf workspace is always present in the list of roots, and is always element 0.
		DirectoryRef defaultTestRoot = createDirectoryRefFromProperty(Stf.ARG_STF_BIN_DIR).getParent().getParent();
		// Work out where the test cases workspace/s are.
		if (!getProperty(Stf.ARG_TEST_ROOT).isEmpty()) {
			// Get hold of test-root, ie, the directories containing test cases. 
			// This should be a list of valid directories.
			this.testRoots = explodeStringOfPaths(defaultTestRoot, getProperty(Stf.ARG_TEST_ROOT), "test-root");
			
		} else {
			// Use 2nd best option, and assume that everything is relative to the STF directory.
			this.testRoots.add(defaultTestRoot);
			invocationProperties.put(Stf.ARG_TEST_ROOT.getName(), defaultTestRoot.getSpec());
		}
		
		// Work out where the prereqs are
		if (!getProperty(Stf.ARG_SYSTEMTEST_PREREQS).isEmpty()) {
			// Get hold of test-root, ie, the directories containing test cases. 
			// This should be a list of valid directories.
			this.prereqRoots = explodeStringOfPaths(null,getProperty(Stf.ARG_SYSTEMTEST_PREREQS),"systemtest-prereq");
		} else {
			DirectoryRef prereqDir = findSystemtestPrereqs(invocationProperties, this.testRoots.get(0));
			this.prereqRoots = new ArrayList<DirectoryRef>();
			this.prereqRoots.add(prereqDir);
			invocationProperties.put(Stf.ARG_SYSTEMTEST_PREREQS.getName(), prereqDir.getSpec());
			logger.warn("systemtest_prereqs directory was not set on the command line, "
					+ "nor found in a properties file. STF will set it to: " 
					+ this.prereqRoots.get(0).getSpec());
		}
		
		this.testDir    = createDirectoryRef(testDir);
		this.tmpDir     = this.testDir.childDirectory("tmp");
		this.resultsDir = this.testDir.childDirectory(StfConstants.RESULTS_DIR);
		this.modulesDir = this.testDir.childDirectory("modules");
		this.debugDir   = this.testDir.childDirectory("debug");
		
		this.tmpDir.asJavaFile().mkdirs();
		this.resultsDir.asJavaFile().mkdirs();
		this.modulesDir.asJavaFile().mkdirs();
		this.debugDir.asJavaFile().mkdirs();
	}
	
	/**
	 * This method takes a semi-colon-seperated String of valid paths, and returns an Arraylist containing 
	 * a DirectoryRef object for each unique path.
	 * @param firstElement   The first element for the array, in situations where one element must be first.
	 * @param  directoryList A list of valid directories, separated by semicolons.
	 * @param  titleString  The title of this paths collection, for use in errors and debug messages. E.g. "test-root".
	 * @throws StfException  To be thrown if any of the directories either does not exist, or is not a directory.
	 */
	private ArrayList<DirectoryRef> explodeStringOfPaths(DirectoryRef firstElement, String directoryList, String titleString) throws StfException {
		ArrayList<DirectoryRef> arrayOfPaths = new ArrayList<DirectoryRef>();
		ArrayList<String> uniqueDirs = new ArrayList<String>(); // To ensure we only add unique directories to testRoots.
		
		// Add the default first element to the array.
		if (firstElement != null) {
			arrayOfPaths.add(firstElement);
			try {
				uniqueDirs.add(firstElement.asJavaFile().getCanonicalPath());
			} catch (IOException e) {
				throw new StfException("Could not get the canonical path for the default " + titleString + ": " + firstElement.getSpec(),e);
			}
		}
		
		// Split the string at every semicolon, and iterate over the list;
		// removing duplicates and verifying that each is a valid directory.
		String[] pathStrings = directoryList.split(";");
		for (int i = 0 ; i < pathStrings.length ; i++) {
			File canonicalPath = new File(pathStrings[i]);
			try {
				if (!uniqueDirs.contains(canonicalPath.getCanonicalPath())) { 
					uniqueDirs.add(canonicalPath.getCanonicalPath());
					arrayOfPaths.add(createDirectoryRef(pathStrings[i]));
					logger.debug(titleString.substring(0, 1).toUpperCase() + titleString.substring(1) + " " + (arrayOfPaths.size()-1) + " = " + arrayOfPaths.get(arrayOfPaths.size()-1).getSpec());
				} else {
					logger.debug("Ignoring " + titleString + " " + pathStrings[i] + " "
								+ "because its canonical path is identical to that of an earlier " + titleString + ".");
				}
			} catch (IOException e) {
				throw new StfException("Could not get the canonical path for the " + titleString + ": " + pathStrings[i],e);
			}
		}
		//Now we verify that all of the directories are valid
		for (DirectoryRef onePath : arrayOfPaths) {
			if (!onePath.exists()) {
				throw new StfException(titleString.substring(0, 1).toUpperCase() + titleString.substring(1) + " does not exist: " + onePath.getSpec());
			}
			if (!onePath.asJavaFile().isDirectory()) {
				throw new StfException(titleString.substring(0, 1).toUpperCase() + titleString.substring(1) + " exists, but is not a directory: " + onePath.getSpec());
			}
		}
		return arrayOfPaths;
	}
	
	/**
	 * This method looks for the prereqs location if we were not given one.
	 * @param  invocationProperties For setting ARG_SYSTEMTEST_PREREQS when we have the correct path.
	 * @return DirectoryRef         The location we think there might be a copy of the prereqs.
	 * @throws StfException         To be thrown if we can't find systemtest_prereqs on the workspace path.
	 */
	private DirectoryRef findSystemtestPrereqs(OrderedProperties invocationProperties, DirectoryRef testRoot) throws StfException {
		// Figure out where it is by scanning the paths of each test root.
		boolean endPrereqsLoop = false;
		DirectoryRef potentialPrereqsRoot = (createDirectoryRef(testRoot.getSpec()));
		String asciiOrEbcdic = "ascii";
		if (getPlatform().startsWith("zos")) {
			asciiOrEbcdic = "ebcdic";
		}
		while (!endPrereqsLoop) {
			//Go up one step in the path. check to see if we haven't left the path.
			try {
				//Does this directory contain systemtest_prereqs?
				if(potentialPrereqsRoot.childDirectory("systemtest_prereqs").exists()) {
					return potentialPrereqsRoot.childDirectory("systemtest_prereqs");
				} else if(potentialPrereqsRoot.childDirectory("prereqs").childDirectory(asciiOrEbcdic).childDirectory("systemtest_prereqs").exists()) {
					return potentialPrereqsRoot.childDirectory("prereqs").childDirectory(asciiOrEbcdic).childDirectory("systemtest_prereqs");
				} else {
					//If no, then go up a step and try again.
					potentialPrereqsRoot = potentialPrereqsRoot.getParent();
				}
			} catch (StfException e) {
				//do nothing.
			}
		}
		
		//If systemtest_prereqs was not on the test root path, throw an exception.
		throw new StfException("Prereqs root cannot be found. We were not provided a location, and were "
							 + "unable to find the systemtest_prereqs directory inside any of the directories "
							 + "along any of the test_root paths: " + testRoot.getSpec());
	}


	/**
	 * This method allows STF to set the stage which code is currently being 
	 * generated for.
	 */
	public void setStage(Stage stage) {
		this.stage = stage;
	}

	
	/**
	 * This method allows extensions to find out which stage they are generating code for.
	 * This allows different behaviour for different stages.
	 */
	public Stage getStage() {
		return stage;
	}

	
	/**
	 * This method writes out the current state of the all properties.
	 * It also checks that no unknown property values have been set.
	 * @throws StfException if a problem is detected.
	 */
	public void dumpAndCheckAllProperties(ArrayList<Argument> allArguments) throws StfException {
		// Record contents of all properties layer by layer
		FileRef allPropertiesDebugFile = debugDir.childFile("properties.txt");
		properties.dumpAllProperties(allPropertiesDebugFile.asJavaFile());
		
		// Record the current value for all properties. 
		// i.e. the actual values which would be returned if each property is retrieved. 
		FileRef resolvedPropertiesFile = debugDir.childFile("resolvedProperties.txt");
		properties.dumpResolvedProperties(allArguments, resolvedPropertiesFile.asJavaFile());
		
		// Verify that no values have been supplied for unknown properties
		properties.checkForUnknownProperties();
	}
	

	public FileRef createFileRef(String fileName) throws StfException {
		return new FileRef(fileName);
	}


	public DirectoryRef createDirectoryRef(String fileName) throws StfException {
		return new DirectoryRef(fileName);
	}
	
	
	//Calls findDirectory, but defaults to our list of prereq roots rather than the supplied list.
	public DirectoryRef findPrereqDirectory(String directoryRef, String... errorPrefixes) throws StfException {
		return DirectoryRef.findDirectory(directoryRef, getPrereqRoots(), errorPrefixes);
	}
	
	
	//Calls findFile, but defaults to our list of prereq roots rather than the supplied list.
	public FileRef findPrereqFile(String fileRef, String... errorPrefixes) throws StfException {
		return FileRef.findFile(fileRef, getPrereqRoots(), errorPrefixes);
	}
	
	
	//Calls findDirectory, but defaults to our list of test roots rather than the supplied list.
	public DirectoryRef findTestDirectory(String directoryRef, String... errorPrefixes) throws StfException {
		return DirectoryRef.findDirectory(directoryRef, getTestRoots(), errorPrefixes);
	}
	
	
	//Calls findFile, but defaults to our list of test roots rather than the supplied list.
	public FileRef findTestFile(String fileRef, String... errorPrefixes) throws StfException {
		return FileRef.findFile(fileRef, getTestRoots(), errorPrefixes);
	}
	

	public DirectoryRef createDirectoryRefFromProperty(Argument arg) throws StfException {
		String argValue = null;
		try {
			argValue = getProperty(arg);
			return createDirectoryRef(argValue);
		} catch (StfException e) { 
			throw new StfException("Failed to create directory reference "
					+ "for property '" + arg.getName() + "' "
					+ "with a value of '" + argValue +"'");
		}
	}
	
	
	public ArrayList<DirectoryRef> getTestRoots() {
		return testRoots;
	}
	
	public ArrayList<DirectoryRef> getPrereqRoots() {
		return prereqRoots;
	}

	public DirectoryRef getTmpDir() { 
		return tmpDir;
	}

	public DirectoryRef getResultsDir() { 
		return resultsDir;
	}

	public DirectoryRef getModulesDir() { 
		return modulesDir;
	}

	public DirectoryRef getDebugDir() { 
		return debugDir;
	}

	/**
	 * @return a String containing the name of the running plugin.
	 * @throws StfException 
	 */
	public String getSourceFileName() throws StfException {
		return getProperty(Stf.ARG_TEST);
	}

	
	// Test specific information is specified in the '-test-args' which holds a comma 
	// separated list of name value pairs.
	// For example, expected could be: "suite", "single-thread", "reporter=[LIVE]"
	// and the actual args: '-testProperties="suite=bidi, single-thread=false"
	public StfTestArguments getTestProperties(String... expectedTestArgs) throws StfException {
		if (testArgs == null) {
			String testArgsValue = getProperty(Stf.ARG_TEST_ARGS);
			testArgs = new StfTestArguments(testArgsValue, expectedTestArgs);
		}
		
		return testArgs;
	}

	
	public boolean getBooleanProperty(Argument property) throws StfException {
		return properties.getProperty(property.getName()).equals("");
	}
	
	public String getProperty(Argument property) throws StfException {
		return properties.getProperty(property.getName());
	}
	
	public int getPropertyAsInt(Argument property) throws StfException {
		return Integer.parseInt(properties.getProperty(property.getName()));
	}
	
	String getProperty(String propertyName) throws StfException {
		return properties.getProperty(propertyName);
	}
	

	/**
	 * Sets an existing property to a new value.
	 * This should only been done when absolutely necessary.
	 * @throws StfException 
	 */
	public String updateProperty(Argument argument, String newValue) throws StfException {
		String existingValue = properties.getProperty(argument.getName());
		properties.updateProperty(argument, newValue);
		return existingValue;
	}

	
	/**
	 * Returns the directory for JAVA_HOME
	 * The actual value return depends on the stage which is currently executing.
	 * 
	 * @return Reference to the JAVA_HOME directory.
	 * @throws StfException if JAVA_HOME has not been defined or if the plugin is attempting 
	 *         to start a java process during its initialisation.
	 */
	public DirectoryRef getJavaHome() throws StfException {
		Argument javaHomeArgument = null;
		switch (getStage()) {
		case SETUP:    javaHomeArgument = Stf.ARG_JAVAHOME_SETUP; break;
		case EXECUTE:  javaHomeArgument = Stf.ARG_JAVAHOME_EXECUTE; break;
		case TEARDOWN: javaHomeArgument = Stf.ARG_JAVAHOME_TEARDOWN; break;
		default: 
			throw new StfException("Can't run java process in the initialisation stage");
		}
		
		return createDirectoryRefFromProperty(javaHomeArgument);
	}

	
	public String getPlatform() throws StfException {
		return PlatformFinder.getPlatformAsString();  //TODO: convert to enumeration
	}

	
	/** 
	 * Returns the osgi.os name.
	 * Expected return values are win32, linux, aix, zos
	 */
	public String getOsgiOperatingSystemName() throws StfException {
		switch (PlatformFinder.getPlatform()) {
		case WINDOWS: return "win32";
		case AIX :    return "aix";
		case LINUX:   return "linux";
		case ZOS :    return "zos";
		default:      throw new StfException("Unknown platform for osgi.os: " + PlatformFinder.getPlatformAsString());
		}
	}

	/** 
	 * Returns the osgi.ws value.
	 * Expected return values are win32, gtk
	 */
	public String getOsgiWindowingSystemName() throws StfException {
		switch (PlatformFinder.getPlatform()) {
		case WINDOWS: return "win32";
		case AIX :    return "gtk";
		case LINUX:   return "gtk";
		default:      throw new StfException("Unknown platform for osgi.ws: " + PlatformFinder.getPlatformAsString());
		}
	}

	/** 
	 * Returns the osgi.arch name.
	 * Expected return values are x86, x86_64, ppc, ppc64, arm
	 */
	public String getOsgiProcessorArchitecture() throws StfException {
		String archName = PlatformFinder.getArchName();
		String wordSize = PlatformFinder.getArchType();
		
		if (archName.equals("x86") && wordSize.equals("32")) {
			return "x86";
		} else if (archName.equals("x86") && wordSize.equals("64")) {
			return "x86_64";
		} else if (archName.equals("ppc") && wordSize.equals("32")) {
			return "ppc";
		} else if (archName.equals("ppc") && wordSize.equals("64")) {
			return "ppc64";
		} else if (archName.equals("arm")) {
			return "arm";
		}
		
		throw new StfException("Unknown osgi.arch. archName:" + archName + " wordSize:" + wordSize);
	}

	
	/**
	 * Returns the word size of the machine. eg, 32 or 64
	 */
	public int getWordSize() throws NumberFormatException, StfException { 
		return Integer.parseInt(PlatformFinder.getArchType());
	}
	

	/**
	 * Check that any test specific arguments have actually been used.
	 * This prevents the user from running a test and supplying test specific arguments 
	 * which they believe do something but actually have no effect.
	 * 
	 * @throws StfException if the test has been invoked with unused test specific args.
	 */
	public void verifyTestArgsUsed() throws StfException {
		if (testArgs == null) {
			String testArgs = getProperty(Stf.ARG_TEST_ARGS);
			if (!testArgs.isEmpty()) {
				throw new StfException("Can't start test with '-" + Stf.ARG_TEST_ARGS.getName() + "' as this test does not use any.");
			}
		}
	}

	
	public boolean isVerboseSet() throws StfException {
		return getBooleanProperty(Stf.ARG_VERBOSE);
	}

	public boolean isSuperVerboseSet() throws StfException {
		return getBooleanProperty(Stf.ARG_VERBOSE_VERBOSE);
	}
	
	public JavaVersion primaryJvm() throws StfException {
		String javahome = getProperty(Stf.ARG_JAVAHOME_EXECUTE);
		return JavaVersion.getInstance(true, javahome);
	}

	public JavaVersion secondaryJvm() throws StfException {
		String javahome = getProperty(Stf.ARG_JAVAHOME_EXECUTE_SECONDARY);
		if (javahome.isEmpty()) {
			throw new StfException("Secondary JVM not usable, as none has been configured. "
					+ "See '" + Stf.ARG_JAVA_ARGS_EXECUTE_SECONDARY.getName() + "' argument or set JAVA_HOME_SECONDARY enviroment variable.");
		}
		return JavaVersion.getInstance(false, javahome);
	}
	
	/**
	 * @return true if the JVM used for test execution is Java version 6. Otherwise false.
	 */
	public boolean isUsingJava6() throws StfException {
		return JavaVersion.getInstance(this).isJava6();
	}
	
	/**
	 * @return the java version with the format as a single digit.
	 * eg, 6, 7, 8 or 9, etc
	 * @return int containing the java version number.
	 * @throws StfException if an unknown JVM release has been found.
	 */
	public int getJavaVersion() throws StfException {
		return JavaVersion.getInstance(this).getJavaVersion();
	}
	
	/**
	 * @return the java version with the format as 90 for Java 9,
	 * 80 for Java 8 and so forth.
	 * @throws StfException if an unknown JVM release has been found.
	 */
	public String getJavaVersionCode() throws StfException {
		return JavaVersion.getInstance(this).getJavaVersionCode();
	}
	
	/**
	 * @return true if version of Java used in the execution stage is an IBM JVM.
	 * @throws StfException if the execution of 'java -version' failed.
	 */
	public boolean isUsingIBMJava() throws StfException {
		return JavaVersion.getInstance(this).isIBMJvm();
	}
}
