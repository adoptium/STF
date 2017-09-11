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

package net.adoptopenjdk.stf.extensions.core;

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.StfExitCodes;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.codeGeneration.Stage;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator.CommandDetails;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.ModuleRef;
import net.adoptopenjdk.stf.environment.PlatformFinder;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.Argument.Required;
import net.adoptopenjdk.stf.extensions.Stf;
import net.adoptopenjdk.stf.extensions.StfExtensionBase;
import net.adoptopenjdk.stf.extensions.interfaces.StfExtension;
import net.adoptopenjdk.stf.modes.ModeDecoder;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.definitions.JDKToolProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.JlinkDefinition;
import net.adoptopenjdk.stf.processes.definitions.JmodDefinition;
import net.adoptopenjdk.stf.processes.definitions.LoadTestProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.ProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.SystemProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition.JarId;
import net.adoptopenjdk.stf.results.ReportFilteredTestResults;
import net.adoptopenjdk.stf.runner.StfClassLoader;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;
import net.adoptopenjdk.stf.supportApps.junit.JUnitRunner;
import net.adoptopenjdk.stf.util.StringSplitter;


/**
 * This STF extension implements basic test support functions.
 * 
 * It is anticipated that the functions provided by this extension 
 * are applicable to a wide range of tests.
 */
public class StfCoreExtension implements StfExtension {
	StfEnvironmentCore environmentCore;
	StfEnvironment environment;
	
	StfExtensionBase extensionBase;
    PerlCodeGenerator generator;

    //                        Java name                         Extension    Argument name     Boolean            Type
	public static Argument ARG_JUNIT_JAR         = new Argument("stfCore", "junit-jar",         false,  Required.MANDATORY);
	public static Argument ARG_HAMCREST_CORE_JAR = new Argument("stfCore", "hamcrest-core-jar", false,  Required.MANDATORY);
	public static Argument ARG_LOG4J_API_JAR     = new Argument("stfCore", "log4j-api-jar",     false,  Required.MANDATORY);
	public static Argument ARG_LOG4J_CORE_JAR    = new Argument("stfCore", "log4j-core-jar",    false,  Required.MANDATORY);
	public static Argument ARG_APPS_ROOT         = new Argument("stfCore", "apps-root",         false,  Required.MANDATORY);
	public static Argument ARG_MODE              = new Argument("stfCore", "mode",              false,  Required.MANDATORY);

    private DirectoryRef appsRoot;
    
    // This extension may go through the init cycle many times, so prevent duplicate
    // attempts to decode the mode by parsing modes.xml or variations.xml. 
    // The javaArgsExecuteInitial are remembered so that a run with '-mode=random'
    // with a '-repeat' value can each get a new set of random args for each invocation.
    private static boolean needToDecodedMode = true;
    String javaArgsExecuteInitial = null;

	@Override
	public Argument[] getSupportedArguments() {
		return new Argument[] {
				ARG_JUNIT_JAR, 
				ARG_HAMCREST_CORE_JAR,
				ARG_LOG4J_API_JAR,
				ARG_LOG4J_CORE_JAR,
				ARG_APPS_ROOT,
				ARG_MODE,
			};
	}
	
	@Override
	public void help(HelpTextGenerator help) {
		help.outputSection("Stf-core extension options.");
		
		help.outputArgName("-" + ARG_JUNIT_JAR.getName(), "FILE");
		help.outputArgDesc("Points to the location of the junit jar file.");
		
		help.outputArgName("-" + ARG_HAMCREST_CORE_JAR.getName(), "FILE");
		help.outputArgDesc("Points to the location of the hamcrest jar file.");
		
		help.outputArgName("-" + ARG_LOG4J_API_JAR.getName(), "FILE");
		help.outputArgDesc("Points to the location of the log4j api jar file.");
		
		help.outputArgName("-" + ARG_LOG4J_CORE_JAR.getName(), "FILE");
		help.outputArgDesc("Points to the location of the log4j core jar file.");
	}
	
	public void initialise(StfEnvironmentCore environmentCore, StfExtensionBase extensionBase, PerlCodeGenerator generator) throws StfException {
		this.environmentCore = environmentCore;
		this.environment = new StfEnvironment(environmentCore);
		this.extensionBase = extensionBase;
		this.generator = generator;
		// Find out where the apps directory is
		this.appsRoot = environmentCore.createDirectoryRefFromProperty(ARG_APPS_ROOT);

		// Convert the '-mode' name to java-args. But only do it once if possible 
		String modeName = environmentCore.getProperty(ARG_MODE);
		if (needToDecodedMode || modeName.toLowerCase().startsWith("random")) {
			// Translate the mode string to JVM arguments
			// Although the decoded mode arguments are only to be used in the execute stage do it
			// for all stages so that the values are available for checking in a tests init method.
			// Use the mode file to find out what arguments should be used
			String modeArguments = ModeDecoder.decodeModeName(environmentCore, modeName);
		
			if (javaArgsExecuteInitial == null) {
				// Only remember the initial values once (so that it can be reused multiple times. Even for random args)
				javaArgsExecuteInitial = environmentCore.getProperty(Stf.ARG_JAVA_ARGS_EXECUTE_INITIAL);
			}
		
			// Prepend the mode arguments to any existing initial JVM arguments.
			// The mode values are added at the start of the java command to allow
			// plugin code to override any of the values.
			String fullOptions = modeArguments + " " + javaArgsExecuteInitial;
			environmentCore.updateProperty(Stf.ARG_JAVA_ARGS_EXECUTE_INITIAL, fullOptions);
			needToDecodedMode = false;
		}

	}
	
	
	/**
	 * Provides access to the StfEnvironment object, which can be used to discover 
	 * details about the environment in which a plugin is running.
	 * Allows access to the results directory, temp directory and current platform. 
	 * 
	 * @return an StfEnvironment object.
	 */
	public StfEnvironment env() {
		return environment;
	}

	
	/**
	 * Some test material is held in the third party apps directory tree.
	 * This method provides access to its location for the current run.
	 *  
	 * @return a Directory reference pointing to the top of the apps directory.
	 */
	public DirectoryRef getAppsRoot() {
		return appsRoot;
	}

	/**
	 * Removes a directory when the test executes.
 	 * This is equivalent to running the Unix 'rm -rf' command on a directory. 
	 * The test will fail at runtime if the directory does not exist or the 
	 * file cannot be deleted.
	 * 
	 * @param comment is a short summary describing why the test is performing this step.
	 * @param targetDir is the directory to remove.
	 * @throws StfException if there is an internal error.
	 */
	public void doRm(String comment, DirectoryRef targetDir) throws StfException {
		generator.startNewCommand(comment, "rm", "Recursively delete a directory", 
						"Directory:", targetDir.getSpec());
		
		if (PlatformFinder.isWindows()) {
			generator.outputLine("stf::stfUtility->splatTree( dir => " + "'" + targetDir.getSpec() + "' );");
		} else {
			generator.outputLine("rmtree( " + "'" + targetDir.getSpec() + "', {error => \\$err" + "});");
			extensionBase.outputErrorCheck("rm", comment, "@$err");
		}
	}


	/**
	 * Removes a file when the test executes.
 	 * This is equivalent to the Unix rm command when used to delete a file. 
	 * The test will fail at runtime if the file does not exist or the 
	 * file cannot be deleted.
	 * 
	 * @param comment is a short summary describing why the test is performing this step.
	 * @param targetFile is the file to remove.
	 * @throws StfException if there is an internal error.
	 */
	public void doRm(String comment, FileRef targetFile) throws StfException {
		generator.startNewCommand(comment, "rm", "Delete a file", "File: ", targetFile.getSpec());
		generator.outputLine("rmtree " + "'" + targetFile.getSpec() + "';");
	}

	
	/**
	 * Copy a file to a directory.
	 * This is equivalent to the Unix cp command. 
	 * The cp will fail at runtime if the sourceFile does not exist, or it cannot write to destDir.
	 * 
	 * @param comment is a short summary describing why the test is performing this step.
	 * @param sourceFile is the file which is to be copied.
	 * @param destDir is the directory to copy the file to.
	 * @return a file reference to the file in the destination directory.
	 * @throws StfException if there is an internal error.
	 */
	public FileRef doCp(String comment, FileRef sourceFile, DirectoryRef destDir) throws StfException {
		generator.startNewCommand(comment, "cp", "Copy a file to another directory",
						"Source file:", sourceFile.getSpec(),
						"Dest dir:", destDir.getSpec());
		
		generator.outputLine("$rc = copy " + "'" + sourceFile.getSpec() + "', '" + destDir.getSpec() + "';");
		extensionBase.outputFailIfTrue("cp", comment, "$rc", "!=", StfExitCodes.expected(1));
		
		return destDir.childFile(sourceFile.getName());
	}
	
	
	/**
	 * Copy a directory tree recursively. The contents from the source directory are recursively copied to 
	 * the destination directory. 
	 * Note that the same directory structure will be recursively copied.
	 * The copy will fail at runtime if the source directory does not exist. The destination directory will 
	 * be created if it does not exist.
	 * 
	 * @param comment is a short summary describing why the test is copying a directory.
	 * @param sourceDir is the directory to be copied. This must already exist.
	 * @param destDir is the directory to copy to. This does not exist before running the command.
	 * @return a directory reference to the newly created directory.
	 * @throws StfException if there is an internal error.
	 */
	public DirectoryRef doCpDir(String comment, DirectoryRef sourceDir, DirectoryRef destDir) throws StfException {
		generator.startNewCommand(comment, "cpDir", "Copy a directory to a new directory",
						"Source dir:", sourceDir.getSpec(),
						"Dest dir:", destDir.getSpec());

		generator.outputLine("$rc = stf::stfUtility->copyTree(from => '" + sourceDir + "', to => '" + destDir + "');");		
		extensionBase.outputFailIfTrue("cpDir", comment, "$rc", "!=", StfExitCodes.expected(0));
		
		return destDir;
	}
	

