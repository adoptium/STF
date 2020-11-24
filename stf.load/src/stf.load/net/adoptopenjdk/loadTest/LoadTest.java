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

package net.adoptopenjdk.loadTest;

import java.io.File;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.loadTest.SuiteData.SelectionMode;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.util.TimeParser;


/**
 * This class is the entry point for running a load test.
 * Most of it's work is to parse the command line arguments, before kicking off the test run.
 * 
 * It uses these exit codes:
 *   0 - All tests ran successfully.
 *   1 - One or more tests failed.
 *   2 - Failed with usage message.
 */
public class LoadTest {
    private static final Logger logger = LogManager.getLogger(LoadTest.class.getName());

	// Supports suites numbered 0..7
	private static int MAX_SUITE_NUMBER = 7;

	private File resultsDir = null;
	private String resultsPrefix = "";
	
	// If the test is going to run for a fixed time period then no new tests will be
	// started after the testEndTime
	private boolean timeLimitedTest = false;
	private String timeLimitString;
	private long testEndTime;
	
	// If set, test will fail due to inactivity after the provided duration of 
	// inactivity in a live test is encountered 
	private String inactivityLimitString; 
	private long inactivityLimit = 15 * 60 * 1000; // By default, set to 15 minutes; 
	
	// LoadTest behaviour on OMM controllable through argument
	private boolean abortIfOutOfMemory = true; 
	
	// This flag indicates whether or not to create core dumps on the event of first load test failure 
	private boolean dumpRequested = false; 
	
	// Error reporting control
	private int reportFailureLimit = 10; 
	private int abortAtFailureLimit = 25;
	
	// To limit the disk space used for execution logs
	private long maxTotalLogFileSpace;
	private int maxSingleLogSize;
	
	private ArrayList<SuiteData> suites = new ArrayList<SuiteData>();
	
	// Holds the names of the fields that can be used to specify arguments for a suite.
	// For example: -suite.0.threadCount 2
	private static final Set<String> SUITE_ARGUMENTS = new HashSet<String>(Arrays.asList(
		    "threadCount", 
			"seed",
			"inventoryFile", 
			"inventoryExcludeFile", 
			"totalNumberTests", 
			"repeatCount",
			"thinkingTime",
			"selection"  // random or sequential
			));
	
	// Holds data on a single argument for suite data. eg '-suite0.threadCount 2'
	private static class SuiteArg { 
		private String suiteName;
		private String argName;
		private String argValue;
	}
	

	public static void main(String[] args) {
		// Parse the arguments and inventory files
		LoadTest loadTest = null;
		try {
			loadTest = new LoadTest(args);
		} catch (Exception e) {
			logger.fatal("Failed to initialise LoadTest", e);
			System.exit(2);
		}
		
		// Setup a security manager to block System.exit attempts
		SecurityManager defaultSecurityManager = System.getSecurityManager();
		overrideSecurityManager();

		// Run the tests
		long numberFailingTests = -1;
		try {
			numberFailingTests = loadTest.runLoadTest();
		} catch (Exception e) {
			logger.fatal("Failed during LoadTest execution", e);
			System.exit(3);
		}
		
		// Restore original security manager
		System.setSecurityManager(defaultSecurityManager);
		
		// Exit with a non-zero value if a test has failed
		int exitCode = numberFailingTests == 0 ? 0 : 1;
		System.exit(exitCode);
	}


	private static void overrideSecurityManager() {
	    System.setSecurityManager(new SecurityManager() { 
	        @Override
	    	public void checkExit(int status) {
	        	// Don't allow the test to exit the process
	     		super.checkExit(status);
	    		throw new BlockedExitException(status);
	    	}
	    	 
	    	public void checkPermission(Permission perm) {
	    	}
	        
	        // Don't block permission check, so that log4j will work
	        public void checkPermission(Permission perm, Object context) {
	        }
	     });
	}


