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

import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_COMMAND_MNEMONIC;
import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_COMMAND_NUMBER;
import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_PROCESS_INSTANCE;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.util.FileOperations;


/**
 * This object captures the information needed to start a LoadTest process.
 * 
 * Parameters to control one or more suites are supplied following each call
 * to addSuite(). 
 * 
 * This class throws an exception if the Java process information is not built  
 * in the same order in which it will be used.
 * Callers should build the java process information in the following order:
 *   - Results directory
 *   - Time limit
 *   - suite arguments
 *   
 * Configuration of the load test is split into 2 parts:
 * 1) Values for the load test tool itself, eg, classpath, optional time limit.
 * 2) Workload definition. This consists of 1 or more suites. Each suite describes some tests to 
 * run and their configuration, eg, number of worker threads, test selection
 * mode (random or sequential), etc.
 *   
 * Load tests should generally run a fixed work load, as this means that at the 
 * end of test run it is possible to state that a exactly what has been run and
 * the result of that run.
 * If a test is run for a specific time period that there is no such certainty
 * over what has really been done due to variations in both machine and JVM performance.
 * The number of tests to run for each suite is configured by using the setSuiteNumTests(int)
 * method.
 * 
 * Some tests run a load test as a means of getting the machine to do some work.
 * Typically running it to represent an application that does some work, so that
 * another application can do some sort of monitoring. A total run time can be 
 * set by using the setTimeLimit() method. Tests are started until the time limit 
 * is reached. The total runtime is going to depend on how long the final test takes. 
 *
 * If a load test is started with both a fixed workload and an overall time limit
 * then the load test runs until the first limit is reached.
 *
 * See SampleLoadTest.java for a runnable example.
 */
public class LoadTestProcessDefinition implements ProcessDefinition {
    private static final Logger logger = LogManager.getLogger(LoadTestProcessDefinition.class.getName());

  	// Load test runs Java so this class is really provides a program 
	// specific interface to the LoadTest class
	private JavaProcessDefinition javaProcessDefinition;
	
	private StfEnvironmentCore environmentCore;
	
	private String currentSuiteName = null;
	private boolean haveThreadCount   = false;
	private boolean haveSeed          = false;
	private boolean haveInventoryFile = false;
	private boolean haveRepeatCount   = false;
	private boolean haveSelectionMode = false;
	private boolean haveThinkingTime  = false;
	
	// Keeps track of suite names that have already been used.
	private HashSet<String> knownSuites = new HashSet<String>();
	
	// To enforce correct buildup of invocation arguments, all the addition 
	// methods fall into one of these categories.
	private enum Stage {
		JVM_ARGS(1),
		CLASSPATH(2),
		CLASS(3),
		LOAD_TEST_ARGS(4),
		SUITE(5);
		
		private int level;
		Stage(int level) { this.level = level; }
	}

	// Holds the category of addition method last used
	private Stage previousStage = Stage.JVM_ARGS;
	
	// Keep track of relocated inventory files. 
	// They are copied from a workspace to the results directory
	private LinkedHashSet<String> relocatedInventoryFiles = new LinkedHashSet<String>();

	// Error reporting and abort on failure control
	private static int DEFAULT_REPORT_FAILURE_LIMIT = 1;
	private static int DEFAULT_ABORT_AT_FAILURE_LIMIT = 10;
	private boolean haveReportFailureLimit = false;
	private boolean haveAbortAtFailureLimit = false;
	
	// Disk space limits for execution logs
	private static String DEFAULT_MAX_TOTAL_LOG_FILE_SPACE = "200M";
	private static String DEFAULT_MAX_SINGLE_LOG_SIZE = "1/25";
	private boolean haveMaxTotalLogFileSpace = false;
	private boolean haveMaxSingleLogSize = false;
	
	
	public LoadTestProcessDefinition(StfEnvironmentCore environmentCore, JavaVersion jvm) throws StfException {
		this.environmentCore = environmentCore;
		javaProcessDefinition = new JavaProcessDefinition(environmentCore, jvm);
	}


	public boolean isJdkProgram() {
		return javaProcessDefinition.isJdkProgram();
	}
	
	public JavaVersion getJavaVersion() {
		return javaProcessDefinition.getJavaVersion();
	}
	