	/**
	 * Copy files with the filtering options of including only files with a given 
	 * set of extensions and/or a list of file names to exclude from the destination directory. 
	 * Only the files that match the specified extensions are copied from the source to the destination. 
	 * If a matching file from within the source tree is copied then any intervening directories are 
	 * created in the destination. For example, a match of $source/x/y/zob.jar would result in the creation of $dest/x/y/zob.jar.
	 * The copy will fail at runtime if the source directory does not exist. The destination directory will 
	 * be created if it does not exist.
	 * All files specified in the list of excludes will be absent in the destination directory .  
	 * 
	 * @param comment is a short summary describing why the test is copying a directory.
	 * @param sourceDir is the directory to be copied.
	 * @param destDir is the directory to copy to.
	 * @param includeSpec a comma separated list of file extensions to copy.
	 * @param excludeSpec a comma separated list of filenames to exclude.
	 * @return a directory reference to the newly created directory.
	 * @throws StfException if there is an internal error.
	 */	
	public DirectoryRef doCpDir(String comment, DirectoryRef sourceDir, DirectoryRef destDir, String includeSpec,
			String excludeSpec) throws StfException {
		if (includeSpec == null && excludeSpec == null) {
			return doCpDir(comment, sourceDir, destDir);
		}
		
		String formattedIncludeList = formatCommaSeparatedList(includeSpec);; 
		String formattedExcludeList = formatCommaSeparatedList(excludeSpec);
		
		generator.startNewCommand(comment, "cpDir", "Partial directory copy. Only matching files copied.",
				"Source dir:", sourceDir.getSpec(), 
				"Dest dir:", destDir.getSpec(), 
				"Extensions:", formattedIncludeList, 
				"Excludes:", formattedExcludeList);
		
		return runCpDir(comment, sourceDir, destDir, formattedIncludeList, formattedExcludeList);
	}
	
	
	/**
	 * Private utility method that copies a source directory to a destination directory after applying 
	 * filters to include and exclude files. This method is used by doCpDir() and doCreateProjectJar() methods. 
	 * */
	private DirectoryRef runCpDir(String comment, DirectoryRef sourceDir, DirectoryRef destDir, String includeList,
			String excludeList) throws StfException {
		if (includeList != null && excludeList != null) {
			generator.outputLine("$rc = stf::stfUtility->copyTree(from => '" + sourceDir + "', to => '" + destDir + "',"
					+ "includeList => " + includeList + ", excludelist => " + excludeList + ");");
		} else if (includeList == null && excludeList != null) {
			generator.outputLine("$rc = stf::stfUtility->copyTree(from => '" + sourceDir + "', to => '" + destDir + "',"
					+ "excludelist => " + excludeList + ");");
		} else if (includeList != null && excludeList == null) {
			generator.outputLine("$rc = stf::stfUtility->copyTree(from => '" + sourceDir + "', to => '" + destDir + "',"
					+ "includeList => " + includeList + ");");
		} else {
			throw new StfException ("Wrong argument supplied for include list and / or exclude list");
		}
		extensionBase.outputFailIfTrue("cpDir", comment, "$rc", "!=", StfExitCodes.expected(0));

		return destDir;
	}

	
	/**
	 * Explicitly changes access permission in a given folder using chmod command.
	 * @param comment is a short summary describing why the test is explicitly changing permissions.
	 * @param targetDir is the directory where permission is to be changed. 
	 * @param modeBits is the Numeric notation of permission modes, e.g., 777. 
	 * @param isRecursive is the flag set if chmod is to be applied recursively in the given directory. 
	 * @throws StfException is thrown if there is an internal error. 
	 */
	public void doChmod(String comment, DirectoryRef targetDir, String modeBits, Boolean isRecursive) throws StfException {
		generator.startNewCommand(comment, "chmod", "Change file permission", "targetDir:", targetDir.getSpec(), "modeBits:", modeBits);
		String command = "system('" + "chmod";
		if (isRecursive) { 
			command = command + " -R";
		}
		command = command + " " + modeBits  + " \"" + targetDir + "\"" + "')";
		
		generator.outputLine("$rc = " + command + ";");
		extensionBase.outputFailIfTrue("chmod", comment, "$rc", "!=", StfExitCodes.expected(0));
	}


	/**
	 * Explicitly changes access permission to a given file.
	 * @param comment is a short summary describing why the test is explicitly changing permissions.
	 * @param targetFile is the file whose permission is to be changed. 
	 * @param modeBits is the Numeric notation of permission modes, e.g., 777. 
	 * @throws StfException is thrown if there is an internal error. 
	 */
	public void doChmod(String comment, FileRef targetFile, String modeBits) throws StfException {
		generator.startNewCommand(comment, "chmod", "Change file permission", "targetFile:", targetFile.getSpec(), "modeBits:", modeBits);
		if (PlatformFinder.isLinux() || PlatformFinder.isAix() || PlatformFinder.isZOS() || PlatformFinder.isOSX()) {
			 String command = "system('" + "chmod";
			 command = command + " " + modeBits  + " \"" + targetFile+ "\"" + "')";
			 generator.outputLine("$rc = " + command + ";");
			 extensionBase.outputFailIfTrue("chmod", comment, "$rc", "!=", StfExitCodes.expected(0));
		} else if (PlatformFinder.isWindows()) {
		    if (modeBits.equals("600")) {
		    	String currentUser = System.getProperty("user.name");
		    	String targetFilePath = targetFile.getSpec().replace("/","\\"); // Make sure we have Windows style slashes before issuing icacls commands
		    	runCmd("Remove inherited permission for jmxremote.password file", "system('icacls " + targetFilePath + " /inheritance:r')");
			    runCmd("Re-assign ownership for jmxremote.password file", "system('takeown /f " + targetFilePath +"')");
			    runCmd("Grant restricted ownership to current user", "system('icacls " + targetFilePath + " /grant " + currentUser + ":(r,w)')");
		    } else {
		        throw new StfException("Unsupported mode...");
		    }
		} else {
		    throw new StfError("Unsported platform...");
		}
	}
	
	
	// Helper method to run arbitrary commands 
	private void runCmd(String comment, String commandToRun) throws StfException {
		generator.outputLine("$rc = " + commandToRun + ";");
		extensionBase.outputFailIfTrue(commandToRun, comment, "$rc", "!=", StfExitCodes.expected(0));
	}
	
	
	/**
	 * Create a new directory.
	 * This is equivalent to the Unix mkdir command.
	 * The mkdir command will fail at runtime if targetDir cannot be created.
	 * 
	 * @param comment is a short summary describing why the test is creating a new directory.
	 * @param targetDir is the directory to create.
	 * @throws StfException if there is an internal error.
	 */
	public void doMkdir(String comment, DirectoryRef targetDir) throws StfException {
		generator.startNewCommand(comment, "mkdir", "Create new directory", "Directory:", targetDir.getSpec());
		generator.outputLine("$rc = mkpath( " + "'" + targetDir.toString() + "', {error => \\$err} );");
		extensionBase.outputErrorCheck("mkdir", comment, "@$err");
	}