	private long runLoadTest() throws Exception {
		// Create now, so that it's ready to go to work when a failure happens
		FirstFailureDumper.createInstance();
		
		LoadTestRunner loadTestRunner = new LoadTestRunner(resultsDir, resultsPrefix, 
				timeLimitedTest, testEndTime, inactivityLimit, 
				abortIfOutOfMemory,
				reportFailureLimit, abortAtFailureLimit,
				maxTotalLogFileSpace, maxSingleLogSize,
				suites, dumpRequested);
		return loadTestRunner.run();
	}

	
	public LoadTest(String[] args) throws Exception {
		String maxTotalLogFileSpaceStr = null;
		String maxSingleLogSizeStr = null;
		
		// Make sure we can number each suite as we parse the program arguments
		String currSuiteName = null;
		int currSuiteNum = -1;
		HashSet<String> previousArgs = new HashSet<String>();

		// Create a HashMap for each suite. Holds the suites arguments from the command line.
		@SuppressWarnings("unchecked")
		HashMap<String, String>[] suiteData = (HashMap<String, String>[]) new HashMap[MAX_SUITE_NUMBER];
		for (int i=0; i<MAX_SUITE_NUMBER; i++) {
			suiteData[i] = new HashMap<String, String>();
		}
		String[] suiteNames = new String[MAX_SUITE_NUMBER];
		
		int i = 0;
		while (i < args.length) {
			String argName = args[i++];
			
			if (argName.equals("-resultsDir")) {
				String resultsDirString = getArgValue(args, i++);
				this.resultsDir = new File(resultsDirString);

			} else if (argName.equals("-resultsPrefix")) {
				this.resultsPrefix = getArgValue(args, i++);;
			
			} else if (argName.equals("-timeLimit")) {
				this.timeLimitedTest = true;
				this.timeLimitString = getArgValue(args, i++);
				long timeLimitSeconds = TimeParser.parseTimeSpecification(timeLimitString).getSeconds();
				this.testEndTime = System.currentTimeMillis() + (timeLimitSeconds * 1000);

			} else if (argName.equals("-inactivityLimit")) {
				this.inactivityLimitString = getArgValue(args, i++);
				long inactivityLimitSeconds = TimeParser.parseTimeSpecification(inactivityLimitString).getSeconds();
				this.inactivityLimit = System.currentTimeMillis() + (inactivityLimitSeconds * 1000);

			} else if (argName.equals("-abortIfOutOfMemory")) {
				String abortValue = getArgValue(args, i++);
				this.abortIfOutOfMemory = Boolean.parseBoolean(abortValue);
			
			} else if (argName.equals("-dumpRequested")) {
				String dumpReq = getArgValue(args, i++);
				this.dumpRequested = Boolean.parseBoolean(dumpReq);
				
			} else if (argName.equals("-reportFailureLimit")) {
				this.reportFailureLimit = Integer.parseInt(getArgValue(args, i++));
				
			} else if (argName.equals("-abortAtFailureLimit")) {
				this.abortAtFailureLimit = Integer.parseInt(getArgValue(args, i++));
				
			} else if (argName.equals("-maxTotalLogFileSpace")) {
				maxTotalLogFileSpaceStr = getArgValue(args, i++);
				
			} else if (argName.equals("-maxSingleLogSize")) {
				maxSingleLogSizeStr = getArgValue(args, i++);
				
			} else if (argName.startsWith("-suite.")) {
				// Grab suite argument, and chop it up. eg '-suite.mauve.threadCount 3'
				String suiteArgValue = getArgValue(args, i++);
				SuiteArg suiteArg = parseSuiteArg(argName, suiteArgValue);
				
				// Validate that this is not a repeated argument. 
				// Guards against people how copy+paste suite config, but don't change the suite name
				if (previousArgs.contains(argName)) {
					if (!argName.endsWith(".inventoryExcludeFile")) {  // There can be multiple exclude files
					    throw new StfException("Duplicate LoadTest argument: '" + argName + "'");
					}
				}
				previousArgs.add(argName);

				// Check to see if this is a new suite
				if (!suiteArg.suiteName.equals(currSuiteName)) {
					currSuiteNum++;
					currSuiteName = suiteArg.suiteName;
					suiteNames[currSuiteNum] = currSuiteName;
					if (currSuiteNum >= MAX_SUITE_NUMBER) {
						throw new StfException("Too many suites. Max supported number: " + MAX_SUITE_NUMBER);
					}
				}

				// Special case handling for exclude files.
				// There can be multiple files, so the single arg value into a comma separated list of files
				if (suiteArg.argName.equals("inventoryExcludeFile") && suiteData[currSuiteNum].containsKey(suiteArg.argName)) {
					// There is already a value for the inventory file. Combine into a file list
					String existingExcludeFiles = suiteData[currSuiteNum].get(suiteArg.argName);
					String fullExcludeList = existingExcludeFiles + ", " + suiteArg.argValue;
					suiteData[currSuiteNum].put(suiteArg.argName, fullExcludeList);
				} else {
					suiteData[currSuiteNum].put(suiteArg.argName, suiteArg.argValue);
				}

				// Exit if this is not a valid suite field name
				if (!SUITE_ARGUMENTS.contains(suiteArg.argName)) {
					usage("Unknown suite argument type: " + argName + " " + suiteArg.argValue);
				}
			} else {
				usage("Unknown argument: " + argName);
			}
		}
		

		// Validate the result directory
		if (resultsDir == null) { 
			usage("Results directory must be supplied");
		}
		if (!(resultsDir.isDirectory() && resultsDir.exists())) {
			usage("Results directory is not an existing directory: " + resultsDir.getAbsolutePath());
		}
		
		// Make sure that it's possible to see if this is a time limited test run
		logger.info("Load test parameters");
		logger.info("  Time limited         = " + this.timeLimitedTest);
		if (timeLimitedTest) {
			logger.info("  Time limit         = " + this.timeLimitString);
		}
		
		// Report other load test arguments
		logger.info("  abortIfOutOfMemory   = " + abortIfOutOfMemory);
		logger.info("  reportFailureLimit   = " + reportFailureLimit);
		logger.info("  abortAtFailureLimit  = " + abortAtFailureLimit);

		// Find out log file sizes
		if (maxTotalLogFileSpaceStr == null) {
			usage("Value must be supplied for -maxTotalLogFileSpace");
		}
		if (maxSingleLogSizeStr == null) {
			usage("Value must be supplied for -maxSingleLogSize");
		}
		// Process argument for the total storage limits of log files
		try {
			this.maxTotalLogFileSpace = parseSizeWithUnits(maxTotalLogFileSpaceStr);
		} catch (StfException e) {
			usage("Failed to parse maxTotalLogFileSpace. Value: " + maxTotalLogFileSpaceStr);
			throw e;
		}
		// Process arg for max size of each log file 
		if (maxSingleLogSizeStr.startsWith("1/")) {
			String fractionalPart = maxSingleLogSizeStr.substring(maxSingleLogSizeStr.indexOf("/")+1);
			this.maxSingleLogSize = (int) (maxTotalLogFileSpace / Integer.parseInt(fractionalPart));
		} else {
			try {
				this.maxSingleLogSize = (int) parseSizeWithUnits(maxSingleLogSizeStr);
			} catch (StfException e) {
				usage("Failed to parse maxSingleLogSize. Value: " + maxSingleLogSizeStr);
				throw e;
			}
		}
		// Validation
		if (maxSingleLogSize > maxTotalLogFileSpace) {
			usage("Size of single log file cannot be greater that the size limit for all log files");
		}
		// Show conclusion. For debugging
		logger.info("  maxTotalLogFileSpace = " + maxTotalLogFileSpace);
		logger.info("  maxSingleLogSize     = " + maxSingleLogSize);
		
		// Create suite objects, which will trigger the parsing of the inventory xml files
		for (i=0; i<MAX_SUITE_NUMBER; i++) { 
			HashMap<String, String> suiteArguments = suiteData[i];
			if (!suiteArguments.isEmpty()) {
				SuiteData suite = createSuite(resultsDir, i, suiteNames[i], suiteArguments);
				suites.add(suite);
			}
		}
	}


