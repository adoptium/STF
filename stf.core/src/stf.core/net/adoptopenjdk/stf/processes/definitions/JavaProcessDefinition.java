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

package net.adoptopenjdk.stf.processes.definitions;

import net.adoptopenjdk.stf.StfConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.ModuleRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.processes.StfProcess;

/**
 * This object captures the information needed to start a Java process.
 * 
 * Running a Java process can require numerous setup values, so rather than 
 * giving this class a very complex constructor it uses the builder pattern, to 
 * provide a fluent style interface for automation code.
 * 
 * An example of typical usage is:
 *    test.createJavaProcessDefinition()
 *        .addJvmOption("-Xmx500M")
 *	      .addProjectToClasspath("openjdk.test.load")
 *		  .addProjectToClasspath("stf.core")
 *		  .addProjectToClasspath("stf.load")
 *		  .addArg(suiteName)    			
 *		  .addArg(resultsDir.getSpec()));
 *
 * This class throws an exception if the Java process information is not built  
 * in the same order in which it will be used.
 * Callers should build the java process information in the following order:
 *   - jvm arguments
 *   - module --add-reads
 *   - module-path
 *   - upgrade-module-path
 *   - module add-modules
 *   - module limit-modules
 *   - module to run
 *   - classpath setup
 *   - class to execute 
 *   - application arguments
 *
 * See SampleClientServer.java for a runnable example.
 */
public class JavaProcessDefinition implements ProcessDefinition {
	private StfEnvironmentCore environmentCore;
	
	private JavaVersion jvm;
	
	// To accommodate the buildup invocation arguments
	private ArrayList<String> jvmOptions = new ArrayList<String>();
	private ArrayList<String> classpathEntries = new ArrayList<String>();
	private String executableJar = null;
	private String javaClassName = null;
	private ArrayList<String> javaArgs = new ArrayList<String>();
	private HashMap<String,StfProcess> relatedProcesses = new HashMap<String,StfProcess>();
	private HashMap<String,Integer> relatedProcessesData = new HashMap<String,Integer>();
	
	// Module related data
	private DirectoryRef runtimeImage = null;
	private String moduleAddReadsDefinition = null;
	private ArrayList<String> modulepathEntries = new ArrayList<String>();
	private ArrayList<String> upgradeModulepathEntries = new ArrayList<String>();
	private ArrayList<String> rootModules = new ArrayList<String>();
	private ArrayList<String> limitModules = new ArrayList<String>();
	private String initialModuleName = null;
	private Class<?> initialModuleMainClass = null;
	// For --add-exports
	private String addExportsModule = null;
	private String addExportsPackage = null;
	private ArrayList<String> addExportsOtherModules = null;
	
	// for --patch-module
	private static class PatchModuleData {
		String moduleName;
		ArrayList<String> patchDetails;
	}
	private boolean disablePatchModuleAmalgamation;
	ArrayList<PatchModuleData> patchModules = new ArrayList<PatchModuleData>();  
	
	// To enforce correct buildup of invocation arguments, all the addition 
	// methods fall into one of these categories.
	private enum Stage {
		//                level min VM version
		IMAGE(              1,  9),
		JVM_ARGS(           2,  1),
		MODULE_ADD_READS(   3,  9),
		MODULE_PATH(        3,  9),
		MODULE_UPGRADE_PATH(3,  9),
		MODULE_ADDMODS(     3,  9),
		MODULE_LIMITMODS(   3,  9),
		MODULE_ADD_EXPORTS( 3,  9),
		MODULE_PATCH_MODS(  3,  9),
		MODULE(             3,  9),
		CLASSPATH(          4,  1),
		CLASS(              5,  1),
		APPLICATION_ARGS(   6,  1);
		
		private int level;
		private int minimumJVM;
		Stage(int level, int minimumJVM) { 
			this.level = level;
			this.minimumJVM = minimumJVM;
		}
	}

	// Holds the category of addition method last used
	private Stage oldStage = Stage.IMAGE;
	
	
	public JavaProcessDefinition(StfEnvironmentCore environmentCore, JavaVersion jvm) {
		this.environmentCore = environmentCore;
		this.jvm = jvm;
	}

	public JavaProcessDefinition(StfEnvironmentCore environmentCore) throws StfException {
		this(environmentCore, environmentCore.primaryJvm());
	}