	/**
	 * Changes the current working directory.
	 * This is equivalent to the Unix cd command.
	 * The cd will fail at runtime if targetDir does not exist.
	 * 
	 * @param comment is a short summary describing why the test is changing directory.
	 * @param targetDir is the directory to move to.
	 * @throws StfException if there is an internal error.
	 */
	public void doCd(String comment, DirectoryRef targetDir) throws StfException {
		generator.startNewCommand(comment, "cd", "Change current working directory", "To:", targetDir.getSpec());
		generator.outputLine("$rc = chdir " + "'" + targetDir + "';");
		extensionBase.outputFailIfTrue("cd", comment, "$rc", "!=", StfExitCodes.expected(1));
	}

	
	/**
	 * Unpacks the contents of an archive into the current working directory.
	 * This is a platform specific action.
	 *   aix - unzips files with '.zip' extension.
	 *   win - unzips files with '.zip' extension.
	 *   other - uses tar to unpack '.tar.gz' or unzip to unpack '.zip' files.
	 * The unpacking will fail at runtime if the archive does not exist, or if it 
	 * cannot be written to the current location (eg, permissions or lack of space).  
	 * 
	 * @param comment is a short summary describing why the test is unpacking an archive.
	 * @param zipFile is the archive to be unpacked.
	 * @throws StfException asked to unpack an archive which is not supported for the current platform.
	 */
	public void doUnzip(String comment, FileRef zipFile) throws StfException {
		generator.startNewCommand(comment, "unzip", "Unpack archive file", "Archive:", zipFile.getSpec());
		
		CommandDetails command;
		String mnemonic = "UZIP";
		
		if (!PlatformFinder.isWindows()) {
			if (PlatformFinder.isAix()) {
			    // On aix expect zips only and unzip with "unzip -qq -o -C <file>
				generator.verify(zipFile.getSpec().endsWith(".zip") , "archive must end with .zip extension: " + zipFile);
				command = generator.buildCommand(mnemonic, 1, null, "unzip", "-qq", "-o", "-C", zipFile.getSpec());
		   	} else if (zipFile.getSpec().endsWith(".tar.gz")) {
			    // Any other unix untar tar files with tar -xzf <file>
				command = generator.buildCommand(mnemonic, 1, null, "tar", "-xzf", zipFile.getSpec());
	   		} else if (zipFile.getSpec().endsWith(".zip")) {
			    // Or unzip zip files with unzip - o <file>
				command = generator.buildCommand(mnemonic, 1, null, "unzip", "-o", zipFile.getSpec());
	   		} else {
	   			throw new StfException("Unexpected archive type: " + zipFile);
	   		}
		} else {
		    // On Windows we can unpack with the JDK jar program
			generator.verify(zipFile.getSpec().endsWith(".zip"), "archive must end with .zip extension: " + zipFile);
			String program = environmentCore.getJavaHome().childFile("bin/jar").getSpec();
			command = generator.buildCommand(mnemonic, 1, comment, program, "-xf", zipFile.getSpec());
		}

		// Generate perl to unpack the archive
		SystemProcessDefinition processDefinition = createSystemProcessDefinition()
				.setProcessName(command.getExecutableName())
				.addArg(command.getArgs());
		extensionBase.runForegroundProcess(comment, mnemonic, ECHO_ON, ExpectedOutcome.cleanRun().within("15m"), processDefinition);
	}

	
	/**
	 * Creates a new file with the specified content.
	 * In general the test should build up the full string of the files contents and then 
	 * write it one operation with this action.
	 * The file write will fail at runtime if the output file cannot be created. 
	 * 
	 * @param comment is a short summary describing why the test creating a new file.
	 * @param outputFile is the file to be created.
	 * @param fileContents is the content to write to the file.
	 * @throws StfException if there is an internal error.
	 */
	public void doWriteFile(String comment, FileRef outputFile, String fileContents) throws StfException {
		generator.startNewCommand(comment, "writeFile", "Create new file", "File:", outputFile.getSpec());

		// Double back slash needs to be replaced with 4 backslashes, so that generated file contains 2 backslashes
		StringBuilder contents = new StringBuilder(fileContents.replace("\\\\", "\\\\\\\\")); 
		
		generator.outputLine("stf::stfUtility->writeToFile(file => '" + outputFile.getSpec() + "', ");
		generator.outputLine("            content => ['" + contents + "']);");
	}


	/**
	 * Generates perl code so that tests can echo the contents of a file.
	 * If the file cannot be opened then all running processes are killed and the test fails.
	 * @param comment is a short summary describing why the test creating a new file.
	 * @param targetFile points to the file to be echoed.
	 * @throws StfException if there is an internal error.
	 */
	public void doEchoFile(String comment, FileRef targetFile) throws StfException {
		extensionBase.outputEchoFile(targetFile);
	}

	
	/**
	 * Apply text replacement to an existing file.
	 * The test will fail at runtime if the file does not exist.
	 * It will also fail at runtime if the actual number of replacements does not
	 * match the expected number. This expected vs. actual runtime check is done 
	 * so as to add as much checking as possible that a test run is going as expected, and
	 * also prevents stale test code which is attempting an edit which no longer actually
	 * does any replacements.
	 * 
	 * @param comment is a short summary describing why the test is editing the file.
	 * @param file is the file to edit.
	 * @param sourceText is the text to be replaced.
	 * @param replacementText is the text to use instead of the sourceText.
	 * @param expectedNumReplacements is the number of replacements that the test expects to be done.
	 * @throws StfException if there is an internal error.
	 */
	public void doFileEdit(String comment, FileRef file, String sourceText, String replacementText, int expectedNumReplacements) throws StfException {
		int commandNum = generator.startNewCommand(comment, "FileEdit", "Automated file edit",
					"File:",    file.getSpec(),
					"Search:",  sourceText,
					"Replace:", replacementText);
		
		String countVariable = "$count" + commandNum;
		generator.outputLine("my " + countVariable + " = stf::stfUtility->searchReplace(");
		generator.outputLine("                    file => '" + file.getSpec() + "',");
		generator.outputLine("                    search => '" + sourceText + "',");
		generator.outputLine("                    replace => '" + replacementText + "');");
		extensionBase.outputFailIfTrue("FileEdit", comment, countVariable, "!=", expectedNumReplacements);
	}

	
	/**
	 * Creates a process definition to run JUnit tests.
	 * 
	 * The caller can then decide on how to execute the tests: synchronously or 
	 * asynchronously, number of instances, expected run time, etc).
	 * This model also allows the caller to add extra jar files, etc, to the 
	 * process definition.
	 * See SampleJUnitTestRun.java for an example.
	 * 
	 * The test fails at runtime if the JUnit test run fails, due to one or 
	 * more of the JUnits tests failing.  
	 * 
	 * Tests known to fail for valid reasons can be treated as a non-fatal error
	 * by adding them in the testExclusions file. Each line of the file contains 
	 * a rule describing one ore more tests which are allowed to fail. See STF 
	 * documentation for a full description of the rules. In it's simplest form 
	 * it lists individually failing tests. eg:
	 *    test=outOfMemoryTest
	 * There are many ways in which this action can fail at runtime:
	 *   - classpath error. eg project does not exist in the workspace.
	 *   - one or more tests fail.
	 *   - tests run for longer than their allowed duration.
	 *
	 * @param project is the name of a project which needs to be added to the classpath.
	 * @param testExclusions is a reference to a file containing rules for matching
	 * known failures, or null if there are no expected failures.
	 * @param junitClasses is one or more classes containing JUnit tests.
	 * @throws StfException if there is an internal error.
	 */
	public JavaProcessDefinition createJUnitProcessDefinition(String project, FileRef testExclusions, Class<?>... junitClasses) throws StfException {
		JavaProcessDefinition junitProcessDef = createJavaProcessDefinition()
			.addPrereqJarToClasspath(JarId.JUNIT)
			.addPrereqJarToClasspath(JarId.HAMCREST)
			.addProjectToClasspath("stf.core")      // To run JUnitRunner
			.addProjectToClasspath(project)
			.runClass(JUnitRunner.class);      // STF utility class. Exit code indicates overall pass/fail
		
	    // If there is an exclusions file then it is the first argument to JUnitRunner
	    if (testExclusions != null) {
	    	junitProcessDef.addArg(testExclusions.getSpec());
	    }
	    
		// Add all the names of the actual JUnit test classes
		for (Class<?> testClass : junitClasses) {
			junitProcessDef.addArg(testClass.getName());
		}
		
		junitProcessDef.resetStageChecking();

		return junitProcessDef;
	}

	
	/**
	 * This method finds a tests resource file. 
	 * The resource file must be in the same directory as the test plugin.
	 * 
	 * @return a FileRef of the named resource file.
	 * @throws StfException if the resource file could not be found. 
	 */
	public FileRef locateResourceFile(String resourceFileName) throws StfException {
		// Finding the file is trickier than it perhaps should be, as it has to work it multiple 
		// environments. Things are easy in the Eclipse workspace, as the resource file will have been 
		// copied to the same bin directory as the plugins class, but not so easy when running 
		// from a command line build as the resource file is not copied to the bin directory so need to 
		// find it in the source directory.
		
		String testName = environmentCore.getProperty(Stf.ARG_TEST);
		
		// To find the resource file we need to know the class name for the test plugin.
		// This is done by looking at the stack traces.
		// Find the oldest method whose class name matches the current test name.
		// Note that we search backwards instead of forwards just in case the actual test 
		// code has called another class with a similar name. 
		int testMethodIndex = -1;
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i=stElements.length-1; i>0; i--) {
        	if (stElements[i].getClassName().endsWith("."+testName)) {
        		testMethodIndex = i;
        	}
        }
        
