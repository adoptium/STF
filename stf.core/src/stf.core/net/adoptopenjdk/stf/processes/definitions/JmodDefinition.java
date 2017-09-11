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

import static net.adoptopenjdk.stf.processes.definitions.generic.ProcessArg.REQUIREMENT.MANDATORY;
import static net.adoptopenjdk.stf.processes.definitions.generic.ProcessArg.REQUIREMENT.OPTIONAL;

import java.util.ArrayList;
import java.util.HashMap;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.ModuleRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.definitions.generic.ProcessArg;
import net.adoptopenjdk.stf.processes.definitions.generic.ProcessArgCollection;
import net.adoptopenjdk.stf.processes.definitions.generic.ProcessArg.ARG_TYPE;
import net.adoptopenjdk.stf.processes.definitions.generic.ProcessArgCollection.Stage;


/**
 * This class builds jmod commands for the following version of jmod:
 * 
 *   $ jmod --help
 *   Usage: jmod (create|list|describe) <OPTIONS> <jmod-file>
 *   
 *    Main operation modes:
 *     create    - Creates a new jmod archive
 *     list      - Prints the names of all the entries
 *     describe  - Prints the module details
 *   
 *    Option                             Description                           
 *    ------                             -----------                           
 *     --class-path <path>                Application jar files|dir containing  
 *                                          classes                             
 *     --cmds <path>                      Location of native commands           
 *     --config <path>                    Location of user-editable config files
 *     --exclude <pattern>                Exclude files, given as a PATTERN     
 *     --hash-modules <pattern>           Compute and record hashes of          
 *                                          dependencies matched by the pattern 
 *     --help                             Print this usage message              
 *     --libs <path>                      Location of native libraries          
 *     --main-class <class-name>          Main class                            
 *     --module-version <module-version>  Module version                        
 *     --module-path, --mp <path>         Module path                           
 *     --os-arch <os-arch>                Operating system architecture         
 *     --os-name <os-name>                Operating system name                 
 *     --os-version <os-version>          Operating system version              
 *     --version                          Version information                   
 *     @<filename>                        Read options from the specified file  
 */
public class JmodDefinition implements ProcessDefinition {
	private static StfEnvironmentCore environmentCore;

	// Define stages. To ensure that the operation type (create/list/describe) is
	// set before any/all the arguments.
	// All stages require a minimum of Java 9
	private Stage operationStage = new ProcessArgCollection.Stage("jmodOperation", 1, 9);
	private Stage argStage       = new ProcessArgCollection.Stage("jmodArguments", 2, 9);

