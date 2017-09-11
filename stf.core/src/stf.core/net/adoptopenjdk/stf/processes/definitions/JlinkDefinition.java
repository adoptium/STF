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

import static net.adoptopenjdk.stf.processes.definitions.generic.ProcessArg.REQUIREMENT.OPTIONAL;

import java.util.ArrayList;
import java.util.HashMap;

import net.adoptopenjdk.stf.StfConstants;
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
 * This class helps to build jlink commands for the following version of jlink:
 * 
 *   $ jlink --help
 *   Usage: jlink <options> --module-path <modulepath> --add-modules <mods> --output <path>
 *   Possible options include:
 *     --help                            Print this help message
 *     --module-path <modulepath>         Module path
 *     --limit-modules <mod>[,<mod>...]      Limit the universe of observable modules
 *     --add-modules <mod>[,<mod>...]        Root modules to resolve
 *     --output <path>                   Location of output path
 *     --endian <little|big>             Byte order of generated jimage (default:native)
 *     --version                         Version information
 *     --save-opts <filename>             Save jlink options in the given file
 *     -G, --strip-debug                 Strip debug information
 *     -c, --compress=2                  Enable compression of resources (level 2)
 *     --plugin-module-path <modulepath> Custom plugins module path
 *     --list-plugins                    List available plugins
 *     @<filename>                       Read options from file
 *     
 *  There also some options described by '--list-plugins'.
 *  I've no idea why they do not appear under help but the don't.
 *  Option lines listed by 'list-plugins' follows:
 *  $ jlink --list-plugins
 *     --class-for-name
 *     --compress=<0|1|2>[:filter=<pattern>]
 *     --copy-files=<List of <file path>=<image target> to copy to the image>.
 *     --exclude-files=<files to exclude | files of excluded files>
 *     --exclude-resources=<resources to exclude | file of excluded resources>
 *     --include-locales=<langtag>[,<langtag>]*
 *     --installed-modules
 *     --order-resources=<pattern-list> of paths in priority order
 *     --strip-debug
 *     --strip-native-commands
 *     --vm=<client|server|minimal|all>
 *     
 *     --release-info=<file>|add:<key1>=<value1>:<key2>=<value2>:...|del:<key list>
 */
public class JlinkDefinition implements ProcessDefinition {
	private static StfEnvironmentCore environmentCore;

	// All arguments are in a single stage. Java 9 and beyond is required
	private Stage argStage = new ProcessArgCollection.Stage("jlinkArguments", 1, 9);