        // Work out the source directory for the project containing the current test class
        String testClassName = stElements[testMethodIndex].getClassName();
        String projectBinDir = StfClassLoader.getProjectName(testClassName);
		File projectDir = new File(projectBinDir).getParentFile();
		File projectSrcDir = new File(projectDir, "src");
		
		// Step down into a java 9 style project directory which is at the top of the src dir
		File[] sourceRootCandidates = projectSrcDir.listFiles();
		if (sourceRootCandidates.length != 1) {
			throw new StfException("Unexpectedly found more than one file/directory in the project source at: " + projectSrcDir.getAbsolutePath());
		}
		File sourceRoot = sourceRootCandidates[0];
		
		// Build the path to the test source file
		String packagePath = stElements[testMethodIndex].getClassName().replace(".", "/");
		File sourceFile = new File(sourceRoot, packagePath + ".java");
		if (!sourceFile.exists()) {
			throw new StfException("Failed to find test source code at:" + sourceFile.getAbsolutePath());
		}
		
		// Now look for the resource file in the same directory as the source file
		File resourceFile = new File(sourceFile.getParentFile(), resourceFileName);
		if (!resourceFile.exists()) {
			throw new StfException("Failed to find resource file at:" + resourceFile.getAbsolutePath());
		}
		
		return environmentCore.createFileRef(resourceFile.getAbsolutePath().replace("\\", "/"));
	}


	/**
	 * This method is used to check to see if a test has passed or failed. 
	 * It applies a result filter file and checks to see if all tests passed.
	 * The filter file allows a test to run, and pass, with known issues.
	 * 
	 * The steps performed are:
	 *  1) Search the result directory for .tr files. 
	 *  2) Read in all .tr result files.
	 *  3) Read in the filter file. 
	 *  4) Report test pass/fail numbers before filtering
	 *  5) Examine test results, with failing tests being matched against every filter rule.
	 *     Failing tests which match a filter are promoted to a pass.
	 *  6) Report post filtering pass/fail numbers.
	 *  7) Set process exit code to 0 if all tests passed, otherwise 1.
	 * 
	 * @param comment is a brief description about what is happening.
	 * @param resultsDirectory is a directory containing one or more '.tr' result files.
	 * @param filterFileString Is the name of the filter file. It is also in '.tr' format.
	 * @throws StfException If there was a problem reading the result or filter files.
	 */
	public void doReportFilteredTestResults(String comment, DirectoryRef resultsDirectory, String filterFileString) throws StfException {
		FileRef filterFile = locateResourceFile(filterFileString);
		
		generator.startNewCommand(comment, "report", "Analyse .tr test result files",
						"Mnemonic:",    "RTR",
						"Results dir:", resultsDirectory.getSpec(),
						"Filter file:", filterFile.getSpec());

		// New examine the xml results file to see if all tests have passed
		extensionBase.runForegroundProcess(comment, "RTR", Echo.ECHO_ON, ExpectedOutcome.cleanRun().within("1m"), 
				createJavaProcessDefinition()
					.addProjectToClasspath("stf")
					.runClass(ReportFilteredTestResults.class)
					.addArg(resultsDirectory.getSpec())   // arg1 - the directory containing the .tr result files
					.addArg(filterFile.getSpec()));       // arg2 - local exclusions file
	}


	/**
	 * Validates that a file exists.
	 * The test run will fail if the file does not exist.
	 * 
	 * @param comment is a brief description summarising why the test wants to do the validation.
	 * @param targetFile is a reference to the file which must exist.
	 * @throws StfException 
	 */
	public void doValidateFileExists(String comment, FileRef targetFile) throws StfException {
		generator.startNewCommand(comment, "fileCheck", "Validate file exists", "File:", targetFile.getSpec());
		
		generator.outputLine("if (!-f '" + targetFile.getSpec() + "') {");
		generator.increaseIndentation();
		extensionBase.outputDieCommand(StfConstants.FAILURE_PREFIX 
					+ "at " + generator.describeCommand("fileCheck", comment) + ". " 
					+ "File does not exist: " + targetFile.getSpec());
		generator.decreaseIndentation();
		generator.outputLine("}");
		generator.outputEmptyLine();
	}
	
	
	/**
	 * Validates that a file does not exists
	 * The test run will fail if the file exists.
	 * 
	 * @param comment is a brief description summarising why the test wants to do the validation.
	 * @param targetFile is a reference to the file which must not exist.
	 * @throws StfException 
	 */
	public void doValidateFileAbsent(String comment, FileRef targetFile) throws StfException {
		generator.startNewCommand(comment, "fileCheck", "Validate file absent", "File:", targetFile.getSpec());
		
		generator.outputLine("if (-f '" + targetFile.getSpec() + "') {");
		generator.increaseIndentation();
		extensionBase.outputDieCommand(StfConstants.FAILURE_PREFIX 
					+ "at " + generator.describeCommand("fileCheck", comment) + ". " 
					+ "File exists: " + targetFile.getSpec());
		generator.decreaseIndentation();
		generator.outputLine("}");
		generator.outputEmptyLine();
	}
	
	
	/**
	 * This step verifies that a display is available. If there is no display 
	 * configured then the test will fail.
	 * Windows is assumed to always have a display attached.
	 * 
	 * @param comment is a brief description about what is happening.
	 * @throws StfException if perl code generation failed.
	 */
	public void doVerifyDisplayAvailable(String comment) throws StfException {
		if (!PlatformFinder.isWindows()) {
			generator.startNewCommand(comment, "display", "Verify display is available");
		
			generator.outputLine("if (!defined $ENV{'DISPLAY'}) {");
			generator.increaseIndentation();
			extensionBase.outputDieCommand(StfConstants.FAILURE_PREFIX 
						+ "at " + generator.describeCommand("display", comment) + ". " 
						+ "Display variable not set");
			generator.decreaseIndentation();
			generator.outputLine("}");
			generator.outputEmptyLine();
		}
	}

	
	/**
	 * Returns the java arguments that would be used when executing a java program
	 * in the current phase.
	 * This allows test automation code to verify that it is running with arguments
	 * which are mandatory for that particular test.
	 *
	 * @return a string containing the java arguments to be used in the current setup/execute or teardown phase.
	 * @throws StfException if invokes from the pluginInit() method.
	 */
	public String getJavaArgs(JavaVersion jvm) throws StfException {
		return generator.getBaseJvmOptions(jvm);
	}


	// Whenever a process is started the caller needs to decide if they want STF to
	// echo the output of the child process to the STF output.
	// Reference using: import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.*;
    public enum Echo { ECHO_ON, ECHO_OFF };

	
	/**
	 * Synchronously runs a java process.
	 * The test will fail at runtime if:
	 *   - the process exits with a non-zero exit value.
	 *   - the process completes, but the actual outcome does not match the expected outcome.
	 *   - process runtime exceeds the expected runtime.
	 * See SampleRunProcess.java for an example.
	 * 
	 * @param comment is a brief summary describing why the test is running the process.
	 * @param processMnemonic is a 3 letter code for this process. This is prefixed 
	 * to the processes output when echoed by STF.
	 * @param echoSetting used to turn on/off echoing of process output. 
	 * @param expectedOutcome describes what the test expects to happen when executed, and 
	 * for specifies a maximum run time for processes which are going to complete. 
	 * For example, completes with exit-code 0, crashes, never exits, etc.
	 * @param processDetails describes how to run the process.
	 * @return a STFProcess object to represent the process.
	 * @throws StfException if process runtime limit not set.
	 */
	public StfProcess doRunForegroundProcess(String comment, String processMnemonic, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		String programName = getProgramName(processDetails);
		
		generator.startNewCommand(comment, "Run " + programName, "Run foreground process",
						"Program:",     processDetails.getCommand(),
						"Mnemonic: ",   processMnemonic,
						"Echo:",        echoSetting.name(),
						"Expectation:", expectedOutcome.toString());

		return extensionBase.runForegroundProcess(comment, processMnemonic, echoSetting, expectedOutcome, processDetails);
	}

	
	/**
	 * Runs multiple processes using the same process definition.
	 * At runtime this call results in test execution blocking until the final process completes.
	 * Other than the numInstances parameter, all other arguments are as described by doRunForegroundProcess(). 
	 */
	public StfProcess[] doRunForegroundProcesses(String comment, String processMnemonic, int numInstances, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {

		String commandDescription = "Run " + getProgramName(processDetails) + "*" + numInstances;
		generator.startNewCommand(comment, commandDescription, "Run multiple concurrent foreground processes",
						"Program:",     processDetails.getCommand(),
						"Mnemonic:",    processMnemonic,
						"Instances:",   Integer.toString(numInstances),
						"Echo:",        echoSetting.name(),
						"Expectation:", expectedOutcome.toString());

		return extensionBase.runForegroundProcesses(comment, processMnemonic, numInstances, echoSetting, expectedOutcome, processDetails);
	}

	
	/**
	 * Start running a process in the background.
	 * Once the process has been started it can be referenced in a monitor call.
	 * Processes which never complete need to be killed before the end of the test stage.
	 * 
 	 * See STF-Manual.hmtl for more detailed information.
	 * See SampleClientServer.java or SampleConcurrentProcesses.java for an example.
	 * 
	 * The test will fail at runtime if there is an error starting the process. 
	 * 
	 * @param comment describes why the test is starting the process.
	 * @param processMnemonic is a 3 character mnemonic used to identify the process.
	 * @param echoSetting used to turn on/off echoing of process output. 
	 * @param expectedOutcome describes what the test expects to happen when executed.
	 * @param processDetails describes how to run the process.
	 * @return a STFProcess object to represent the process.
	 * @throws StfException if process runtime limit not set.
	 */
	public StfProcess doRunBackgroundProcess(String comment, String processMnemonic, Echo echoSetting, ExpectedOutcome expectedOutcome, 
			ProcessDefinition processDetails) throws StfException {
		
		String commandDescription = "Run " + getProgramName(processDetails);
		generator.startNewCommand(comment, commandDescription, "Start background process", 
				"Program:",     processDetails.getCommand(),
				"Mnemonic:",    processMnemonic,
				"Echo:",        echoSetting.name(),
				"Expectation:", expectedOutcome.toString());
		
		return extensionBase.runBackgroundProcess(comment, processMnemonic, echoSetting, expectedOutcome, processDetails);
	}

	/**
	 * This is a variation of doRunBackgroundProcess, and is used when you want to start 2
	 * or more processes with the same definition.
	 *  
	 * @param comment describes why the test is starting the process.
	 * @param processMnemonic is a 3 character mnemonic used to identify the process.
	 * @param numInstances is the number of concurrent processes to start.
	 * @param echoSetting used to turn on/off echoing of process output. 
	 * @param expectedOutcome describes what the test expects to happen when executed.
	 * @param processDetails describes how to run the process.
	 * @return a STFProcess object to represent the set of processes.
	 * @throws StfException if process runtime limit not set, or if numInstances < 2.
	 */
	public StfProcess[] doRunBackgroundProcesses(String comment, String processMnemonic, int numInstances, Echo echoSetting, 
			ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {

		String commandDescription = "Run " + getProgramName(processDetails) + "*" + numInstances;
		generator.startNewCommand(comment, commandDescription, "Start multiple concurrent background processes",
						"Program:",     processDetails.getCommand(),
						"Mnemonic:",    processMnemonic,
						"Instances:",   Integer.toString(numInstances),
						"Echo:",        echoSetting.name(),
						"Expectation:", expectedOutcome.toString());
		
		return extensionBase.runBackgroundProcesses(comment, processMnemonic, numInstances, echoSetting, expectedOutcome, processDetails);
	}


	// Extract the name of the program which this processDetails object plans to run
	private String getProgramName(ProcessDefinition processDetails) throws StfException {
		// If the command to run contains a path then extract just the name of the program 
		String[] commandElements = processDetails.getCommand().split("[\\\\/]");
		String programName = commandElements[commandElements.length-1];
		return programName;
	}

	
	/**
	 * This action allows the test to wait for one or more processes.
	 * The test blocks until the earliest of the following occours:
	 *   - a process completes but not as expected (test fails).
	 *   - core file detected for a process which is not expected to crash (test fails).
	 *   - a process exceeds its allowed runtime (test fails).
	 *   - all process which are expected to complete actually complete (test continues).
	 *   
	 * See STF-Manual.hmtl for more detailed information.
	 * See SampleClientServer.java or SampleConcurrentProcesses.java for an example.
	 * 
	 * @param comment is a brief comment to describe why the test is waiting. 
	 * @param processesToMonitor is one or more processes to monitor. Accepts 3 types of arguments:
	 *   1) A StfProcess object or 
	 *   2) An array of StfProcess objects.
	 *   3) Null. Ignoring null process references simplifies test case logic
	 * @throws StfException if attempting to monitor a process which must have already completed.
	 * Also throws a StfException if attempting to monitor only processes which never complete.
	 */
	public void doMonitorProcesses(String comment, Object... processesToMonitor) throws StfException {
		ArrayList<StfProcess> processesList = convertToProcessList(processesToMonitor);
		
		if (!processesList.isEmpty()) {
			generator.startNewCommand(comment, "Monitor", "Wait for processes to meet expectations", 
						"Processes:", processesList.toString());

			extensionBase.internalDoMonitorProcesses(comment, processesList);
		}
	}

	
	/**
	 * Kills one or more processes. 
	 * The processes being killed must have been started with one of the doRun process methods.
	 * 
	 * See STF-Manual.hmtl for more detailed information.
	 * See SampleClientServer.java or SampleConcurrentProcesses.java for an example.
	 * 
	 * @param comment is a brief comment summarising why the test is killing the process(es).
	 * @param processesesToKill is one or more processes to kill. Accepts 2 types of 
	 * arguments. 1) A StfProcess object or 2) An array of StfProcess objects.
	 * @throws StfException if attempting to kill a process which must have already completed.
	 */
	public void doKillProcesses(String comment, StfProcess... processesToKill) throws StfException {
		ArrayList<StfProcess> processesList = convertToProcessList(processesToKill);
		
		generator.startNewCommand(comment, "kill", "Kill running processes",
						"Processes:", processesList.toString());

		extensionBase.internalDoKillProcess(comment, processesList);
	}

	
	/**
	 * Converts a bunch of processes into a typed ArrayList of StfProcess objects.
	 * Accepts arguments which are either:
	 *   1) A StfProcess object, for a single background process.
	 *   2) An array of StfProcess objects, for when multiple instances have been started.
	 * This method does type validation and unpacks the array to returns an ArrayList of StfProcess objects. 
	 */
	private ArrayList<StfProcess> convertToProcessList(Object[] processesToMonitor) throws StfException {
		// Unpack any arrays so that we have a flattened set of arguments 
		ArrayList<Object> flattenedArguments = new ArrayList<Object>();
		for (Object o : processesToMonitor) {
			if (o == null) {
				// Ignore. Looks like test didn't start the process after all.
			} else if (o.getClass().isArray()) {
				Object[] objArray = (Object[]) o;
				for (int i=0; i<objArray.length; i++) {
					flattenedArguments.add(objArray[i]);
				}
			} else {
				flattenedArguments.add(o);
			}
		}
		
		// Produce return structure. Everything going in to it must be an StfProcess
		ArrayList<StfProcess> processesList = new ArrayList<StfProcess>();
		for (Object o : flattenedArguments) { 
			if (o instanceof StfProcess) {
				processesList.add((StfProcess) o);
			} else {
				throw new StfException("Supplied object is neither of 1) a StfProcess object or, 2) an array of StfProcess objects. " + o.toString());
			}
		}
		
		return processesList;
	}
	

	/**
	 * Creates an object which tests use to describe how to run a Java process using the primary JVM.
	 * Once populated the JavaProcessDefinition can be used to start a JVM.
	 * 
	 * @return a new JavaProcessDefinition object.
	 * @throws StfException 
	 */
	public JavaProcessDefinition createJavaProcessDefinition() throws StfException {
		return new JavaProcessDefinition(environmentCore);
	}


	/**
	 * Creates an object which tests use to describe how to run a Java process.
	 * Once populated the JavaProcessDefinition can be used to start a JVM.
	 * This method is only needed by tests which need to use the secondary JVM.
	 * 
	 * @param jvm describes the jvm to use. Either primary or secondary jvm.
	 * @return a new JavaProcessDefinition object.
	 * @throws StfException 
	 */
	public JavaProcessDefinition createJavaProcessDefinition(JavaVersion jvm) throws StfException {
		return new JavaProcessDefinition(environmentCore, jvm);
	}


	/**
	 * Creates an object which tests use to describe how to run a JDK tool or utility.
	 * Once populated the JavaProcessDefinition can be used to start the process
	 * in the foreground or the background.
	 * 
	 * @return a new JDKToolProcessDefinition object.
	 */
	public JDKToolProcessDefinition createJDKToolProcessDefinition() {
		return new JDKToolProcessDefinition(environmentCore);
	}


	/**
	 * Creates the bare bones process setup needed by a load test, using the primary jvm. 
	 * 
     * @return A new load test process definition.
	 * @throws StfException if an internal error is detected.
	 */
	public LoadTestProcessDefinition createLoadTestSpecification() throws StfException {
		return createLoadTestSpecification(environmentCore.primaryJvm());
	}

	
	/**
	 * Creates the bare bones process setup needed by a load test. 
	 *
	 * @param jvm specifies if the load test should be run with the primary or secondary jvm.
     * @return A new load test process definition.
	 * @throws StfException if an internal error is detected.
	 */
	public LoadTestProcessDefinition createLoadTestSpecification(JavaVersion jvm) throws StfException {
		LoadTestProcessDefinition loadTestInvocation = new LoadTestProcessDefinition(environmentCore, jvm)
			.addProjectToClasspath("stf.load")       // stf.load goes first to make sure we pick up the correct log4j config file
			.addProjectToClasspath("stf.core")
			.addPrereqJarToClasspath(JavaProcessDefinition.JarId.LOG4J_API)
			.addPrereqJarToClasspath(JavaProcessDefinition.JarId.LOG4J_CORE)
			.runClass("net.adoptopenjdk.loadTest.LoadTest")
			.setResultsDir(environmentCore.getResultsDir());
		
		loadTestInvocation.resetStageChecking();
		
		return loadTestInvocation;
	}

	
	/**
	 * Runs the jmod tool, to create Java jmod files.
	 *
	 * @param comment is a brief comment summarising why the test is killing the process(es).
	 * @param jmodDefition which describes how the jmod tool should be run.
	 * @return a ModuleRef object which points at the created jmod file.
	 */
	public ModuleRef doCreateJmod(String comment, JmodDefinition jmodDefinition) throws StfException {
		generator.startNewCommand(comment, "jmod", "Run jmod utility", 
				"jmod:", jmodDefinition.getJmodModuleRef().getName());
		
		// Run the jmod command to create a jmod file
		extensionBase.runForegroundProcess("Run jmod", "JMOD", ECHO_ON, ExpectedOutcome.cleanRun().within("1m"), jmodDefinition);
				
		return jmodDefinition.getJmodModuleRef();
	}

	
	/**
	 * Creates an object which can be used to run a program on the system path.
	 * This method should only be used after careful consideration of all options, as
	 * it has the potential to create fragile system dependent tests.
	 * 
	 * @return a new SystemProcessDefinition object.
	 */
	private SystemProcessDefinition createSystemProcessDefinition() {
		return SystemProcessDefinition.create();
	}
	
	
	/**
	 * Jars the contents of a compiled project.
	 *  
	 * It basically runs commands such as:
	 *   jar --create --file /stf/.../tmp/common.jar -C /tmp/runtimestest_build/ascii/test.modularity/bin/common com
	 *   
     * @param comment is a short explanation of why the test is creating a jar.
	 * @param projectSpec describes the location of the project code within the workspace. eg, "test.modularity/bin/common"
	 * @param archiveSpec contains the sub-directory or files to be added to the jar. 
	 * All names are relative to the projectSpec. eg, "com" or "net"
	 * @return a FileRef object which holds the location of the created jar.
	 * @throws StfException if anything goes wrong.
	 */
	public FileRef doCreateProjectJar(String comment, String projectSpec, String... archiveSpec) throws StfException {
		generator.startNewCommand(comment, "jar", "Create project jar",
				"ProjectSpec:", projectSpec,
				"ArchiveSpec:", formatStringArray(archiveSpec).toString());
	
		// Verify that the projectSpec is references an existing directory
		DirectoryRef projectDir = environmentCore.findTestDirectory(projectSpec);
		if (!projectDir.asJavaFile().exists()) { 
			throw new StfException("Project spec '" + projectSpec + "' does not point at a valid project. Full path is: " + projectDir);
		}
		
		// Work out what the jar will be called and where to create it
		String projectName;
		if (projectSpec.contains("/")) {
			projectName = projectSpec.substring(projectSpec.lastIndexOf('/')+1);  // Use last part of the project spec
		} else {
			projectName = projectSpec;
		}
		String jarName = projectName + ".jar";
		FileRef jarFile = environmentCore.getTmpDir().childFile(jarName);

		// Build the arguments for the jar process
		JDKToolProcessDefinition jarProcessDef = createJDKToolProcessDefinition()
				.setJDKToolOrUtility("jar")
				.addArg("--create")
				.addArg("--file", jarFile.getSpec())
		        .addArg("-C", projectDir.getSpec());
		        
        if (archiveSpec != null) {
			jarProcessDef.addArg(archiveSpec);
		} else {
			jarProcessDef.addArg(".");
		}

        // Run the jar process to create the jar
		extensionBase.runForegroundProcess(comment, "JAR", Echo.ECHO_ON, ExpectedOutcome.cleanRun().within("2m"), jarProcessDef);
		generator.outputLine("info('Created project jar: " + jarName + "');");
		
		return jarFile;
	}
	
	
	/**
	 * Jars the contents of a compiled project with the option to include and / or exclude certain files.
	 *  
	 * It first copies over the source folder into a temporary directory. 
	 * During the copy process it makes sure to only include the files that match the given list of 
	 * file extensions (if provided), and exclude the files that are specified in the list of excludes (if provided). 
	 * It then runs the jar command on the copied temp directory. 
	 * 
	 * @param comment is a short explanation of why the test is creating a jar.
	 * @param projectSpec describes the location of the project code within the workspace. eg, "test.modularity/bin/common"
	 * @param archiveSpec contains the sub-directory or files to be added to the jar. 
	 * All names are relative to the projectSpec. eg, "com" or "net"
	 * @param includeSpec a comma separated list of file extensions to copy (optional).
	 * @param excludeSpec a comma separated list of filenames to exclude (optional).
	 * @return a FileRef object which holds the location of the created jar.
	 * @throws StfException if anything goes wrong.
	 */
	public FileRef doCreateProjectJar(String comment, String projectSpec, String archiveSpec, 
			String includeSpec, String excludeSpec) throws StfException {
		
		String formattedIncludeList = formatCommaSeparatedList(includeSpec);
		String formattedExcludeList = formatCommaSeparatedList(excludeSpec);
		
		int commandNumber = generator.startNewCommand(comment, "jar", "Create project jar",
				"ProjectSpec:", projectSpec,
				"ArchiveSpec:", formatStringArray(archiveSpec).toString(),
				"ExtensionList:", formattedIncludeList,
				"ExcludeFileList:", formattedExcludeList);
		
		// Verify that the projectSpec is references an existing directory
		DirectoryRef projectDir = environmentCore.findTestDirectory(projectSpec);
		if (!projectDir.asJavaFile().exists()) { 
			throw new StfException("Project spec '" + projectSpec + "' does not point at a valid project. Full path is: " + projectDir);
		}
		
		// Create a temporary directory in which to copy over the source after applying the filters 
		DirectoryRef filteredProjectDir = environmentCore.getTmpDir().childDirectory(commandNumber + "FilteredProjDir"); 
		
		runCpDir("Copying source directory to a temporary directory to apply filters", 
				projectDir, filteredProjectDir, formattedIncludeList, formattedExcludeList);
		
		// Work out what the jar will be called and where to create it
		String projectName = null;
		
		if (projectSpec.contains("/")) {
			projectName = projectSpec.substring(projectSpec.lastIndexOf('/')+1);  // Use last part of the project spec
		} else {
			projectName = projectSpec;
		}

		String prefix = Integer.toString(commandNumber) + "."; 
		DirectoryRef targetDir = environmentCore.getModulesDir(); 
		
		// If we are creating an automatic module jar, we must have the original name of the module.
		// In this case we will create the jar in the temp directory instead of the module directory
		// to avoid potential name conflicts with other jars (e.g. modular jars) that may be created 
		// for the same module. 
		if (formattedExcludeList.contains("module-info.class")) {
			prefix = ""; 
			targetDir = environmentCore.getTmpDir();
		}
		
		String jarName = prefix + projectName + ".jar";
		FileRef jarFile = targetDir.childFile(jarName);

		// Build the arguments for the jar process
		JDKToolProcessDefinition jarProcessDef = createJDKToolProcessDefinition()
				.setJDKToolOrUtility("jar")
				.addArg("--create")
				.addArg("--file", jarFile.getSpec())
		        .addArg("-C", filteredProjectDir.getSpec());
		
		if (archiveSpec != null) {
			jarProcessDef.addArg(archiveSpec);
		} else {
			jarProcessDef.addArg(".");
		}
		
		// Run the jar process to create the jar
		extensionBase.runForegroundProcess(comment, "JAR", Echo.ECHO_ON, ExpectedOutcome.cleanRun().within("2m"), jarProcessDef);
		generator.outputLine("info('Created project jar: " + jarName + "');");
		
		return jarFile;
	}

	
	/**
	 * This action creates a modular jar. 
	 * It basically runs commands such as the following:
	 *   jar --create --file=mlib/com.greetings.jar --main-class=com.greetings.Main -C mods/com.greetings .
	 *
	 * @param comment is a short explanation of why the test is creating a modular jar.
	 * @param moduleSpec describes the location of the module within the workspace. eg, "test.modularity/bin/common-mods"
	 * @param moduleVersion is an optional version number for the modular jar, eg "1.0"
	 * @param mainClass is the optional main class for the modular jar.
	 * @param hashModules is the optional hash dependencies pattern for the modular jar.
	 * @return a FileRef object which holds the location of the created modular jar.
	 * @throws StfException 
	 */
	public ModuleRef doCreateModularJar(String comment, String moduleSpec, String moduleVersion, Class<?> mainClass, String hashModules) throws StfException {
		int commandNum = generator.startNewCommand(comment, "jar", "Create modular jar",
				"ModuleSpec:",    moduleSpec,
				"ModuleVersion:", moduleVersion,
				"MainClass:",     (mainClass==null) ? null : mainClass.getName(),
				"HashModules:",     (hashModules==null) ? null : hashModules);
	
		// Verify that the moduleSpec really is pointing at a compiled module
		DirectoryRef moduleDir = environmentCore.findTestDirectory(moduleSpec);
		FileRef moduleInfoFile = moduleDir.childFile("module-info.class");
		if (!moduleInfoFile.asJavaFile().exists()) { 
			throw new StfException("Module spec '" + moduleSpec + "' does not point at a valid module. "
					+ "Expected module-info.class file does not exist at: " + moduleInfoFile.getSpec());
		}
		
		// Work out what the jar will be called and where to create it
		String versionSpec = (moduleVersion != null && !moduleVersion.isEmpty()) ? "@"+moduleVersion : ""; 
		String moduleName = moduleDir.asJavaFile().getName();
		String jarName = commandNum + "." + moduleName + versionSpec + ".jar";
		FileRef jarFile = environmentCore.getModulesDir().childFile(jarName);

		// Build the arguments for the jar process
		JDKToolProcessDefinition jarProcessDef = createJDKToolProcessDefinition()
				.setJDKToolOrUtility("jar")
				.addArg("--create")
				.addArg("--file", jarFile.getSpec());
		if (moduleVersion != null && !moduleVersion.isEmpty()) {
			jarProcessDef = jarProcessDef.addArg("--module-version=" + moduleVersion);
		}
		if (mainClass != null) {
			jarProcessDef = jarProcessDef.addArg("--main-class=" + mainClass.getName());
		}
		if (hashModules != null && !hashModules.isEmpty()) {
			jarProcessDef = jarProcessDef.addArg("--hash-modules=" + hashModules);
		}
		jarProcessDef = jarProcessDef.addArg("-C", moduleDir.getSpec(), ".");
		
		// Run the jar process to create the modular jar
		extensionBase.runForegroundProcess(comment, "CMJ", Echo.ECHO_ON, ExpectedOutcome.cleanRun().within("2m"), jarProcessDef);
		generator.outputLine("info('Created modular jar: " + jarName + "');");
		
		return new ModuleRef(moduleName, jarFile);
	}


	/**
	 * Convenience method for creating modular jars which have neither a version number or main class.
	 * @see doCreateModularJar(String, String, String, Class<?>, String) 
	 */
	public ModuleRef doCreateModularJar(String comment, String moduleSpec) throws StfException {
		return doCreateModularJar(comment, moduleSpec, null, null, null);
	}

	/**
	 * Convenience method for creating modular jars with a version but without a main class.
	 * @see doCreateModularJar(String, String, String, Class<?>, String) 
	 */
	public ModuleRef doCreateModularJar(String comment, String moduleSpec, String moduleVersion) throws StfException {
		return doCreateModularJar(comment, moduleSpec, moduleVersion, null, null);
	}

	/**
	 * Convenience method for creating modular jars with a main class but without a version number.
	 * @see doCreateModularJar(String, String, String, Class<?>, String) 
	 */
	public ModuleRef doCreateModularJar(String comment, String moduleSpec, Class<?> mainClass) throws StfException {
		return doCreateModularJar(comment, moduleSpec, null, mainClass, null);
	}
	
	/**
	 * Convenience method for creating modular jars which have no main class, but a hash-module value.
	 * @see doCreateModularJar(String, String, String, Class<?>, String) 
	 */
	public ModuleRef doCreateModularJar(String comment, String moduleSpec, String moduleVersion, String hashModules) throws StfException {
		return doCreateModularJar(comment, moduleSpec, moduleVersion, null, hashModules);
	}
	

	/**
	 * Runs java9's jlink command, to create a runtime image.
	 * The image is create in the tmp dir of the results area.
	 * For example it may create a directory '/stf/20160722-142737-CpMpJlinkTest/tmp/2.HiJVM' 
	 * which contains 'bin/java'.
	 * 
	 * This method ends up running commands such as:
	 *   jlink --module-path /home/user/jdks/jdk-9_linux-x64_bin/jdk-9/jmods:/media/ramdisk/stf/20160720-132549-CpMpModularJarTest3/results/modules --add-modules com.hi --output /tmp/x
	 * 
     * @param comment is a short explanation of what the test is doing.
     * @param jlinkDefinition describes the jlink options which the test requires.
	 * @return A reference to the directory which will hold the custom JVM image.
	 * @throws StfException if anything goes wrong.
	 */
	public DirectoryRef doRunJlink(String comment, JlinkDefinition jlinkDefinition) throws StfException {
		generator.startNewCommand(comment, "jlink", "Run jlink to create runtime image",
				"ImageName:", jlinkDefinition.getOutputImageName());

		// If the test is deciding where to create the image then guard against
		// possibility that it may already contain an image by deleting its contents.
		DirectoryRef jlinkOutputDir = jlinkDefinition.getOutputImageDir();
		if (jlinkDefinition.usingCustomOuputImageDir()) {
			if (PlatformFinder.isWindows()) {
				generator.outputLine("stf::stfUtility->splatTree( dir => " + "'" + jlinkOutputDir.getSpec() + "' );");
			} else {
				generator.outputLine("rmtree( " + "'" + jlinkOutputDir.getSpec() + "', {error => \\$err" + "});");
			}
		}
			
		// Run the jlink command to create a custom JVM image
		extensionBase.runForegroundProcess("Run jlink", "JLNK", ECHO_ON, ExpectedOutcome.cleanRun().within("2m"), jlinkDefinition);

		return jlinkDefinition.getOutputImageDir();
	}


	/**
	 * Adds a block of perl code which scans a log file and counts the number of matches for supplied strings.
	 * The test will fail if the actual number of matches does not equal the expected number. 
	 * 
	 * It does this by creating a while loop which opens a log and does a regex match on each line.
	 * If a match is found the count variable is incremented, this method also supports multiple expected messages and
	 * builds the if statement arguments accordingly.
	 * 
     * @param comment is a short explanation of what the test is doing.
	 * @param file is the file whose output is scanned.
	 * @param expectedCount is the expected number of matches for the patterns in the file. 
	 * @param patterns contains the strings that the data file is expected to contain.
	 * @throws StfException if anything goes wrong.
	 */
	public void doCountFileMatches(String comment, FileRef file, int expectedCount, String... patterns) throws StfException {
		generator.startNewCommand(comment, "count", "Count file matches",
				"TargetFile:", file.getSpec(),
				"ExpectedNumMatches:", Integer.toString(expectedCount),
				"SearchStrings:", formatStringArray(patterns).toString());

		// Generate perl to count how many matches there are in the file
		extensionBase.outputCountFileMatches("$file_match_count", file, patterns);
		
		// Fail if the actual number of matches does not equal the expected number
		extensionBase.outputFailIfTrue("java", comment, "$file_match_count", "!=", expectedCount);
		generator.outputLine("info('Found " + expectedCount + " instances of " + formatStringArray(patterns) + "');");
	}
	

	/**
	 * Adds a block of perl code which scans a log file for supplied strings.
	 * The test will pass if there is *any* number of matches found for the supplied 
	 * string. It will fail if no occurrence of the given string is found in the log.  
	 * 
	 * It does this by creating a while loop which opens a log and does a regex match on each line.
	 * If a match is found the count variable is incremented, this method also supports multiple expected messages and
	 * builds the if statement arguments accordingly.
	 * 
     * @param comment is a short explanation of what the test is doing.
	 * @param file is the file whose output is scanned.
	 * @param patterns contains the strings that the data file is expected to contain.
	 * @throws StfException if anything goes wrong.
	 */
	public void doFindFileMatches(String comment, FileRef file, String... patterns) throws StfException {
		generator.startNewCommand(comment, "count", "Count file matches",
				"TargetFile:", file.getSpec(),
				"SearchStrings:", formatStringArray(patterns).toString());

		// Generate perl to count how many matches there are in the file
		extensionBase.outputCountFileMatches("$file_match_count", file, patterns);
		
		// Fail if the actual number of matches does not equal the expected number
		extensionBase.outputFailIfTrue("java", comment, "$file_match_count", "==", 0);
		generator.outputLine("info('Found instances of " + formatStringArray(patterns) + "');");
	}
	
	
	// Given an array of Strings, builds a coma separated list of Strings with double quote around each string
	// Added to prevent perl parsing errors by escaping string arguments which start and end
	// with single quotes. 
	private StringBuilder formatStringArray(String... arguments) {
		StringBuilder buff = new StringBuilder();
		if (arguments == null || arguments.length == 0 || arguments[0] == null) {
			return buff;
		}
		
		buff.append("[");
		for (int i=0; i<arguments.length; i++) {
			if (i>0) { 
				buff.append(", ");
			}
			String argValue = arguments[i];
			argValue = argValue.replaceAll("'", "\"");
			buff.append("\"" + argValue + "\"");
		}
		buff.append("]");
		return buff;
	}


	// Utility method to process a comma separated list of includes or excludes
	// and invoke formatStringArray() method to create the formatted StringBuilder
	private String formatCommaSeparatedList(String list) {
		if (list == null) {
			return null;
		}
		return formatStringArray(list.split(",")).toString();
	}  
	
	
	/**
	 * Verifies that java processes using the primary JVM will be run with the listed arguments.
	 * The test will fail with an error if any of the mandatory arguments are not present.
	 * 
	 * @param stage is the stage whose arguments should be checked.
	 * @param mandatoryArgs contains 1 or more java arguments.
	 * @throws StfException if this method is called from the setup, execute or teardown stages.
	 */
	public void verifyJavaArgsContains(Stage stage, String... mandatoryArgs) throws StfException {
		if (stage == Stage.INITIALISATION) {
			throw new StfException("Java argument verification is not valid for the initialisation stage.");
		}

		// Get hold of the mandatory and actual java arguments
		List<String> mandatoryArgsList = Arrays.asList(mandatoryArgs);
		ArrayList<String> actualArgs = getActiveJavaArgs(stage);

		// Subtract the actual args from the mandatory. 
		// The remaining args are mandatories which haven't been given a value.
		Set<String> mandatoryArgsSet = new LinkedHashSet<String>(mandatoryArgsList);
		Set<String> actualArgsSet = new LinkedHashSet<String>(actualArgs);
		mandatoryArgsSet.removeAll(actualArgsSet);

		if (!mandatoryArgsSet.isEmpty()) {
			throw new StfError("Unable to run test as the " + stage.getMethodName() + " stage "
					+ "is missing the following mandatory Java arguments: \"" + convertToString(new ArrayList<String>(mandatoryArgsSet)) + "\". "
					+ "Actual Java arguments for this stage are \"" + convertToString(actualArgs) + "\"");
		}
	}


	/**
	 * Verifies that none of the specified java arguments will be used when running
	 * java processes running on the primary JVM.
	 * The test will fail with an error if any of the arguments are present.
	 * 
	 * @param stage is the stage whose java arguments the caller wants to check.
	 * @param invalidArgs contains 1 or more java arguments.
	 * @throws StfError if any invalid argument would be used for Java processes.
	 */
	public void verifyJavaArgsDoesntContain(Stage stage, String... invalidArgs) throws StfException {
		if (stage == Stage.INITIALISATION) {
			throw new StfException("Java argument verification is not valid for the initialisation stage.");
		}

		// Get hold of the mandatory and actual java arguments
		List<String> invalidArgsList = Arrays.asList(invalidArgs);
		ArrayList<String> actualArgs = getActiveJavaArgs(stage);

		// Work out the intersection between the invalid args and the actual args.
		// Anything left in 'invalidArgsSet' is an arg than the caller says is invalid 
		// but which the current stage has a value for.
		Set<String> invalidArgsSet = new LinkedHashSet<String>(invalidArgsList);
		Set<String> actualArgsSet = new LinkedHashSet<String>(actualArgs);
		invalidArgsSet.retainAll(actualArgsSet);

		if (!invalidArgsSet.isEmpty()) {
			throw new StfError("Unable to run test as the " + stage.getMethodName() + " stage "
					+ "is has been given values for Java arguments that are not compatible with the test: \"" + convertToString(new ArrayList<String>(invalidArgsSet)) + "\". "
					+ "Actual Java arguments for this stage are \"" + convertToString(actualArgs) + "\"");
		}
	}
	
	
	// Returns the java arguments which are used for the specified stage.
	private ArrayList<String> getActiveJavaArgs(Stage stage) throws StfException {
		ArrayList<String> commandArgs = new ArrayList<String>();
		
		if (stage == Stage.EXECUTE) {
			// Add in the arguments that can optionally be supplied for the execute stage. 
			// This value will also contain the arguments derived from use of the -mode=xxxx option. 
			String initialJvmOptions = environmentCore.getProperty(Stf.ARG_JAVA_ARGS_EXECUTE_INITIAL);
			ArrayList<String> jvmOptionArgs = StringSplitter.splitArguments(initialJvmOptions);
			commandArgs.addAll(jvmOptionArgs);
		}
		
		// Get hold of the java arguments for the specified stage
		Argument baseJvmOptionsArgument = stage.getJavaArg(environmentCore.primaryJvm());
		String stageJavaArgs = environmentCore.getProperty(baseJvmOptionsArgument);
		
		// Add the regular java arguments for the stage onto the known command arguments
		commandArgs.addAll(StringSplitter.splitArguments(stageJavaArgs));
		
		return commandArgs;
	}
	
	
	// Concatenate together a list of strings.
	// @returns a String without commas between the elements so that the  
	// resulting string can be fed back in as java arguments.
	private String convertToString(List<String> mandatoryArgsList) {
		StringBuilder mandatorySpec = new StringBuilder();
		for (String s : mandatoryArgsList) {
			if (mandatorySpec.length() > 0) {
				mandatorySpec.append(" ");
			}
			mandatorySpec.append(s);
		}
		
		return mandatorySpec.toString();
	}
	
	
	/**
	 * Creates an environment variable.
	 * 
	 * @param variable is the environment variable that we want to set
	 * @param value is the value of the environment variable being set
	 * @throws StfException if something goes wrong
	 */
	public void doSetEnvVariable(String comment, String variable, String value) throws StfException {
		generator.startNewCommand(comment, "$ENV{variable}=value", "Set environment variable",
				"Variable:", variable,
				"Value:", value);
		
		generator.outputLine("$ENV{'" + variable + "'} = \"" + value + "\";");
	}


	/**
	 * This is a crude function to sleep for a given period in seconds. 
	 * 
	 * It is not normally a good approach to have sleep calls but this 
	 * provides a workaround until STF has an API to sync processes and 
	 * wait for specific events.   
	 * 
	 * @param period is the period in seconds to sleep for
	 * @throws StfException if something goes wrong
	 */
	public void doSleep(String comment, String period) throws StfException {
		generator.startNewCommand(comment, "sleep(time)", "Sleep for a given period",
				"period:", period);
		
		generator.outputLine("sleep(" + period + ");");
	}
}