	public boolean isJdkProgram() {
		return true;
	}
	
	public JavaVersion getJavaVersion() {
		return jvm;
	}
	
	public JavaProcessDefinition addRunImage(DirectoryRef runtimeImage) throws StfException {
		checkAndUpdateLevel(Stage.IMAGE);
		
		this.runtimeImage = runtimeImage;
		return this;
	}
	

	/**
	 * Adds a value to be used as a Jvm option. eg, '-Xmx100M'
	 * It is usual to provide a single option on each call, but if options
	 * are related then it makes sense to supply several at the same time, as
	 * this helps to allow a logical layout in the test code.  
	 * @param jvmOptions one or more values to be used as JVM options
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence.
	 */
	public JavaProcessDefinition addJvmOption(String... jvmOptions) throws StfException {
		checkAndUpdateLevel(Stage.JVM_ARGS);
		
		if (jvmOptions != null) {
			for (String option : jvmOptions) {
				if (option != null) {
					this.jvmOptions.add(option);
				}
			}
		}
		
		return this;
	}
	
	
	/**
	 * Adds a value to be used as a Jvm option. eg, '-Xmx100M', only if 
	 * we are using an IBM JVM.  
	 * 
	 * @param jvmOptions one or more values to be used as JVM options
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence.
	 */
	public JavaProcessDefinition addJvmOptionIfIBMJava(String... jvmOptions) throws StfException {
		if (environmentCore.isUsingIBMJava()) {
			return addJvmOption(jvmOptions);
		}
		
		return this;
	}
	

	/**
	 * Adds a value for the --add-reads argument for Java9 modularity.
	 * Supply a value for add-reads, eg "com.test=ALL-UNNAMED"
	 * @param moduleAddReadsDefinition, which should be in the form '<module>=<other-module>(,<other-module>)*' 
	 * <other-module> may be ALL-UNNAMED to require the unnamed module.
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addModuleAddReads(String moduleAddReadsDefinition) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_ADD_READS);

		this.moduleAddReadsDefinition = moduleAddReadsDefinition;
		
		return this;
	}


	/**
	 * Adds a directory to the --module-path argument for Java9 modularity.
	 * @param directoryReference is the directory of modules to add as the next modulepath entry.
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addDirectoryToModulepath(DirectoryRef directoryReference) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_PATH);
		
		validateDirectoryExists(directoryReference, "Can't add directory to modulepath");
		modulepathEntries.add(directoryReference.getSpec());
		
		return this;
	}
	
	
	/**
	 * Adds a workspace directory to the --module-path argument for Java9 modularity. 
	 * @param moduleRef is the path to a module within the workspace, eg, "test.modularity/bin/common-mods"
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addModuleToModulepath(String moduleRef) throws StfException {
		DirectoryRef moduleDir = environmentCore.findTestDirectory(moduleRef);
		return addDirectoryToModulepath(moduleDir);
	}

	
	/**
	 * Adds a jar file to the --module-path argument for Java9 modularity. 
	 * @param moduleRef points to the jar file.
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addModuleToModulepath(ModuleRef moduleRef) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_PATH);
		
		modulepathEntries.add(moduleRef.getJarFileRef().getSpec());
		
		return this;
	}
    

	/**
	 * Adds a jar file to the --module-path argument for Java9 modularity. 
	 * @param jarRef points to the jar file.
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addJarToModulepath(FileRef jarRef) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_PATH);
		
		modulepathEntries.add(jarRef.getSpec());
		
		return this;
	}
	
	
	/**
	 * Adds a known systemtest-prereq jar to the modulepath.
	 * These are jar files held in one of the systemtest-prereq locations, and are referenced by the JarId enumeration. 
	 * See stf.core.properties for the default values. 
	 * @param jarId refers to the jar to be added.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the jar file does not exist.
	 */
	public JavaProcessDefinition addPrereqJarToModulePath(JarId jarId) throws StfException {
		// Find out where the jar file lives
		String relativeJarLocation = environmentCore.getProperty(jarId.getArgument()).replace("/systemtest-prereqs/", "");
		FileRef jarFileRef = environmentCore.findPrereqFile(relativeJarLocation);
		
		if (!jarFileRef.asJavaFile().exists()) {
			throw new StfException("Jar file does not exist: " + jarFileRef.getSpec());
		}
		
		return addJarToModulepath(jarFileRef);
	}	
  