	/**
	 * Adds a value to be used as a Jvm option. eg, '-Xmx100M'
	 * @see JavaProcessDefinition.addJvmOption
	 */
	public LoadTestProcessDefinition addJvmOption(String... jvmOptions) throws StfException {
		checkAndUpdateLevel(Stage.JVM_ARGS);
		
		javaProcessDefinition.addJvmOption(jvmOptions);
		
		return this;
	}


	/**
	 * Defines the modules to add to '--add-modules'.
	 * @param rootModuleName contains a comma separated list of modules. Empty string values are ignored.
	 */
	public LoadTestProcessDefinition addModules(String rootModuleName) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);
		
		if (rootModuleName != null && !rootModuleName.isEmpty()) {
			javaProcessDefinition.addRootModule(rootModuleName);
		}
		
		return this;
	}
	
	
	/**
	 * Adds the bin directory of a workspaces project to the classpath.
	 * @see JavaProcessDefinition.addJvmOption
	 */
	public LoadTestProcessDefinition addProjectToClasspath(String projectName) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);

		logger.debug("Adding \" + projectName + \" to classpath");
		javaProcessDefinition.addProjectToClasspath(projectName);
		
		return this;
	}

	/**
	 * Adds a known systemtest-prereq jar to the classpath.
	 */
	public LoadTestProcessDefinition addPrereqJarToClasspath(JavaProcessDefinition.JarId jarId) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);

		javaProcessDefinition.addPrereqJarToClasspath(jarId);
		
		return this;
	}
		
	/**
	 * Adds a jar file to the classpath.
	 */
	public LoadTestProcessDefinition addJarToClasspath(FileRef jarReference) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);
		
		javaProcessDefinition.addJarToClasspath(jarReference);
		
		return this;
	}
	
	
	/**
	 * Adds 1 or more directories to the classpath.
	 */
	public LoadTestProcessDefinition addDirectoryToClasspath(DirectoryRef... directoryReferences) throws StfException {
		checkAndUpdateLevel(Stage.CLASSPATH);
		
		for (DirectoryRef dirRef : directoryReferences)
		javaProcessDefinition.addDirectoryToClasspath(dirRef);

		return this;
	}

	

	/**
	 * Declares the class to be run for this java process.
	 */
	public LoadTestProcessDefinition runClass(String javaClassName) throws StfException {
		checkAndUpdateLevel(Stage.CLASS);

		javaProcessDefinition.runClass(javaClassName);
		
		return this;
	}
	
	
	/**
	 * Sets the results directory which load test can write results to.
	 */
	public LoadTestProcessDefinition setResultsDir(DirectoryRef resultsDir) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);

		// Use placeholder values to make sure a different output file is created for each execution. Eg '2.SCL1.'
		String resultsPrefix = PLACEHOLDER_STF_COMMAND_NUMBER + "." + PLACEHOLDER_STF_COMMAND_MNEMONIC + PLACEHOLDER_STF_PROCESS_INSTANCE + ".";

		javaProcessDefinition.addArg("-resultsDir", resultsDir.getSpec());
		javaProcessDefinition.addArg("-resultsPrefix", resultsPrefix);
		
		return this;
	}

	/**
	 * Optional method which sets a maximum runtime for the load test.
	 * If set, then no more tests will be started once the time limit is reached.
	 * Most test scenarios are best run with a fixed workload (using setSuiteNumTests()), but
	 * this method can be useful if you need some load to run for a fixed period of time.
	 * @param limitSpec a String describing the limit. Supports h,m,s units. eg, '45s' or '5m30s' or '1h30m', etc
	 * @return Updated load test process definition.
	 */
	public LoadTestProcessDefinition setTimeLimit(String limitSpec) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);
		
		javaProcessDefinition.addArg("-timeLimit", limitSpec);
		
		return this;
	}

	/**
	 * Optional method which sets the policy when an OutOfMemoryError is detected.
	 * @param abortIfOOM can be set to true (the default) to abort all worker threads 
	 * as soon as possible after detecting an OutOfMemoryError. The threads will complete 
	 * their current test before exiting.
	 * It set to false then the LoadTest will not abort on OOM. ie, it will do its best to 
	 * continue until the workload is complete.
	 * @return Updated load test process definition.
	 */
	public LoadTestProcessDefinition setAbortIfOutOfMemory(boolean abortIfOOM) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);
		
		javaProcessDefinition.addArg("-abortIfOutOfMemory", Boolean.toString(abortIfOOM));
		
		return this;
	}
	
	/**
	 * Optional method which controls how many test failures are reported in detail, with 
	 * a description of the failing test case and a stack trace, etc.
	 * Once the reporting limit is reached load test prints out a single line to say that a failure
	 * has been detected but doesn't provide any further information. 
	 * 
	 * @param reportFailureLimit is the number of failures to be reported in detail. Defaults to 1. 
	 * Set to '-1' to disable, causing all test failures to be reported in detail.
	 * @return Updated load test process definition.
	 */
	public LoadTestProcessDefinition setReportFailureLimit(int reportFailureLimit) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);
		
		javaProcessDefinition.addArg("-reportFailureLimit", Integer.toString(reportFailureLimit));
		haveReportFailureLimit = true;
		
		return this;
	}

	/**
	 * Optional method which sets the number of test failures which load test 
	 * allows before aborting a test run. 
	 * 
	 * @param abortAtFailureLimit is the maximum number of failures before load test 
	 * will abort a run. Set to '-1' to disable, so that load test never aborts a run.
	 * @return Updated load test process definition.
	 */
	public LoadTestProcessDefinition setAbortAtFailureLimit(int abortAtFailureLimit) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);
		
		javaProcessDefinition.addArg("-abortAtFailureLimit", Integer.toString(abortAtFailureLimit));
		haveAbortAtFailureLimit = true;
		
		return this;
	}
	
	/**
	 * Optional method to specify amount of disk space allocated to load test 
	 * execution logs (to record start+end of each test invocation)
	 * @param size is a string in the form '<size>[g|G|m|M|k|K]', eg '250M' or '1G'
	 * @return Update load test process definition.
	 */
	public LoadTestProcessDefinition setMaxTotalLogFileSpace(String size) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);

		javaProcessDefinition.addArg("-maxTotalLogFileSpace", size);
		haveMaxTotalLogFileSpace = true;
		
		return this;
	}
	
	/**
	 * Optional method to specify maximum amount of disk space used for a
	 * single execution logs.
	 * @param size is a string in the form '<size>[g|G|m|M|k|K] | 1/<number>', 
	 * eg '250M' or '1G' for a fixed size,
	 * or '1/50' to divide the maxTotalLogFileSpace into 50 parts. 
	 * @return Update load test process definition.
	 */
	public LoadTestProcessDefinition setMaxSingleLogSize(String size) throws StfException {
		checkAndUpdateLevel(Stage.LOAD_TEST_ARGS);

		javaProcessDefinition.addArg("-maxSingleLogSize", size);
		haveMaxSingleLogSize = true;
		
		return this;
	}
	
	/**
	 * Tell the load test process definition that you want supply arguments for the numbered suite.
	 * Suite numbers start at 0 and must be sequential. 
	 */
	public LoadTestProcessDefinition addSuite(String suiteName) throws StfException {
		// Supply defaults for error handling
		if (!haveReportFailureLimit) {
			setReportFailureLimit(DEFAULT_REPORT_FAILURE_LIMIT);
		}
		if (!haveAbortAtFailureLimit) {
			setAbortAtFailureLimit(DEFAULT_ABORT_AT_FAILURE_LIMIT);
		}
		
		// Supply log space defaults if not already set
		if (!haveMaxTotalLogFileSpace) {
			setMaxTotalLogFileSpace(DEFAULT_MAX_TOTAL_LOG_FILE_SPACE);
		}
		if (!haveMaxSingleLogSize) {
			setMaxSingleLogSize(DEFAULT_MAX_SINGLE_LOG_SIZE);
		}
		
		checkAndUpdateLevel(Stage.SUITE);

		// Make sure the suite name doesn't have any characters that would break perl
		Scanner suiteScanner = new Scanner(suiteName);        
	    String validationResult = suiteScanner.findInLine("[ \t\r\n\\\"'.,]");
	    suiteScanner.close();
	    if (validationResult != null) {
	        throw new StfException("Invalid suite name: '" + suiteName + "'. It cannot contain white space, slash or quote characters");
	    }
	    
	    if (!knownSuites.isEmpty()) {
			validateSuiteArgs();  // validate the arguments of previous suite
		}
		if (knownSuites.contains(suiteName)) {
			throw new StfException("Suite name not unique: " + suiteName);
		}
		knownSuites.add(suiteName);
		this.currentSuiteName = suiteName;
		
		haveThreadCount   = false;
		haveSeed          = false;
		haveInventoryFile = false;
		haveRepeatCount   = false;
		haveSelectionMode = false;
		haveThinkingTime  = false;

		return this;
	}


	/**
	 * Mandatory method which sets the number of threads for the current suite.
	 * @param threadCount is the number of threads to use.
	 * @return Updated process definition.
	 */
	public LoadTestProcessDefinition setSuiteThreadCount(int threadCount) throws StfException {
		outputSuiteArg("threadCount", Integer.toString(threadCount));
		haveThreadCount = true;
		
		return this;
	}

	/**
	 * Convenience method which sets the thread count but won't allow it below a minimum value.
	 * @param threadCount is a calculated value for the number of threads.
	 * @param minThreadCount is the minimum number of threads that will be used.
	 * @return Updated process definition.
	 */
	public LoadTestProcessDefinition setSuiteThreadCount(int threadCount, int minThreadCount) throws StfException {
		int actualThreadCount = Math.max(threadCount, minThreadCount);
		return setSuiteThreadCount(actualThreadCount);
	}

	/**
	 * Convenience method for setting thread count within min and max limits.
	 * @param threadCount is expected to be the number of available cores minus 1 or 2
	 * to account for the JIT/GC or more suites.
	 * @param minThreadCount is the minimum number of threads that will be used.
	 * @param maxThreadCount is the maximum number of threads that will be used.
	 * @return Updated process definition.
	 */
	public LoadTestProcessDefinition setSuiteThreadCount(int threadCount, int minThreadCount, int maxThreadCount) throws StfException {
		int actualThreadCount = threadCount;
		if (threadCount < minThreadCount) {
			actualThreadCount = minThreadCount;
		} else if (threadCount > maxThreadCount) {
			actualThreadCount = maxThreadCount;
		}

		return setSuiteThreadCount(actualThreadCount);
	}

	
	/**
	 * Optional method to set the seed for the suites random number generator.
	 * If not called then a new seed will be used for the run.
	 */
	public LoadTestProcessDefinition setSuiteSeed(long seed) throws StfException {
		outputSuiteArg("seed", Long.toString(seed));
		haveSeed = true;
		
		return this;
	}

	
	/**
	 * Mandatory method which specifies a file listing the tests to be used.
	 * @param inventoryFileRef points to an inventory file to use for the load test.
	 * The reference of the inventory file is relative to one of the test-roots.
	 */
	public LoadTestProcessDefinition setSuiteInventory(String inventoryFileRef) throws StfException {
		ArrayList<DirectoryRef> testRoots = environmentCore.getTestRoots();
		if (!inventoryFileRef.startsWith("/")) {
			throw new StfException("inventory file name must start with a '/' to indicate a file below the root directory. Inventory=" + inventoryFileRef);
		}
		
		// We want the generated command to use an inventory file in the results
		// directory (to allow easy hacking at runtime).
		// The implications of this are:
		//   1) The generated command needs to reference a file in the results dir.
		//   2) We need to copy the inventory file to the results dir (done in generationComplete() 
		//      once we know the command number and mnemonic)
		FileRef newInventoryFile = calculateRelocatedInventoryFile(inventoryFileRef);
		outputSuiteArg("inventoryFile", newInventoryFile.getSpec());

		// Build a list of all inventory files which are going to be used.
		ArrayList<String> allInventoryFiles = new ArrayList<String>();
		findAllInventoryFiles(allInventoryFiles, testRoots, inventoryFileRef);
		
		relocatedInventoryFiles.addAll(allInventoryFiles);
		haveInventoryFile = true;
		
		// Find the full set of exclusion files
		ArrayList<String> allExcludeFiles = new ArrayList<String>();
		for (String inv : allInventoryFiles) {
			// See if the current inventoryFile has any exclude files
			ArrayList<String> excludeFiles = LoadTestProcessDefinition.findExclusionFiles(testRoots, inv);
			allExcludeFiles.addAll(excludeFiles);
		}

		// Output load test argument to describe the exclude files for the current suite
		if (!allExcludeFiles.isEmpty()) {
			for (String excludeFileRef : allExcludeFiles) {
				FileRef relocatedExcludeFile = calculateRelocatedInventoryFile(excludeFileRef);
				outputSuiteArg("inventoryExcludeFile", relocatedExcludeFile.getSpec());
			}
		} else {
			outputSuiteArg("inventoryExcludeFile", "none");
		}
		
		// Make sure all the exclude files get copied to the results dir
		relocatedInventoryFiles.addAll(allExcludeFiles);

		logger.debug("Inventory files to copy:");
		for (String relFile : relocatedInventoryFiles) {
			logger.debug("  " + relFile);
		}

		return this;
	}


	// This method recursively finds all inventory files. 
	// It starts with an initial inventory file and follows all 'include references'.
	// It uses a bare bones parser to skim the file and follow the includes. InventoryData.java does the real parsing.
	// @param allInventoryFiles is the discovered list of all used inventory files.
	// @param testRoots is the list of tests root directories which may contain inventory file references.
	// @param inventoryFileRef is the location of an inventoryFile below one of the testRoots.
	private void findAllInventoryFiles(ArrayList<String> allInventoryFiles, ArrayList<DirectoryRef> testRoots, String inventoryFileRef) throws StfException {
		allInventoryFiles.add(inventoryFileRef);
		
		// Find and validate inventory file.
		File inventoryFile = FileRef.findFile(inventoryFileRef,testRoots).asJavaFile();
		
		// Open inventory file as xml document
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		Document doc;
		try {
			doc = dbFactory.newDocumentBuilder().parse(inventoryFile);
		} catch (Exception e) {
			throw new StfException("Failed to parse file:" + inventoryFile.getAbsolutePath(), e);
		}
		
		// Process every top level 'inventory' node
		for (int i = 0; i < doc.getChildNodes().getLength(); i++) {
			Node inventoryNode = doc.getChildNodes().item(i);
			if (!inventoryNode.getNodeName().equals("inventory") && !inventoryNode.getNodeName().equals("#comment")) {
				throw new IllegalStateException("Expected 'inventory' node but was '" + inventoryNode.getNodeName());
			}

			// Parse inventory content. Expecting a list of tests
			NodeList testNodes = inventoryNode.getChildNodes();
			for (int j=0; j<testNodes.getLength(); j++) {
				Node testNode = testNodes.item(j);
				if (testNode.getNodeType() == Node.TEXT_NODE || testNode.getNodeType() == Node.COMMENT_NODE) {
					continue;  // Ignore text and comment nodes
				}
				
				if (testNode.getNodeName().equals("include")) {
					Node includeInventoryNode = testNode.getAttributes().getNamedItem("inventory");
					if (includeInventoryNode == null) {
						throw new IllegalStateException("Failed to find mandatory 'inventory' attribute for include node. Child index: " + j + " in file: " + inventoryFileRef);
					}
					String includeFileName = includeInventoryNode.getNodeValue();
					if (!includeFileName.startsWith("/")) {
						throw new StfException("Included inventory file must start with a '/' to indicate a file below the root directory. Inventory: " + inventoryFile + " is attempting to include: " + includeFileName);
					}
					try {
						FileRef.findFile(includeFileName,testRoots).asJavaFile();
					} catch (StfException e) {	
						throw new StfException("Inventory file '" + inventoryFile + "' is attempting to include file '" + includeFileName + "', but failed. This attempt resulted in this error: " + e.getMessage());
					}
					findAllInventoryFiles(allInventoryFiles, testRoots, includeFileName);
				}
			}
		}
	}


	// Work out where the given inventory file will be once it has been copied to the results directory
	private FileRef calculateRelocatedInventoryFile(String inventoryFileRef) throws StfException {
		// Create the name of the directory to copy inventory files to, eg, 1.LT.inventory
		String inventoryDirName = PLACEHOLDER_STF_COMMAND_NUMBER + "." + PLACEHOLDER_STF_COMMAND_MNEMONIC + "." + StfConstants.INVENTORY_DIR_SUFFIX; 
		FileRef newInventoryFile = environmentCore.getResultsDir().childDirectory(inventoryDirName).childFile(inventoryFileRef);
		return newInventoryFile;
	}

	/**
	 * Optional method which controls how many tests are to be run for this suite.
	 * Worker threads will run tests until the supplied number of tests have been started.
	 * If this value is not specified then the load test runs until the time limit
	 * specified by setTimeLimit().
	 * 
	 * For example, if the inventory file contains tests A,B,C,D,E. 
	 * Then randomly executing 4 tests may run B,E,A,E or A,C,B,B.
	 * Sequentially executing 4 tests will always run A,B,C,D 
	 * Sequentially executing 7 tests would run A,B,C,D,E,A,B
	 * If the repetition count is to 2 then sequentially executing 7 tests would run A,A,B,B,C,C,D
	 */
	public LoadTestProcessDefinition setSuiteNumTests(int numTests) throws StfException {
		outputSuiteArg("totalNumberTests", Integer.toString(numTests));
		
		return this;
	}
	
	/**
	 * Optional method which sets how many times each test will be run before the selection 
	 * of the the next text.
	 * If not supplied then the default of '1' is used. ie. run a test once before 
	 * picking the next test.
	 * There is no interaction of this value with the setSuiteNumTests value. For example,
	 * if we are running 9 randomly selected tests with a repeat count of 4, then we may 
	 * execute test numbers: C,C,C,C,A,A,A,A,D 
	 */
	public LoadTestProcessDefinition setSuiteTestRepeatCount(int repeatCount) throws StfException {
		outputSuiteArg("repeatCount", Integer.toString(repeatCount));
		haveRepeatCount = true;
		
		return this;
	}

	/**
	 * Sets the amount of time which each load test thread should wait between executing 
	 * successive tests of the current suite. 
	 * Each load test thread has it's own random number generator which picks a pause 
	 * time between the specified min and max times. 
	 * The min and max can be set to the same value for a fixed delay.
	 * If not set then min and max default to zero, which means that the load test thread 
	 * will not sleep between tests.  
	 * @param minThinkingTime The minimum sleep time. Supported time units are 'h', 'm', 's' and 'ms'. eg, '100ms1
	 * @param maxThinkingTime The maximum sleep time. eg, '2s'.
	 * @throws StfException 
	 */
	public LoadTestProcessDefinition setSuiteThinkingTime(String minThinkingTime, String maxThinkingTime) throws StfException {
		String thinkTimeSpec = minThinkingTime + ".." + maxThinkingTime;
		outputSuiteArg("thinkingTime", thinkTimeSpec);
		haveThinkingTime = true;
		
		return this;
	}
	
	/**
	 * Tests to run will be selected sequentially, A,B,C,D,E,F,G,...
	 * If the final test in the inventory list has been executed then the next test 
	 * will be the first in the list.
	 * This method or the setSuiteRandomSelection() method must be called for each suite.
	 */
	public LoadTestProcessDefinition setSuiteSequentialSelection() throws StfException {
		outputSuiteArg("selection", "sequential");
		haveSelectionMode = true;
		
		return this;
	}

	/**
	 * Tests to run will be selected randomly.
	 * There is no weighting at present.
	 * This method or the setSuiteSequentialSelection() method must be called for each suite.
	 */
	public LoadTestProcessDefinition setSuiteRandomSelection() throws StfException {
		outputSuiteArg("selection", "random");
		haveSelectionMode = true;
		
		return this;
	}

	
	private void outputSuiteArg(String suiteFieldName, String value) throws StfException {
		checkAndUpdateLevel(Stage.SUITE);

		javaProcessDefinition.addArg("-suite." + currentSuiteName + "." + suiteFieldName);
		javaProcessDefinition.addArg(value);
	}

	
	// Validate the values for the current suite.
	// Make sure that all mandatory values have been supplied.
	private void validateSuiteArgs() throws StfException {
		if (currentSuiteName == null) {
			throw new IllegalStateException("No suite data supplied for first suite. Call 'LoadTestProcessDefinition.addSuite(\"name\")'");
		}
		
		// Verify that mandatory suite values have been set
		boolean mandatoryArgsAllSet = haveThreadCount && haveInventoryFile && haveSelectionMode;
		if (!mandatoryArgsAllSet) {
			throw new IllegalStateException("Incomplete suite data. Values must be supplied for threadCount, inventoryFile and the selection mode");
		}
		
		// Make sure that defaults are set for optional values
		if (!haveSeed) {
			setSuiteSeed(-1);
		}
		if (!haveRepeatCount) {
			setSuiteTestRepeatCount(1);
		}
		if (!haveThinkingTime) {
			setSuiteThinkingTime("0ms", "0ms");
		}
	}

	
	/**
	 * @return "java" as this process definition class can only run java.
	 * @throws StfException 
	 */
	@Override 
	public String getCommand() throws StfException {
		return javaProcessDefinition.getCommand();
	}
	
	
	/**
	 * Returns a HashMap containing a list of data that we want to get from all processes in the relatedProcesses HashMap.
	 * @return A HashMap where each key is a unique String, and each Integer is a PERL_PROCESS_DATA key linked to a specific 
	 * 		   operation that can be performed after appending the perl variable representing a specific process.
	 */
	public HashMap<String, Integer> getRelatedProcessesData() {
		return new HashMap<String, Integer>();
	}

	
	/**
	 * Returns a HashMap containing links to all processes that have been identified as related to this process.
	 * @return A HashMap where each key is a unique String, and each StfProcess is a process related to this process.
	 */
	public HashMap<String, StfProcess> getRelatedProcesses() {
		return new HashMap<String, StfProcess>();		
	}
	
	
	/**
	 * Creates strings for running the command.
	 * @throws StfException if validation of the suites arguments fails.
	 */
	@Override
	public ArrayList<String> asArgsArray() throws StfException {
		validateSuiteArgs();
		return javaProcessDefinition.asArgsArray();
	}


	/**
	 * Not for normal use. 
	 * This method resets the internal state which tracks the last method used.
	 * Only to be used by extensions which pre-populate a bare bones process definition.
	 */
	public void resetStageChecking() {
		javaProcessDefinition.resetStageChecking();
		previousStage = Stage.JVM_ARGS;
	}


	// Verifies that the addition method is not being called at the wrong time.
	// eg. throws exception if attempting to add to the classpath if the last
	// call was adding an application argument.
	private void checkAndUpdateLevel(Stage newStage) throws StfException {
		if (newStage.level < previousStage.level) { 
			throw new StfException("Java invocation built out of sequence. " + newStage + " cannot be set after " + previousStage);
		}
		
		previousStage = newStage;
	}
	
	
	@Override
	public void generationCompleted(int commandSerialNum, String processMnemonic) throws StfException {
		// Copy all inventory files from a workspace to the results directory. 
		// This allows for easy modification of the inventory, followed by manual re-execution.
		// Inventory files may be under a testRoot
		for (String sourceFileName : relocatedInventoryFiles) {
			File sourceFile = environmentCore.findTestFile(sourceFileName).asJavaFile();
			String destDirName = commandSerialNum + "." + processMnemonic + ".inventory";
			FileRef destFile = environmentCore.getResultsDir().childDirectory(destDirName).childFile(sourceFileName);
			logger.debug("Copying inventory file. Source=" + sourceFile.getAbsolutePath() + " Destination=" + destFile);
			FileOperations.copyFile(sourceFile, destFile.asJavaFile());
		}
	}
	
	
	/**	
	 * Finds the exclusion files for the specified inventory file.
	 *
	 * @param inventoryFile points to the inventory file.
	 * @return an arrayList containing a File object for each exclusion file. 
	 * If there are no exclusion files then the list is empty. 
	 * @throws StfException 
	 */
	public static ArrayList<String> findExclusionFiles(ArrayList<DirectoryRef> testRoots, String inventoryFileRef) throws StfException {
		File inventoryFile = FileRef.findFile(inventoryFileRef,testRoots).asJavaFile();

		// Work out the exclusion file name to look for. eg for 'mauvePt1.xml' it would be 'mauvePt1_exclude*'
		int extensionAt = inventoryFile.getName().lastIndexOf(".");
		String baseFileName = inventoryFile.getName().substring(0, extensionAt);
		String searchPrefix = baseFileName + "_exclude";
		
		// Work out the directory holding the inventory file
		int finalSlash = inventoryFileRef.lastIndexOf('/');
		String inventoryDir = inventoryFileRef.substring(0, finalSlash);
		
		// Find all of the exclusion files in the same directory as the inventory files
		ArrayList<String> exclusionFiles = new ArrayList<String>();
		for (File ef : inventoryFile.getParentFile().listFiles()) {
			if (ef.getName().startsWith(searchPrefix)) {
				String excludeFileRef = inventoryDir + "/" + ef.getName();
				logger.debug("Saving inventory exclusion file: " + excludeFileRef);
				exclusionFiles.add(excludeFileRef);
			}
		}
	
		return exclusionFiles;
	}
}