	// Define the arguments which jmod commands accept
	ProcessArg operationArg        = new ProcessArg(ARG_TYPE.STRING, MANDATORY, "operation",        null);
	ProcessArg classPathArg        = new ProcessArg(ARG_TYPE.PATH,   OPTIONAL,  "class-path",       "--class-path");
	ProcessArg cmdsArg             = new ProcessArg(ARG_TYPE.PATH,   OPTIONAL,  "cmds",             "--cmds");
	ProcessArg configArg           = new ProcessArg(ARG_TYPE.PATH,   OPTIONAL,  "config",           "--config");
	ProcessArg excludeArg          = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "exclude",          "--exclude");
	ProcessArg hashModulesArg      = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "hashModules",      "--hash-modules");
	ProcessArg helpArg             = new ProcessArg(ARG_TYPE.FLAG,   OPTIONAL,  "help",             "--help");
	ProcessArg libsArg             = new ProcessArg(ARG_TYPE.PATH,   OPTIONAL,  "libs",             "--libs");
	ProcessArg mainClassArg        = new ProcessArg(ARG_TYPE.CLASS,  OPTIONAL,  "mainClass",        "--main-class");
	ProcessArg moduleVersionArg    = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "moduleVersion",    "--module-version");
	ProcessArg modulepathArg       = new ProcessArg(ARG_TYPE.PATH,   OPTIONAL,  "modulepath",       "--module-path");
	ProcessArg osArchArg           = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "osArch",           "--os-arch");
	ProcessArg osNameArg           = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "osName",           "--os-name");
	ProcessArg osVersionArg        = new ProcessArg(ARG_TYPE.STRING, OPTIONAL,  "osVersion",        "--os-version");
	ProcessArg versionArg          = new ProcessArg(ARG_TYPE.FLAG,   OPTIONAL,  "version",          "--version");
	ProcessArg jmodFileArg         = new ProcessArg(ARG_TYPE.FILE,   OPTIONAL,  "jmodFile",         null);

	ProcessArgCollection argCollection;


	public JmodDefinition() {
		this.argCollection = new ProcessArgCollection(environmentCore, operationStage,
									operationArg,
									classPathArg,
									cmdsArg,
									configArg,
									excludeArg,
									hashModulesArg,
									helpArg,
									libsArg,
									mainClassArg,
									moduleVersionArg,
									modulepathArg,
									osArchArg,
									osNameArg,
									osVersionArg,
									versionArg,
									jmodFileArg);
	}


	// For STF internal use
	public static void setEnvironmentCore(StfEnvironmentCore environmentCore) {
		JmodDefinition.environmentCore = environmentCore;
	}


	///////////////////////////  Jmod operation modes  ///////////////////////////
	
	/**
	 * Sets the jmod operation to 'create'.
	 * @param jmodName is the name of the jmod file to create. 
	 * It will be created in the stf temp directory.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition doJmodCreate(String jmodName) throws StfException {
		argCollection.checkAndUpdateLevel(operationStage);
		operationArg.add("create");
		jmodFileArg.setFile(environmentCore.getTmpDir().childFile(jmodName));
		return this;
	}
	
	
	/**
	 * If this jmod definition represents a create operation then this 
	 * method returns a reference for the created file.
	 * @return a ModuleRef object for the jmod file that will be created.
	 */
	public ModuleRef getJmodModuleRef() throws StfException {
		FileRef jmodFile = jmodFileArg.getFileValue();
		return new ModuleRef(jmodFile.getName(), jmodFile);
	}
	
	
	/**
	 * Sets the jmod operation to 'list'.
	 * @param jmod is the jmod which you want to list.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition doJmodList(ModuleRef jmod) throws StfException {
		argCollection.checkAndUpdateLevel(operationStage);
		operationArg.add("list");
		jmodFileArg.setFile(jmod.getJarFileRef());
		return this;
	}
	

	/**
	 * Sets the jmod operation to 'describe'.
	 * @param jmod is the jmod which you want to describe.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition doJmodDescribe(ModuleRef jmod) throws StfException {
		argCollection.checkAndUpdateLevel(operationStage);
		operationArg.add("describe");
		jmodFileArg.setFile(jmod.getJarFileRef());
		return this;
	}
	

	///////////////////////////  --class-path <path>  ///////////////////////////

	/**
	 * For '--class-path <path>' argument.
	 * @param dir is a directory to add to the classpath.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addDirectoryToClassPath(DirectoryRef dir) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		classPathArg.addToPath(dir);
		return this;
	}
	
	/**
	 * For '--class-path <path>' argument.
	 * @param jar is an application jar to add to the classpath.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addJarToClassPath(FileRef jar) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		classPathArg.add(jar);
		return this;
	}

	
	///////////////////////////  --cmds <path>  ///////////////////////////

	/**
	 * For '--cmds <path>' argument.
	 * @param dir is a directory to add to the path for native commands.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addDirectoryToCmdsPath(DirectoryRef dir) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		cmdsArg.addToPath(dir);
		return this;
	}


	///////////////////////////  --config <path>  ///////////////////////////

	/**
	 * For '--config <path>' argument.
	 * @param dir is a directory to add to the path for user-editable config files.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addDirectoryToConfigPath(DirectoryRef dir) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		configArg.addToPath(dir);
		return this;
	}


	///////////////////////////  --exclude <pattern>  ///////////////////////////

	/**
	 * For '--exclude <pattern>' argument.
	 * @param excludePattern is the value to use as the module version.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setExcludePattern(String excludePattern) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		excludeArg.add(excludePattern);
		return this;
	}


	///////////////////////////  --hash-modules <pattern>  ///////////////////////////

	/**
	 * For '--hash-modules <pattern>' argument.
	 * @param hashModulesPattern is the value to use as the module version.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setHashModulesPattern(String hashModulesPattern) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		hashModulesArg.add(hashModulesPattern);
		return this;
	}

	
	///////////////////////////  --help  ///////////////////////////

	/**
	 * For '--help' argument.
	 * Calling this method enables the inclusion of '--help' into the jmod command.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition enableHelp() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		helpArg.setFlag(true);
		return this;
	}
	

	///////////////////////////  --libs <path>  ///////////////////////////

	/**
	 * For '--libs <path>' argument.
	 * @param dir is a directory to add to the libs path.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addDirectoryToLibsPath(DirectoryRef dir) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		libsArg.addToPath(dir);
		return this;
	}


	///////////////////////////  --main-class <class-name>  ///////////////////////////

	/**
	 * For '--main-class <class-name>' argument.
	 * @param clazz is the class to use for the the main class.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setMainClass(Class<?> clazz) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		mainClassArg.setClass(clazz);
		return this;
	}

	
	///////////////////////////  --module-version <module-version>  ///////////////////////////

	/**
	 * For '--module-version <module-version>' argument.
	 * @param moduleVersionStr is the value to use as the module version.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setModuleVersion(String moduleVersion) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		moduleVersionArg.add(moduleVersion);
		return this;
	}


	///////////////////////////  --module-path <path>  ///////////////////////////

	/**
	 * For '--module-path <path>' argument.
	 * @param dir is a directory to add to the modulepath path.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addDirectoryToModulepath(DirectoryRef dir) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		modulepathArg.addToPath(dir);
		return this;
	}

	/**
	 * For '--module-path <path>' argument.
	 * @param jar is an application jar to add to the modulepath path.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition addJarToModulPath(FileRef jar) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		modulepathArg.add(jar);
		return this;
	}
	
	
	///////////////////////////  --os-arch <os-arch>  ///////////////////////////

	/**
	 * For '--os-arch <os-arch>' argument.
	 * @param osArch is the value to supply for the argument.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsArch(String osArch) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		osArchArg.add(osArch);
		return this;
	}


	/**
	 * For '--os-arch <os-arch>' argument.
	 * Sets the os-arch to the value of the current platform. 
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsArchToCurrentPlatform() throws StfException {
		return setOsArch(System.getProperty("os.arch"));
	}


	///////////////////////////  --os-name <os-name>  ///////////////////////////

	/**
	 * For '--os-name <os-name>' argument.
	 * @param osName is the value to supply for the argument.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsName(String osName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		osNameArg.add(osName);
		return this;
	}

	
	/**
	 * For '--os-name <os-name>' argument.
	 * Sets the os-name to the value of the current platform. 
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsNameToCurrentPlatform() throws StfException {
		return setOsName(System.getProperty("os.name"));
	}

	
	///////////////////////////  --os-version <os-version>  ///////////////////////////

	/**
	 * For '--os-version <os-version>' argument.
	 * @param osVersion is the value to supply for the argument.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsVersion(String osVersion) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		osVersionArg.add(osVersion);
		return this;
	}

	
	/**
	 * For '--os-version <os-version>' argument.
	 * Sets the os-version to the value of the current platform. 
	 * @return Updated jmod definition.
	 */
	public JmodDefinition setOsVersionToCurrentPlatform() throws StfException {
		return setOsVersion(System.getProperty("os.version"));
	}

	
	///////////////////////////  -version  ///////////////////////////

	/**
	 * For '--version' argument.
	 * Calling this method enables the inclusion of '--version' into the jmod command.
	 * @return Updated jmod definition.
	 */
	public JmodDefinition enableVersion() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		versionArg.setFlag(true);
		return this;
	}

	
	// ------------------------------------------------------------------------
	
	@Override
	public String getCommand() throws StfException {
		// Build the command to run, with the full path to jmod
		return environmentCore.getJavaHome().childFile("bin/jmod").getSpec();
	}
	
	@Override
	public HashMap<String, StfProcess> getRelatedProcesses() {
		return new HashMap<String, StfProcess>();		
	}
	
	@Override
	public HashMap<String, Integer> getRelatedProcessesData() {
		return new HashMap<String, Integer>();
	}

	@Override
	public ArrayList<String> asArgsArray() throws StfException {
		return argCollection.asArgsArray();
	}

	@Override
	public boolean isJdkProgram() {
		return true;
	}

	@Override
	public JavaVersion getJavaVersion() {
		return null;
	}
	
	@Override
	public void generationCompleted(int commandSerialNum, String processMnemonic) throws StfException {
	}
}