	// Utility method to help process command line arguments.
	// Returns the argument at 'argNum', after checking that it exists.  
	private static String getArgValue(String[] args, int argNum) {
		if (argNum >= args.length) {
			String argName = args[argNum-1];
			throw new IllegalStateException("No argument supplied for: " + argName);
		}
		
		return args[argNum].trim();
	}

	
	// Parse a value such as '128k'
	private long parseSizeWithUnits(String valueStr) throws StfException {
		final Pattern pattern = Pattern.compile("^([0-9]+)([gGmMkK]?)$");
	    Matcher matcher = pattern.matcher(valueStr);
	    if (!matcher.find()) {
	        throw new StfException("Invalid size specification: '" + valueStr + "'. Must be in the form '<size>[g|G|m|M|k|K]'");
	    }
	    
	    long value = Long.parseLong(matcher.group(1));
	    
	    String unit = matcher.group(2).toUpperCase();
	    if (unit.equals("G")) {
	    	value = value * 1024 * 1024 *1024;
	    } else if (unit.equals("M")) {
		    value = value * 1024 * 1024;
	    } else if (unit.equals("K")) {
	    	value = value * 1024;
	    }
	    
		return value;
	}


	// Parse out the parts of a single suite argument.
	// eg parses "-suite.mauve.threadCount 2"
	// returns SuiteArg holding the suite number(3), argument name (threadCount) and argument value (2)
	private SuiteArg parseSuiteArg(String argName, String argValue) {
		// Extract the name of the suite
		String suitePrefix = "-suite.";
		int idEnd = argName.indexOf(".", suitePrefix.length());
		String suiteName = argName.substring(suitePrefix.length(), idEnd);
		
		SuiteArg suiteArg = new SuiteArg();
		suiteArg.suiteName = suiteName;		
		suiteArg.argName = argName.substring(idEnd+1);
		suiteArg.argValue = argValue;
		
		return suiteArg;
	}