	// Define the arguments which jlink commands accept
	ProcessArg helpArg                = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "help",                  "--help");
	ProcessArg modulepathArg          = new ProcessArg(ARG_TYPE.PATH,       " ",          "",  OPTIONAL,  "modulepath",            "--module-path");
	ProcessArg limitmodsArg           = new ProcessArg(ARG_TYPE.MULTI_VAL,  " ",          ",", OPTIONAL,  "limitmods",             "--limit-modules");
	ProcessArg addmodsArg             = new ProcessArg(ARG_TYPE.MULTI_VAL,  " ",          ",", OPTIONAL,  "addmods",               "--add-modules");
	ProcessArg outputArg              = new ProcessArg(ARG_TYPE.DIR,        " ",          "",  OPTIONAL,  "output",                "--output");
	ProcessArg endianArg              = new ProcessArg(ARG_TYPE.STRING,     " ",          "",  OPTIONAL,  "endian",                "--endian");
	ProcessArg versionArg             = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "version",               "--version");
	ProcessArg saveoptsArg            = new ProcessArg(ARG_TYPE.STRING,     " ",          "",  OPTIONAL,  "saveopts",              "--save-opts");
	ProcessArg stripDebugArg          = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "strip-debug",           "--strip-debug");
	ProcessArg stripNativeCommandsArg = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "strip-native-commands", "--strip-native-commands");
	ProcessArg compressArg            = new ProcessArg(ARG_TYPE.STRING,     "=",          "",  OPTIONAL,  "compress",              "--compress");
	ProcessArg pluginModulePathArg    = new ProcessArg(ARG_TYPE.PATH,       " ",          "",  OPTIONAL,  "plugin-module-path",    "--plugin-module-path");
	ProcessArg listPluginsArg         = new ProcessArg(ARG_TYPE.FLAG,       " ",          "",  OPTIONAL,  "list-plugins",          "--list-plugins");
	ProcessArg classForNameArg        = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "class-for-name",        "--class-for-name");
	ProcessArg copyFilesArg           = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=",          ",", OPTIONAL,  "copy-files",            "--copy-files");
	ProcessArg excludeFilesArg        = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=",          ",", OPTIONAL,  "exclude-files",         "--exclude-files");
	ProcessArg excludeResourcesArg    = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=",          ",", OPTIONAL,  "exclude-resources",     "--exclude-resources");
	ProcessArg includeLocalesArg      = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=",          ",", OPTIONAL,  "include-locales",       "--include-locales");
	ProcessArg installedModulesArg    = new ProcessArg(ARG_TYPE.FLAG,       "",           "",  OPTIONAL,  "installed-modules",     "--installed-modules");
	ProcessArg orderResourcesArg      = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=",          ",", OPTIONAL,  "order-resources",       "--order-resources");
	ProcessArg vmArg                  = new ProcessArg(ARG_TYPE.STRING,     "=",          "",  OPTIONAL,  "vm",                    "--vm");
	ProcessArg releaseInfoFileArg     = new ProcessArg(ARG_TYPE.REPEAT_ARG, "=",          "",  OPTIONAL,  "release-info",          "--release-info");
	ProcessArg releaseInfoAddArg      = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=add:",      ":", OPTIONAL,  "release-info",          "--release-info");
	ProcessArg releaseInfoDelArg      = new ProcessArg(ARG_TYPE.MULTI_VAL,  "=del:keys=", ",", OPTIONAL,  "release-info",          "--release-info");
	
	ProcessArgCollection argCollection;
	
	String imageName;
	DirectoryRef imageDir;  // For cases in which the test decides on the output directory
	private int commandSerialNum;


	public JlinkDefinition() {
		this.argCollection = new ProcessArgCollection(environmentCore, argStage,
				helpArg,            
				modulepathArg,      
				limitmodsArg,       
				addmodsArg,         
				outputArg,          
				endianArg,          
				versionArg,
				saveoptsArg,        
				stripDebugArg,      
				stripNativeCommandsArg,
				compressArg,        
				pluginModulePathArg,
				listPluginsArg,
				classForNameArg,
				copyFilesArg,
				excludeFilesArg,
				excludeResourcesArg,
				includeLocalesArg, 
				installedModulesArg,
				orderResourcesArg,
				vmArg,
				releaseInfoFileArg,
				releaseInfoAddArg,
				releaseInfoDelArg);
	}

	
	///////////////////////////  --help  ///////////////////////////

	/**
	 * For '--help' argument.
	 * Calling this method enables the inclusion of '--help' into the jlink command.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableHelp() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		helpArg.setFlag(true);
		return this;
	}

	
	///////////////////////////  --module-path <modulepath>  ///////////////////////////
	
	/**
	 * For '--module-path <modulepath>' argument.
	 * @param dir is 1 or more directories to add to the modulepath. 
	 * $JAVA_HOME/jmods is also added to the module path.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addDirectoryToModulePath(DirectoryRef... dirs) throws StfException {
		for (DirectoryRef dir : dirs) {
			addToModulePath(dir.getSpec());
		}
		return this;
	}

	
	/**
	 * For '--module-path <modulepath>' argument.
	 * @param modularJar is a reference to 1 or more modular jar files.
	 * $JAVA_HOME/jmods is also added to the module path.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModularJarToModulePath(FileRef... modularJars) throws StfException {
		for (FileRef modularJar : modularJars) {
			addToModulePath(modularJar.getSpec());
		}
		return this;
	}

	
	/**
	 * For '--module-path <modulepath>' argument.
	 * @param module is 1 or more references to an existing module. The test may have created the module
	 * by running doCreateJmod() or by creating a ModuleRef object for a pre-existing module.
 	 * $JAVA_HOME/jmods is also added to the module path.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModuleToModulePath(ModuleRef... modules) throws StfException {
		for (ModuleRef module : modules) {
			DirectoryRef parentDir = module.getJarFileRef().parent();
			addToModulePath(parentDir.getSpec());
		}
		return this;
	}

	
	private void addToModulePath(String modulePathEntry) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		modulepathArg.add(modulePathEntry);
	}
	    

	///////////////////////////  --limit-modules <mod>[,<mod>...]  ///////////////////////////

	/**
	 * For '--limit-modules <mod>[,<mod>...]' argument.
	 * @param module is a module to to be appended to the limitmods list.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModuleToLimitmods(ModuleRef module) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		limitmodsArg.add(module.getBaseName());
		return this;
	}


	///////////////////////////  --add-modules <mod>[,<mod>...]  ///////////////////////////

	/**
	 * For '--add-modules <mod>[,<mod>...]' argument.
	 * @param modules is 1 or more modules to to be appended to the addmods list.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModuleToAddmods(ModuleRef... modules) throws StfException {
		for (ModuleRef module : modules) {
			argCollection.checkAndUpdateLevel(argStage);
			addmodsArg.add(module.getBaseName());
		}
		return this;
	}


	///////////////////////////  --output <path>  ///////////////////////////

	/**
	 * For '--output <path>' argument.
	 * @param imageName is a unique name for this image.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setOutput(String imageName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		
		this.imageName = imageName;
		String fullImageName = StfConstants.PLACEHOLDER_STF_COMMAND_NUMBER + "." + imageName;
		DirectoryRef imageDir = environmentCore.getTmpDir().childDirectory(fullImageName);
		outputArg.add(imageDir);

		return this;
	}
	
	
	/**
	 * For '--output <path>' argument.
	 * Allows the test to specify the output directory in which to create the image.
	 * This method should only be used in cases in which the test really _must_ 
	 * have control over the output location. 
	 * In most cases it is best to let STF decide where to put the image. 
	 * @param dirName is a platform specific directory name where the image is to be created.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setOutputDirectory(String dirName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		
		this.imageName = dirName;
		this.imageDir = environmentCore.createDirectoryRef(dirName);
		outputArg.add(imageDir);

		return this;
	}
	
	
	/**
	 * @return a String containing the image name, as supplied to JlinkDefinition.setOutput()
	 */
	public String getOutputImageName() {
		return imageName;
	}

	
	/**
	 * @return the directory in which STF has created the jlinked image.
	 * @throws StfException 
	 */
	public DirectoryRef getOutputImageDir() throws StfException {
		if (imageDir != null) {
			// Special case for explicitly set output directory
			return imageDir;
		}

		String actualImageName = Integer.toString(commandSerialNum) + "." + imageName;
		return environmentCore.getTmpDir().childDirectory(actualImageName);
	}


	/**
	 * @returns true if this JlinkDefinition has been told to create it's image at
	 * a particular location. 
	 */
	public boolean usingCustomOuputImageDir() {
		return imageDir != null;
	}

	
	///////////////////////////  --endian <little|big>  ///////////////////////////

	/**
	 * For '--endian <little|big>' argument.
	 * Calling this method forces output to little endian.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setEndianToLittle() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		endianArg.add("little");
		return this;
	}

	/**
	 * For '--endian <little|big>' argument.
	 * Calling this method forces output to big endian.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setEndianToBig() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		endianArg.add("big");
		return this;
	}


	///////////////////////////  --version  ///////////////////////////

	/**
	 * For '--version' argument.
	 * Calling this method enables the inclusion of '--version' into the jlink command.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableVersion() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		versionArg.setFlag(true);
		return this;
	}


	///////////////////////////  --save-opts <filename>  ///////////////////////////

	/**
	 * For '--save-opts <filename>' argument.
	 * Calling this method tells jlink to save the jlink options to the referenced file.
	 * @param saveoptsFile points to a file to save the options to.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setSaveopts(FileRef saveoptsFile) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		saveoptsArg.add(saveoptsFile);
		return this;
	}
	
	
	///////////////////////////  --strip-debug  ///////////////////////////
	
	/**
	 * For '--strip-debug' argument.
	 * Call this method to tell jlink to string debug information.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableStripDebug() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		stripDebugArg.setFlag(true);
		return this;
	}
	
	
	///////////////////////////  --strip-native-commands  ///////////////////////////
	
	/**
	 * For '--strip-native-commands' argument.
	 * Call this method to tell jlink to remove commands (such as java and keytool)
	 * from the created image.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableStripNativeCommands() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		stripNativeCommandsArg.setFlag(true);
		return this;
	}
	
	
	///////////////////////////  --compress=n  ///////////////////////////
	
	/**
	 * For '--compress=n' argument.
	 * @param compressLevel describes which resources to compress:
	 *   Level 0: constant string sharing
	 *   Level 1: ZIP
	 *   Level 2: both.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setCompress(int compressLevel) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		compressArg.add(compressLevel);
		return this;
	}
	
	/**
	 * For '--compress=n[:filter=<pattern>]' argument, with a filter specification.
	 * @param compressLevel as with previous setCompress method.
	 * @param filterPattern 'java --list-plugins' describes this as:
	 *   "An optional filter can be specified to list the pattern of files to be filtered.
	 *   Use ^ for negation. e.g.: *Exception.class,*Error.class,^/java.base/java/lang/*"
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setCompress(int compressLevel, String filterPattern) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		compressArg.add(compressLevel + ":filter=" + filterPattern);
		return this;
	}
	
	
	///////////////////////////  --plugin-module-path <modulepath>  ///////////////////////////

	/**
	 * For '--plugin-module-path <modulepath>' argument.
	 * Calling this method adds a module to the plugin module path.
	 * @param pluginModuleDir is the directory to add to the plugin module path.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addDirectoryToPluginModulePath(DirectoryRef dir) throws StfException {
		return addToPluginModulePath(dir.getSpec());
	}

	
	/**
	 * For '--plugin-module-path <modulepath>' argument.
	 * @param modularJar is a reference to a modular jar to be added to the plugin module path.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModularJarToPluginModulePath(FileRef modularJar) throws StfException {
		return addToPluginModulePath(modularJar.getSpec());
	}

	
	/**
	 * For '--plugin-module-path <modulepath>' argument.
	 * @param module is a reference to an existing module. The test may have created the module
	 * by running doCreateJmod() or by creating a ModuleRef object for a pre-existing module.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addModuleToPluginModulePath(ModuleRef module) throws StfException {
		DirectoryRef parentDir = module.getJarFileRef().parent();
		return addToPluginModulePath(parentDir.getSpec());
	}

	
	private JlinkDefinition addToPluginModulePath(String pluginModulePathEntry) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		pluginModulePathArg.add(pluginModulePathEntry);
		return this;
	}
	
	
	///////////////////////////  --list-plugins  ///////////////////////////
	
	/**
	 * For '--list-plugins' argument.
	 * Enables the jlink argument to list plugins.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableListPlugins() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		listPluginsArg.setFlag(true);
		return this;
	}
	

	///////////////////////////  --class-for-name  ///////////////////////////
	
	/**
	 * For '--class-for-name' argument.
	 * Call this method to enable the output of this flag, which is a class optimisation
	 * that 'converts Class.forName calls to constant loads'. 
	 * @return Updated jlink definition
	 */
	public JlinkDefinition enableClassForName() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		classForNameArg.setFlag(true);
		return this;
	}
	

	///////////////////////////  --copy-files  ///////////////////////////
	
	/**
	 * For the '--copy-files=<List of <file path>=<image target> to copy to the image>' 
	 * argument, which copies files or directories into a jlinked image.
	 * This method is for cases in which you only want to specify the source file.
	 * @param source is a file or directory to be copied to the jlink image output.
	 * If not an absolute file/directory then it is assumed to be relative to the 
	 * root of the JVM for the execute stage. 
	 * The file or directory will be copied into the jlink image with the same name.
	 * Note: Value is currently a string. This may need to change to a FileRef or 
	 * DirectoryRef object to preserve platform independence.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addToCopyFiles(String source) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		copyFilesArg.add(source);
		return this;
	}

	/**
	 * For the '--copy-files=<List of <file path>=<image target> to copy to the image>'
	 * argument, which copies files or directories into a specific location within a 
	 * jlinked image.
	 * This method is for cases in which you want to specify a source file/dir and 
	 * also it's destination. 
	 * At the time of writing there is little Oracle documentation on copy-files but it
	 * appears to work in the way that you would expect a unix style copy to operate.
	 * @param source is a file or directory to be copied to the jlink image output.
	 * If not an absolute file/directory then it is assumed to be relative to the 
	 * root of the JVM for the execute stage. 
	 * @param dest is the file or directory to copy the source to.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addToCopyFiles(String source, String dest) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		copyFilesArg.add(source + "=" + dest);
		return this;
	}

	
	///////////////////////////  --exclude-files  ///////////////////////////

	/**
	 * For the '--exclude-files=<files to exclude | files of excluded files>' argument.
	 * @param excludeFileName desribes which files to exclude, eg '*.diz' or '/java.base/native/client/*' 
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition addToExcludeFiles(String excludeFileName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		excludeFilesArg.add(excludeFileName);
		return this;
	}
	
	
	///////////////////////////  --exclude-resources  ///////////////////////////

	/**
	 * For the '--exclude-resources=<resources to exclude | file of excluded resources>'
	 * argument.
	 * @param excludeResourceName describes the resource to exclude. eg, '*.jcov' or 'META-INF'
	 * @return Updated jlink definition
	 */
	public JlinkDefinition addToExcludeResources(String excludeResourceName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		excludeResourcesArg.add(excludeResourceName);
		return this;
	}
		
	///////////////////////////  --include-locales  ///////////////////////////
	
	/**
	 * For the '--include-locales=<langtag>[,<langtag>]*' argument.
	 * @param localeName is the name of a locale to include, 'es' or 'th'
	 * @return Updated jlink definition
	 */
	public JlinkDefinition addToIncludeLocales(String localeName) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		includeLocalesArg.add(localeName);
		return this;
	}
		
	
	///////////////////////////  --installed-modules  ///////////////////////////
	
	/**
	 * For '--installed-modules' argument.
	 * Call this method to tell enable the '--installed-modules' option.
	 * This option appears to be aimed at improving the startup time of the JVM
	 * if it doesn't use lambdas.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition enableInstalledModules() throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		installedModulesArg.setFlag(true);
		return this;
	}
	
	
	///////////////////////////  --order-resources  ///////////////////////////
	
	/**
	 * For the '--order-resources=<pattern-list> of paths in priority order'
	 * argument.
	 * @param resourceSpec is a name to be added to the order resources argument values.
	 * @return Updated jlink definition
	 */
	public JlinkDefinition addToOrderResources(String resourceSpec) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		orderResourcesArg.add(resourceSpec);
		return this;
	}
	
	
	///////////////////////////  --vm  ///////////////////////////

	public enum VmType {CLIENT, SERVER, MINIMAL, ALL};
	
	/**
	 * For the '--vm=<client|server|minimal|all>' argument. 
	 * @param vmType describes the type of JVM to create.
	 * @return Updated jlink definition.
	 */
	public JlinkDefinition setVm(VmType vmType) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		vmArg.add(vmType.name().toLowerCase());
		return this;
	}
	
	
	///////////////////////////  --release-info  ///////////////////////////
	
	public JlinkDefinition setReleaseInfoFile(String filename) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		releaseInfoFileArg.add(filename);
		return this;
	}
	
	
	public JlinkDefinition addReleaseInfoProperty(String key, String value) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		releaseInfoAddArg.add(key + "=" + value);
		return this;
	}
	
	public JlinkDefinition deleteReleaseInfoProperty(String key) throws StfException {
		argCollection.checkAndUpdateLevel(argStage);
		releaseInfoDelArg.add(key);
		return this;
	}
	
	 
	// ------------------------------------------------------------------------
	
	// For STF internal use
	public static void setEnvironmentCore(StfEnvironmentCore environmentCore) {
		JlinkDefinition.environmentCore = environmentCore;
	}
	
	
	@Override
	public String getCommand() throws StfException {
		// Build the command to run, with the full path to jlink
		return environmentCore.getJavaHome().childFile("bin/jlink").getSpec();
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
		this.commandSerialNum = commandSerialNum;
	}
}