	/**
	 * Adds a directory to the --upgrade-module-path argument for Java9 modularity.
	 * @param directoryReference is the directory of modules to add as the next modulepath entry.
	 * @return an updated Java process definition.
	 * @throws StfException if the directory does not exist.
	 */
	public JavaProcessDefinition addDirectoryToUpgradeModulepath(DirectoryRef directoryReference) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_UPGRADE_PATH);
		
		validateDirectoryExists(directoryReference, "Can't add directory to modulepath");
		upgradeModulepathEntries.add(directoryReference.getSpec());
		
		return this;
	}
	
	
	/**
	 * Adds a workspace directory to the --upgrade-module-path argument for Java9 modularity. 
	 * @param moduleRef is the path to a module within the workspace, eg, "test.modularity/bin/common-mods"
	 * @return an updated Java process definition.
	 * @throws StfException if the module directory doesn't exist.
	 */
	public JavaProcessDefinition addModuleToUpgradeModulepath(String moduleRef) throws StfException {
		DirectoryRef upgradeModuleDir = environmentCore.findTestDirectory(moduleRef);
		return addDirectoryToUpgradeModulepath(upgradeModuleDir);
	}

	
	/**
	 * Adds a jar or jmod file to the --upgrade-module-path argument for Java9 modularity. 
	 * @param fileRef points to the file to add. This is expected to be a jar or jmod file.
	 * @return an updated Java process definition.
	 */
	public JavaProcessDefinition addFileToUpgradeModulepath(FileRef fileRef) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_UPGRADE_PATH);
		
		upgradeModulepathEntries.add(fileRef.getSpec());
		
		return this;
	}

	
	/** 
	 * Define a module to be used as part of the --add-modules argument for Java9 modularity.
	 * @param rootModuleName is the name of a root module, eg, "com.test"
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addRootModule(String rootModuleName) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_ADDMODS);
		
		rootModules.add(rootModuleName);
		return this;
	}
	
	
	/** 
	 * Define a module to be used as part of the --limit-modules argument for Java9 modularity.
	 * @param limitModuleName is the name of a module for the limitmods argument. eg, "com.test"
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addLimitModule(String limitModuleName) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_LIMITMODS);
		
		limitModules.add(limitModuleName);
		return this;
	}
	
	
	/**
	 * Allows setting of optional values for --add-exports.
	 * Generates a Java argument in the form:
	 *   --add-exports <module>/<package>=<other-module>(,<other-module>)
	 *   
	 * @param addExportsModule
	 * @param addExportsPackage
	 * @param addExportsOtherModules is one or more module names. Currently string to allow almost anything to be passed in.
	 * @return an updated Java process definition.
	 */
	public JavaProcessDefinition addAddExports(String addExportsModule, String addExportsPackage, String... addExportsOtherModules)  throws StfException {
		checkAndUpdateLevel(Stage.MODULE_ADD_EXPORTS);

		this.addExportsModule = addExportsModule;
		this.addExportsPackage = addExportsPackage;
		this.addExportsOtherModules = new ArrayList<String>(Arrays.asList(addExportsOtherModules));
		
		return this;
	}

	
	
	/**
	 * By default module data for --patch-module arguments are combined into arguments 
	 * with the same module name.
	 * This method disables the amalgamation. Every call to .addPatchModule will then 
	 * result in the generation of a new --patch-module argument.
     * @return an updated Java process definition.
	 */
	public JavaProcessDefinition disablePatchModuleAmalgamation() {
		disablePatchModuleAmalgamation = true;
		return this;
	}
	

	/**
	 * Allows setting of optional values for --patch-module.
	 * Generates a Java argument in the form:
	 *   --patch-module <module>=<file>(:<file>)
	 * If used this method is expected to be invoked only once. 
	 * @param moduleName contains the name of the module.
	 * @param patchDirs are 1 or more directories to be used to patch the module.
     * @return an updated Java process definition.
	 */
	public JavaProcessDefinition addPatchModule(String moduleName, DirectoryRef... patchDirs) throws StfException {
		ArrayList<String> patchStrings = new ArrayList<String>();	
		for (DirectoryRef patchDir : patchDirs) {
			patchStrings.add(patchDir.getSpec());
		}
		
		addPatchModule(moduleName, patchStrings);
		
		return this;
	}

	
	/**
	 * Allows setting of optional values for --patch-module.
	 * Generates a Java argument in the form:
	 *   --patch-module <module>=<file>(:<file>)
	 * If used this method is expected to be invoked only once. 
	 * @param moduleName contains the name of the module.
	 * @param patches contains 1 or more modules to be used.
     * @return an updated Java process definition.
	 */
	public JavaProcessDefinition addPatchModule(String moduleName, ModuleRef... patches) throws StfException {
		ArrayList<String> patchStrings = new ArrayList<String>();
		for (ModuleRef patch : patches) {
			patchStrings.add(patch.getJarFileRef().getSpec());
		}
		
		addPatchModule(moduleName, patchStrings);
		
		return this;
	}

	
	private JavaProcessDefinition addPatchModule(String moduleName, ArrayList<String> patches) throws StfException {
		checkAndUpdateLevel(Stage.MODULE_PATCH_MODS);
		
		// Attempt to find existing patch module data for the named module
		PatchModuleData patchModule = null;
		if (!disablePatchModuleAmalgamation) {
			for (PatchModuleData p : patchModules) {
				if (p.moduleName.equals(moduleName)) {
					patchModule = p;
					break;
				}
			}
		}
		
		// Create a new patchModule object if we cannot add to an existing patchModule entry
		if (patchModule == null) {
			patchModule = new PatchModuleData();
			patchModule.moduleName = moduleName;
			patchModule.patchDetails = new ArrayList<String>();
			this.patchModules.add(patchModule);
		}
		
		// Store patch details for the current addPatchModule method call
		for (String patchArg : patches) {
			patchModule.patchDetails.add(patchArg);
		}
		
		return this;
	}

	
	/** 
	 * Define the initial module to be used for the optional -m argument for Java9 modularity.
	 * @param initialModuleName is the name of the initial module, eg, "com.test"
	 * @param initialModuleMainClass is the main class to execute if not specified in the module. 
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addInitialModule(String initialModuleName, Class<?> initialModuleMainClass)  throws StfException {
		checkAndUpdateLevel(Stage.MODULE);
		
		this.initialModuleName = initialModuleName;
		this.initialModuleMainClass = initialModuleMainClass;
		return this;
	}
	
	
	/** 
	 * Variant of addInitialModule for cases in which no initial module main class is supplied.
	 * Supplied values are used to build java's '-m' argument. 
	 * @param initialModuleName is the name of the initial module, eg, "com.test"
	 * @return an updated Java process definition.
	 * @throws StfException
	 */
	public JavaProcessDefinition addInitialModule(String initialModuleName)  throws StfException {
		checkAndUpdateLevel(Stage.MODULE);
		
		this.initialModuleName = initialModuleName;
		return this;
	}
	
	
	/**
	 * Adds the bin directory of a project to the classpath.
	 * The project is searched for in the test-root locations.
	 * @param projectName is the name of the project to be added, or null if there is no project to add.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the bin directory of the named project does not exist
	 * or if this method is called out of sequence.
	 */
	public JavaProcessDefinition addProjectToClasspath(String projectName) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);
		
		if (projectName != null) {
			DirectoryRef projectBinDir = environmentCore.findTestDirectory((projectName + "/bin"));
			classpathEntries.add(projectBinDir.getSpec());
		}
		
		return this;
	}

	
	/**
	 * This enumeration lists the known jar files which can be added to the classpath.
	 * We remove "/systemtest-prereqs" from the start of each argument so we can search
	 * each prereq location for these jars later.
	 */
	public enum JarId {
		JUNIT(StfCoreExtension.ARG_JUNIT_JAR),
		HAMCREST(StfCoreExtension.ARG_HAMCREST_CORE_JAR),
		LOG4J_API(StfCoreExtension.ARG_LOG4J_API_JAR),
		LOG4J_CORE(StfCoreExtension.ARG_LOG4J_CORE_JAR),
		ASM(StfCoreExtension.ARG_ASM_JAR),
		ASM_COMMONS(StfCoreExtension.ARG_ASM_COMMONS_JAR);
		
		private Argument jarLocation;
		private JarId(Argument jarLocation) { this.jarLocation = jarLocation; }
		public Argument getArgument() { return jarLocation; }
	}
	
	
	/**
	 * Adds a known systemtest-prereq jar to the classpath.
	 * These are jar files held in one of the systemtest-prereqs locations, and are referenced by the JarId enumeration. 
	 * See stf.core.properties for the default values. 
	 * @param jarId refers to the jar to be added.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the jar file does not exist.
	 */
	public JavaProcessDefinition addPrereqJarToClasspath(JarId jarId) throws StfException {
		// Find out where the jar file lives.
		String relativeJarLocation = environmentCore.getProperty(jarId.getArgument()).replace("/systemtest-prereqs/", "");
		FileRef jarFileRef = environmentCore.findPrereqFile(relativeJarLocation);
		
		if (!jarFileRef.asJavaFile().exists()) {
			throw new StfException("Jar file does not exist: " + jarFileRef.getSpec());
		}
		
		return addJarToClasspath(jarFileRef);
	}

	
	/**
	 * Adds a jar file to the classpath.
	 * The jar file is not checked to make sure it exists, as it may not be 
	 * available until run time.
	 * @param jarReference is a FileReference pointing at the jar file to add to the classpath.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence.
	 */
	public JavaProcessDefinition addJarToClasspath(FileRef jarReference) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);

		classpathEntries.add(jarReference.getSpec());
		return this;
	}
	
	
	/**
	 * Adds a directory to the classpath.
	 * @param directoryReference is a DirectoryRef pointing at the directory to add to the classpath.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the directory does not exist or if this method 
	 * is called out of sequence.
	 */
	public JavaProcessDefinition addDirectoryToClasspath(DirectoryRef directoryReference) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);
		
		classpathEntries.add(directoryReference.getSpec());
		return this;
	}

	private void validateDirectoryExists(DirectoryRef directory, String failureMessage) throws StfException {
		if (!directory.asJavaFile().exists()) {
			throw new StfException(failureMessage + ". Directory does not exist: " + directory.getSpec());
		}
	}
	
	
	/**
	 * Specifies the name of an executable jar file.
	 * Results in the Java process being executed with a '-jar <jarname.jar>' argument.
	 * If an executable jar is specified then 'runClass' cannot be called.
	 *  
	 * @param executableJar is a reference to the excutable jar file.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the executable jar has already been set or if an application
	 * argument has already been set.
	 */
	public JavaProcessDefinition setExecutableJar(FileRef executableJar) throws StfException {
		checkAndUpdateLevel(Stage.CLASS);
		
		if (this.executableJar != null) {
			throw new StfException("Can't set executable jar to '" + executableJar +"', as already set to '" + this.executableJar +"'");
		}

		this.executableJar = executableJar.getSpec();
		return this;
	}
	
	
	/**
	 * Declares the class to be run for this java process. 
	 * @param javaClass is the class object that needs to be executed.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the class has already been set or an application 
	 * argument has already been set.
	 */
	public JavaProcessDefinition runClass(Class<?> javaClass) throws StfException {
		return runClass(javaClass.getName());
	}

	
	/**
	 * Declares the class to be run for this java process.
	 * This variant should only be used in cases in which the java class is not
	 * known at compile time. If at all possible the runClass(Class<?>) method should used.  
	 * @param javaClass is the class object that needs to be executed.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if the class has already been set or if an application argument 
	 * has already been set.
	 */
	public JavaProcessDefinition runClass(String javaClassName) throws StfException {
		checkAndUpdateLevel(Stage.CLASS);

		if (this.javaClassName != null) {
			throw new StfException("Can't set java class to '" + javaClassName +"', as already set to '" + this.javaClassName +"'");
		}
		
		this.javaClassName = javaClassName;
		
		return this;
	}
	

	/**
	 * Adds a argument value to the Java invocation.
	 * Arguments are normally added singly, but where a set of related arguments need 
	 * to be passed then they can all be supplied in a single addArg() call.
	 * @param args is one or more arguments to be passed to the java class.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence.
	 */
	public JavaProcessDefinition addArg(String... args) throws StfException {
		checkAndUpdateLevel(Stage.APPLICATION_ARGS);

		for (String arg : args) {
			if (!arg.isEmpty()) {
				this.javaArgs.add(arg);
			}
		}
		
		return this;
	}
	

	/**
	 * Convenience method which adds one or more FileRef objects as an argument.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence.
	 */
	public JavaProcessDefinition addArg(FileRef... files) throws StfException {
		for (FileRef file : files) {
			addArg(file.getSpec());
		}
		
		return this;
	}
	
	/**
	 * Adds another process' perl variable to the Java invocation as an argument value, plus a suffix that tells it to return data we want.
	 * @param process is the StfProcess object whose data we want.
	 * @param perlProcessDataIndex is the number of the Perl process variable add-on that returns the data we want. 
	 *                             e.g. Passing 1 would result in "$processVariableName->{pid}", which would tell the 
	 *                             perl variable representing a process to return the process id.
	 * @return the updated JavaProcessDefinition.
	 * @throws StfException if this method is called out of sequence, or if that perlProcessDataKey doesn't exist inside 
	 * 						the PERL_PROCESS_DATA constant HashMap.
	 */
	public JavaProcessDefinition addPerlProcessData(StfProcess process, Integer perlProcessDataKey) throws StfException {
		checkAndUpdateLevel(Stage.APPLICATION_ARGS);

		if (process != null) {
			//First we check to see if this a
			if (!StfConstants.PERL_PROCESS_DATA.containsKey(perlProcessDataKey)) {
				throw new StfException("PERL_PROCESS_DATA does not contain key " + perlProcessDataKey);
			}
			//Then we add this process to the list of processes.
			String processKey = "Nonconstant_Placeholder_For_Perl_Code_That_Retrieves_Process_Data" + this.relatedProcesses.size() + ";";
			this.relatedProcesses.put(processKey,process); 
			this.relatedProcessesData.put(processKey, perlProcessDataKey);
			//Then we add a placeholder to the list of arguments, to retain the correct argument order.
			addArg(processKey);
		}
		
		return this;
	}

	/**
	 * @return the full path to java, as this process definition class can only run java.
	 * This may use the standard JAVA_HOME or a custom runtime image.
	 * @throws StfException 
	 */
	public String getCommand() throws StfException {
		DirectoryRef javaHome;
		if (this.runtimeImage == null) {
			javaHome = environmentCore.createDirectoryRef(jvm.getJavaHome());
		} else {
			javaHome = runtimeImage;  // use a jlink image
		}
		
		// Create the command using the full path to Java
		return javaHome.childFile("bin/java").getSpec();
	}
	
	
	/**
	 * Returns a HashMap containing links to all processes that have been identified as related to this process.
	 * @return A HashMap where each key is a unique String, and each StfProcess is a process related to this process.
	 */
	public HashMap<String, StfProcess> getRelatedProcesses() {
		return this.relatedProcesses;
	}
	
	
	/**
	 * Returns a HashMap containing a list of data that we want to get from all processes in the relatedProcesses HashMap.
	 * @return A HashMap where each key is a unique String, and each Integer is a PERL_PROCESS_DATA key linked to a specific 
	 * 		   operation that can be performed after appending the perl variable representing a specific process.
	 */
	public HashMap<String, Integer> getRelatedProcessesData() {
		return this.relatedProcessesData;
	}

	
	/**
	 * Call this when all options, jars, arguments, etc have been added and it 
	 * will build all lines of the java command with the logically correct ordering.
	 * @return an array list of Strings with the values needed to run java.
	 * @throws StfException if the java class to run hasn't been specified.
	 */
	public ArrayList<String> asArgsArray() throws StfException {
		// Sanity check jar/classpath type settings
		if (javaClassName != null && executableJar != null) {
			throw new StfException("Can't run as both Java class and executable jar have been specified");
		} else if (executableJar != null && classpathEntries.size() > 0) {
			throw new StfException("Running an excutable jar, therefore classpath entries cannot be specified (JVM ignores them)");
		}
	
		// Add JVM arguments
		ArrayList<String> allArgs = new ArrayList<String>();
		allArgs.addAll(jvmOptions);

		// --add-reads
		// Emit optional addReads option, for classpath/modulepath compatibilty
		if (moduleAddReadsDefinition != null) {
			allArgs.add("--add-reads " + moduleAddReadsDefinition);
		}

		// --module-path
		String modulepath = concatenateStrings(modulepathEntries, File.pathSeparatorChar);
		if (!modulepath.isEmpty()) {
			allArgs.add("--module-path");
			allArgs.add(modulepath);
		}
		
		// --upgrade-module-path
		// To upgrade modules in the runtime image
		String upgradeModulepath = concatenateStrings(upgradeModulepathEntries, File.pathSeparatorChar);
		if (!upgradeModulepath.isEmpty()) {
			allArgs.add("--upgrade-module-path");
			allArgs.add(upgradeModulepath);
		}
		
		// --add-modules
		// To specify root modules
		String rootModuleSpec = concatenateStrings(rootModules, ',');
		if (!rootModuleSpec.isEmpty()) {
			allArgs.add("--add-modules=" + rootModuleSpec);
		}

		// --limit-modules
		// To specify root modules
		String limitModuleSpec = concatenateStrings(limitModules, ',');
		if (!limitModuleSpec.isEmpty()) {
			allArgs.add("--limit-modules");
			allArgs.add(limitModuleSpec);
		}
		
		// --add-exports <module>/<package>=<other-module>(,<other-module>)
		if (addExportsModule != null) {
			StringBuilder addExports = new StringBuilder();
			addExports.append("--add-exports " + addExportsModule + "/" + addExportsPackage + "=");
			addExports.append(concatenateStrings(addExportsOtherModules, ','));
			allArgs.add(addExports.toString());
		}
					
		// --patch-module <module>=<file>(:<file>)
		for (PatchModuleData patchModuleData : patchModules) {
			StringBuilder patchModule = new StringBuilder();
			patchModule.append("--patch-module " + patchModuleData.moduleName + "=");
			patchModule.append(concatenateStrings(patchModuleData.patchDetails, File.pathSeparatorChar));
			allArgs.add(patchModule.toString());
		}

		// -m for initial module
		if (initialModuleName != null) {
			allArgs.add("-m");
			if (initialModuleMainClass == null) {
				allArgs.add(initialModuleName);
			} else {
				allArgs.add(initialModuleName + "/" + initialModuleMainClass.getName());
			}
		}

		
		// Add in the classpath
		String classpath = concatenateStrings(classpathEntries, File.pathSeparatorChar);
		if (!classpath.isEmpty()) {
			allArgs.add("-classpath");
			allArgs.add(classpath);
		}
		
		// Add the class to run 
		if (javaClassName != null && !javaClassName.isEmpty()) {
			allArgs.add(javaClassName);
		}

		// Optionally, specify the executable jar
		if (executableJar != null) {
			allArgs.add("-jar");
			allArgs.add(executableJar);
		}
		
		// Finally, JVM arguments
		allArgs.addAll(javaArgs);
		
		return allArgs;
	}


	// Joins multiple strings together to form classpath, etc
	private String concatenateStrings(ArrayList<String> classpathEntries, char separator) {
		StringBuilder formattedClasspath = new StringBuilder();
		
		for (String entry : classpathEntries) {
			if (formattedClasspath.length() > 0) {
				formattedClasspath.append(separator);
			}
			formattedClasspath.append(entry);
		}
		
		return formattedClasspath.toString();
	}
	
	
	/**
	 * Not for normal use. 
	 * This method resets the internal state which tracks the last method used.
	 * Prevents callers from building process invocations in an illogical order.
	 * Only to be used by extensions which pre-populate a bare bones process definition.
	 */
	public void resetStageChecking() { 
		oldStage = Stage.IMAGE;
	}


	// Verifies that the addition method is not being called at the wrong time.
	// eg. throws exception if attempting to add to the classpath if the last
	// call was adding an application argument.
	private void checkAndUpdateLevel(Stage newStage) throws StfException {
		// Make sure calls not made out of sequence
		if (newStage.level < oldStage.level) { 
			throw new StfException("Java invocation built out of sequence. " + newStage + " cannot be set after " + oldStage);
		}
		
		// Make sure the JVM supports the feature that is being specified
		int javaVersion = environmentCore.getJavaVersion();
		int requiredJavaVersion = newStage.minimumJVM;
		if (javaVersion < requiredJavaVersion) {
			throw new StfException("Target JVM too old. "
					+ "Java module arguments are only available from java 9 onwards. "
					+ "Current JVM version: " + javaVersion + ", but need minimum of version: " + requiredJavaVersion + " for: " + newStage);
		}
		
		oldStage = newStage;
	}


	@Override
	public void generationCompleted(int commandSerialNum, String processMnemonic) {
	}
}