	/**
	 * Create a SuiteData object to hold all the known information about a single suite.
	 * 
	 * @param resultsDir is the STF results directory.
	 * @param suiteNum is the number of the suite.
	 * @param suiteName is the name the test has given the suite.
	 * @param suiteArgs is a Hashmap of name/value pairs of the suite arguments.
	 * @return a new SuiteData object.
	 * @throws Exception if there is a missing suite arguments or if the inventory file cannot be parsed. 
	 */
	private SuiteData createSuite(File resultsDir, int suiteNum, String suiteName, HashMap<String, String> suiteArgs) throws Exception {
		int numThreads      = (int) readLongArg(suiteNum, suiteArgs, "threadCount", null);
		long suppliedSeed    = readLongArg(suiteNum, suiteArgs, "seed", null);
		String inventoryFile = readArg(suiteNum, suiteArgs, "inventoryFile", null);
		String inventoryExcludeFiles = readArg(suiteNum, suiteArgs, "inventoryExcludeFile", null);
		long numberTests     = readLongArg(suiteNum, suiteArgs, "totalNumberTests", "-1");
		int repeatCount      = (int) readLongArg(suiteNum, suiteArgs, "repeatCount", "1");
		String selectionModeString = readArg(suiteNum, suiteArgs, "selection", null);
		String thinkingTimeString = readArg(suiteNum, suiteArgs, "thinkingTime", "0ms..0ms");
		
		logger.info("Parameters for suite " + suiteNum);
		logger.info("  Suite name     = " + suiteName);
		logger.info("  Number threads = " + numThreads);
		logger.info("  Supplied seed  = " + suppliedSeed);
		logger.info("  Inventory file = " + inventoryFile);
		logger.info("  Exclude file   = " + inventoryExcludeFiles);
		logger.info("  Number tests   = " + numberTests);
		logger.info("  Repeat count   = " + repeatCount);
		logger.info("  Thinking time  = " + thinkingTimeString);
		logger.info("  Selection mode = " + selectionModeString);
		
		// Work out how tests are going to be selected
		SelectionMode selection;
		if (selectionModeString.equals("sequential")) {
			selection = SelectionMode.SELECTION_SEQUENTIAL;
		} else if (selectionModeString.equals("random")) {
			selection = SelectionMode.SELECTION_RANDOM;
		} else {
			throw new IllegalStateException("Section mode must be either 'sequential' or 'random'. Not: " + selectionModeString);
		}
		
		// For random test selection set the seed. Either reuse previous seed, or pick a new one.
		long seed = -1;
		if (selection == SelectionMode.SELECTION_RANDOM) {
			if (suppliedSeed == -1) {
				// No seed supplied. Generate a new one.
				seed = System.currentTimeMillis() + ((suiteNum+numThreads) * 277) + repeatCount*3 + inventoryFile.hashCode();
			} else {
				// Don't use a new seed. Use one supplied as suite argument.
				seed = suppliedSeed;
			}
			logger.info("  Actual seed    = " + seed);
		}
		
		// Parse the thinking time string into min and max values
		String[] thinkTimeValues = thinkingTimeString.split("\\.\\.");
		if (thinkTimeValues.length != 2) {
			usage("Thinking time not in expected 'min..max' format: " + thinkingTimeString);
		}
		long minThinkingTime = TimeParser.parseTimeSpecification(thinkTimeValues[0]).getMilliseconds();
		long maxThinkingTime = TimeParser.parseTimeSpecification(thinkTimeValues[1]).getMilliseconds();

		// Make sure that the run is either time or test count limited
		if (!timeLimitedTest && numberTests == -1) {
			usage("Load test must be limited by either time or by the number of tests.");
		}
		
		// Work out top level directory holding the copied inventory files
		DirectoryRef resultsRoot = DirectoryRef.createResultsDirectoryRef(resultsDir);
		DirectoryRef inventoryRoot = calculateInventoryRoot(inventoryFile, resultsRoot);
		
		// Work out where the inventory lives below inventoryRoot
		String inventoryFileOffset = inventoryRoot.getSubpathOf(inventoryFile);
		
		// There will be zero or more test exclude files. Build up an array list of them
		ArrayList<String> excludeFiles = new ArrayList<String>();
		for (String excludeName : inventoryExcludeFiles.split(",")) {
			excludeName = excludeName.trim();
			if (!excludeName.equals("none")) {
				String excludeRef = inventoryRoot.getSubpathOf(excludeName);
				excludeFiles.add(excludeRef);
			}
		}
		
		// Read the lists of tests from the inventory file.
		// inventoryRoot is passed twice because InventoryData might receive two roots under which to look for files
		// (workspace root and test root).
		ArrayList<DirectoryRef> inventoryRootArray = new ArrayList<DirectoryRef>();
		inventoryRootArray.add(inventoryRoot);
		InventoryData inventory = new InventoryData(inventoryRootArray, inventoryFileOffset, excludeFiles, false, true, dumpRequested);

		return new SuiteData(suiteNum, numThreads, seed, inventory, numberTests, repeatCount, minThinkingTime, maxThinkingTime, selection);
	}

	
	// Calculates the directory which holds the load test inventories
	// For example,
	// inventoryFile = /stf/20161213-093256-SharedClassesWorkload/results/12.SCL.inventory/test.load/config/inventories/mauve/mauve_all.xml
	// resultsRoot   = /stf/20161213-093256-SharedClassesWorkload/results
	// means that:
	// inventoryDir  = 12.SCL.inventory
	// inventoryRoot = /stf/20161213-093256-SharedClassesWorkload/results/12.SCL.inventory
	DirectoryRef calculateInventoryRoot(String inventoryFile, DirectoryRef resultsRoot) throws StfException {
		// Remove results root prefix from the inventory file spec
		String inventory2ndHalf = resultsRoot.getSubpathOf(inventoryFile); 

		// Create a reference to the top level directory holding inventory files
		String inventoryDirName = inventory2ndHalf.substring(0, inventory2ndHalf.indexOf("/"));
		DirectoryRef inventoryRoot = resultsRoot.childDirectory(inventoryDirName);

		return inventoryRoot;
	}

	
	/**
	 * Returns a suite value from the map, or the supplied default.
	 * @param suiteId is the number of the suite being processed.
	 * @param suiteMap is a map containing name/value pairs with the suites arguments.
	 * @param argName is the name of the argument to get the value for.
	 * @param defaultValue is a default value, or null if it's a mandatory argument.
	 * @return a String with the value of the named argument. 
	 */
	private static String readArg(int suiteId, HashMap<String, String> suiteMap, String argName, String defaultValue) {
		if (!suiteMap.containsKey(argName)) {
			if (defaultValue != null) { 
				return defaultValue;
			}
			throw new IllegalStateException("Suite data for suite " + suiteId + " does not have a value for the '" + argName + "' field");
		}

		return suiteMap.get(argName);
	}

	
	/**
	 * This variant of readArg() makes sure that the returned value holds a long number.
	 */
	private static long readLongArg(int suiteId, HashMap<String, String> suiteMap, String argName, String defaultValue) {
		String longValue = readArg(suiteId, suiteMap, argName, defaultValue);
		try { 
			return Long.parseLong(longValue);
		} catch (NumberFormatException e) {
			String fullArgName = "suite." + suiteId + "." + argName;
			throw new IllegalStateException("Failed to parse number for '" + fullArgName + "' with value: " + longValue, e); 
		}
	}

	
	private void usage(String errorMessage) {
		logger.info("");
		logger.error(errorMessage);
		
		logger.info("");
		logger.info("Usage: LoadTest -resultsDir <directory> -timeLimit <time-specification> -abortIfOutOfMemory <boolean> -suite.x.name <suite-value> ...");
		logger.info("Where:");
		describeArgument("-resultsDir <directory>", "Mandatory. Is an existing directory to which the results will be written.");
		describeArgument("-resultsPrefix <string>", "Optional string which is used as a prefix to all files written to the results directory.");
		describeArgument("-timeLimit <time-specification>", "Optional. Is the time after which no new tests will be started. Supports units of Hours, Minutes and Seconds. eg '1h15m'.");
		describeArgument("-abortIfOutOfMemory <boolean>", "Optional. Set to 'false' to prevent the LoadTest aborting if out of memory. Default is 'true' (exits on OOM).");
		describeArgument("-reportFailureLimit <int>", "Optional. This is the number of test failures which will be reported in detail (with name of failing test, stack trace, etc). Set '-1' to disable for reporting of all failing tests.");
		describeArgument("-abortAtFailureLimit <int>", "Optional. Load test will abort when this many tests have failed. Set to '-1' to disable so that a run never aborts after a failure.");
		describeArgument("-maxTotalLogFileSpace <size>[g|G|m|M|k|K]", "Mandatory. Limits the disk space used for logging test execution.");
		describeArgument("-maxSingleLogSize <size>[g|G|m|M|k|K] | 1/<number>", "Mandatory. Maximum size for an individual log file. Or calculated as a fraction of maxTotalLogFileSpace (eg '1/20')");
		describeArgument("-suite.x.threadCount <number>", "Mandatory. Is the number of threads to be used for running the tests of this suite."); 
		describeArgument("-suite.x.seed <starting-seed>", "Mandatory. Sets the starting value for the random number generator used to select the next test. A value of '-1' will use a new seed. Use a pervious value to duplicate a run.");
		describeArgument("-suite.x.inventoryFile <file>", "Mandatory. Specifies the file which lists the test that can be run for the suite."); 
		describeArgument("-suite.x.totalNumberTests <number>", "Optional. This is the number of tests which will run before completing this suite. If not specified the load test wil run until the time limit."); 
		describeArgument("-suite.x.repeatCount <number>", "Optional. This is the number of times that a test will executed before a different test is selected. Defaults to '1'."); 
		describeArgument("-suite.x.thinkingTime min..max", "Optional. Random delay in load test thread between executing sucessive tests. Defaults to '0ms..0ms'."); 
		describeArgument("-suite.x.selection ( random | sequential)", "Mandatory. Controls how the next test will be selected. When running sequentially it loops back to the start of the inventory after executing the last test in the list."); 
			
		logger.error(errorMessage);
		
		System.exit(2);
	}
	
	
	private void describeArgument(String argSpec, String argDescription) {
		logger.info("  " + argSpec); 
		
		// Output long description texts over multiple info lines 
		String[] words = argDescription.split(" ");
		StringBuilder line = new StringBuilder();
		for (String word : words) {
			if (line.length() + word.length() > 55) {
				logger.info("     " + line.toString());
				line.setLength(0);
			}
			line.append(" ");
			line.append(word);
		}
		// Output the last few words
		logger.info("     " + line.toString());
		logger.info("");
	}
}