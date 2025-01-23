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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface;
import net.adoptopenjdk.loadTest.adaptors.LoadTestBase;
import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface.ResultStatus;
import net.adoptopenjdk.loadTest.reporting.ExecutionLog;
import net.adoptopenjdk.loadTest.reporting.ExecutionTracker;
import net.adoptopenjdk.loadTest.reporting.OutputFilter;

/**
 * This class holds the core of the load test.
 * It starts threads which run tests until the test limits are reached.
 */
class LoadTestRunner {
    private static final Logger logger = LogManager.getLogger(LoadTestRunner.class.getName());
    
	private static final String REPORTING_FREQUENCY_ENV_NAME = "LT_REPORTING_FREQUENCY";	
	private static final long HUNG_MESSAGE_REPEAT_TIME = 5 * 1000; 
	
	private final File resultsDir;
	private final String resultsPrefix;
	private final boolean timeLimitedTest;
	private final long testEndTime;
	private final long testInactivityLimit; 
	private final boolean abortIfOutOfMemory;
	private final int reportFailureLimit;
	private final int abortAtFailureLimit;
	private final long maxTotalLogFileSpace;
	private final int maxSingleLogSize;
	private final ArrayList<SuiteData> suites;
	private final long reportingFrequency;
	private boolean dumpRequested; 

	
	LoadTestRunner(File resultsDir, String resultsPrefix, boolean timeLimitedTest, long testEndTime, 
			    long testInactivityLimit,
				boolean abortIfOutOfMemory,
				int reportFailureLimit, int abortAtFailureLimit,
				long maxTotalLogFileSpace, int maxSingleLogSize,
				ArrayList<SuiteData> suites,
				boolean dumpRequested) {
		this.resultsDir = resultsDir;
		this.resultsPrefix = resultsPrefix;
		this.timeLimitedTest = timeLimitedTest;
		this.testEndTime = testEndTime;
		this.testInactivityLimit = testInactivityLimit; 
		this.abortIfOutOfMemory = abortIfOutOfMemory;
		this.reportFailureLimit = reportFailureLimit;
		this.abortAtFailureLimit = abortAtFailureLimit;
		this.maxTotalLogFileSpace = maxTotalLogFileSpace;
		this.maxSingleLogSize = maxSingleLogSize;
		this.suites = suites;
		this.dumpRequested = dumpRequested; 
		
		// Decide how frequency progress reports are to be made
		String reportingFrequencyString = System.getenv(REPORTING_FREQUENCY_ENV_NAME);
		if (reportingFrequencyString != null) {
			this.reportingFrequency = Long.parseLong(reportingFrequencyString);			
		} else {
			this.reportingFrequency = 20 * 1000;
		}
	}

	/**
	 * Runs the tests.
	 * @return long containing a count of the number of failing tests .
	 * @throws Exception if there was an IO exception writing to a log file.
	 */
	long run() throws Exception {
		// Report and fail if there are any uncaught exceptions
		final AtomicLong numberUncaughtExceptions = new AtomicLong(0);
		 Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable throwable) {
				numberUncaughtExceptions.incrementAndGet();
				logger.error("Uncaught exception: ", throwable);
			}
		});
		 
		// For periodic progress reporting
		long testStartTime = System.currentTimeMillis();
		long numProgressUpdates = 0;
		long firstReportTime = System.currentTimeMillis() + reportingFrequency;
		long nextReportTime = firstReportTime;
		long previousNumStartedTests = 0;
		
		// Track the number of passing/failing tests.
		// This is shared between all worker threads so using atomic counter.
		final AtomicLong numberStartedTests = new AtomicLong(0);
		final AtomicLong[] passingTestCounters = new AtomicLong[suites.size()];
		final AtomicLong[] failingTestCounters = new AtomicLong[suites.size()];
		for (int i=0; i<suites.size(); i++) {
			passingTestCounters[i] = new AtomicLong(0);
			failingTestCounters[i] = new AtomicLong(0);
		}
		
		// Flag for forcing early thread completion
		final AtomicBoolean testRunAborted = new AtomicBoolean(false);

		// Redirect stdout/stderr. This is done so that:
		//  1) Minimises the output from a run.
		//  2) Allows the OutputFilter class to match the stdout/err output to the test which produced it.
		final PrintStream originalSystemOut = System.out;
		final PrintStream originalSystemErr = System.err;
		System.setOut(new OutputFilter(System.out, false)); 
        System.setErr(new OutputFilter(System.err, false));
		
        // Create a file to hold binary data on test starts and results.
		File executionLogFile = new File(resultsDir, resultsPrefix + "executionlog"); 
		ExecutionLog.createInstance(executionLogFile, maxTotalLogFileSpace, maxSingleLogSize, suites);
		
		ExecutorService es = Executors.newCachedThreadPool();
		
		int nextThreadNum = 0;
		for (int i=0; i<suites.size(); i++) {
			final SuiteData suite = suites.get(i);
			for (int t=0; t<suite.getNumberThreads(); t++) {
				final int threadNum = nextThreadNum++;
				final AtomicLong numberPassingTests = passingTestCounters[suite.getSuiteId()];
				final AtomicLong numberFailingTests = failingTestCounters[suite.getSuiteId()];
				
				logger.info("Starting thread. Suite=" + suite.getSuiteId() + " thread=" + threadNum);
				
				// Start thread to run tests
				es.execute(new Runnable() {
					public void run() {
						String threadName = "load-" + threadNum;
						Thread.currentThread().setName(threadName);
						
						// Variables to support inter test thinking time.
						// Note the random seed value. This allows inter test spacing to be reproducible from run to run.
						long thinkingTimeMin = suite.getMinThinkingTime();
						int thinkingTimeRange = (int) (suite.getMaxThinkingTime() - thinkingTimeMin);
						boolean doThinkingTimeSleep = thinkingTimeMin > 0  ||  thinkingTimeRange > 0; 
						Random thinkingTimeRnd = new Random(threadName.hashCode() + thinkingTimeMin + thinkingTimeRange);
						
						// Create tracker object to record which test is running, output and result
						ExecutionTracker.createNewTracker();
						ExecutionTracker executionTracker = ExecutionTracker.instance();
						
						// Run tests until the inventory says there are none left, or the time limit is reached
						AdaptorInterface test;
						while ((test = suite.getNextTest()) != null) {
							try {
								// Keep note on which test this thread is running
								executionTracker.recordTestStart(test, suite.getSuiteId(), threadNum);

								// Run the actual test
								numberStartedTests.incrementAndGet();
								ResultStatus testResult = null;
								try {
									testResult = test.executeTest();
								} catch(BlockedExitException exitException) {
									// The test has attempted to call System.exit(). Keep running.
									if (exitException.getExitValue() == 0) {
										testResult = ResultStatus.BLOCKED_EXIT_PASS;
									} else {
										testResult = ResultStatus.BLOCKED_EXIT_FAIL;
									}
								} catch (InvocationTargetException e) {
									// Some other exception. Rethrow to log as test failure.
									throw e;
								}

								// Test completed. Record pass/fail result to file
								boolean testPassed = executionTracker.recordTestCompletion(testResult);
								
								// Keep in-memory count of pass/fail result
								if (testPassed) {
									numberPassingTests.incrementAndGet();
								} else {
									// Produce java dumps for only the first test failure if flag for creating dump is set by user
									long failureNum = numberFailingTests.incrementAndGet();
									
									logger.info("suite.getInventory().getInventoryFileRef(): " + suite.getInventory().getInventoryFileRef());
									logger.info("suite.isCreateDump() : " + dumpRequested);
									
									FirstFailureDumper.instance().createDumpIfFirstFailure((LoadTestBase) test, dumpRequested);
									
									// Get log4j to report the test failure
									reportFailure(failureNum, executionTracker.getCapturedOutput(), test, suite, threadNum);
								}
								
								// Inter test thinking time
								if (doThinkingTimeSleep) {
									try {
										long thinkingTime = thinkingTimeMin;
										if (thinkingTimeRange > 0) {
											thinkingTime += thinkingTimeRnd.nextInt(thinkingTimeRange);
										}
										Thread.sleep(thinkingTime);
									} catch (InterruptedException e) {
										// Ignore
									}
								}
							} catch (Throwable t) {
								// Record test failure to binary execution log file
								try {
									executionTracker.recordTestFailure(t);
								} catch (IOException ioException) {
									// Not sure what else can be done at this point.
									// We have caught a failure but then had another failure when logging this. 
									logger.error("Internal Error: Failed to record test failure", ioException);
								}

								// Produce java dumps if this is the first test error
								long failureNum = numberFailingTests.incrementAndGet();
								
								FirstFailureDumper.instance().createDumpIfFirstFailure((LoadTestBase) test, dumpRequested);
								
								// Report exception to process output
								reportFailure(failureNum, executionTracker.getCapturedOutput(), test, suite, threadNum);
								
								// Out of memory exceptions are regarded as fatal for the JVM
								if (t instanceof OutOfMemoryError && abortIfOutOfMemory) {
									// Force completion of the test, by getting all threads to exit
									logger.error("Out of memory exception. Aborting test run", t);
									testRunAborted.set(true);
								}
							}
							
							// Exit thread if it's a time restricted run and we have hit the time limit
							if (timeLimitedTest && System.currentTimeMillis() > testEndTime) {
								break;
							}
							// Check to see if the test has decided to abort or not
							if (testRunAborted.get()) {
								break;
							}
						} // end while

						logger.info("Thread completed. Suite=" + suite.getSuiteId() + " thread=" + threadNum);
					} // end run()


					private void reportFailure(long failureNum, ByteArrayOutputStream capturedOutput,
							AdaptorInterface test, final SuiteData suite, final int threadNum) {
						if (reportFailureLimit == -1 || failureNum <= reportFailureLimit) {
							logger.error("Test failed"
								+ "\n  Failure num.  = " + failureNum
								+ "\n  Test number   = " + test.getTestNum() 
								+ "\n  Test details  = '" + test.toString() + "'"
								+ "\n  Suite number  = " + suite.getSuiteId()
								+ "\n  Thread number = " + threadNum 
								+ "\n>>> Captured test output >>>\n"
								+ capturedOutput.toString().trim()
								+ "\n<<<\n");
						} else {
							logger.error("Test failed. Details recorded in execution log.");
						}
						
						if (abortAtFailureLimit != -1 && failureNum == abortAtFailureLimit) {
							logger.info("Number of test failures has reached 'AbortAtFailureLimit' (" + failureNum + "). Terminating load test");
							testRunAborted.set(true);
						}
					}
                }); // end execute
			}
		}


		// Await completion of the threads running the tests 
		es.shutdown();
		
		// Data used to detect a 'hung' load test
		long numTestsExecuted = 0;
		long activityDeadline = System.currentTimeMillis() + this.testInactivityLimit;
		
		// Wait for all worker threads to complete
		while (!es.isTerminated()) {
			// Periodically report progress, so that people know that we are still running tests
			if (System.currentTimeMillis() >= nextReportTime) {
				numProgressUpdates++;
				long numberStarted = numberStartedTests.get();
				long numberFailing = sumAll(failingTestCounters);
				String deltaText = previousNumStartedTests > 0 ? " (+" + (numberStarted - previousNumStartedTests) + ")" : "";
				String failureSummaryText = numberFailing > 0 ? " (with " + numberFailing + " failure(s))" : "";
				logger.info("Completed " + String.format("%.1f%%. ", calculatePercentageDone(testStartTime)) 
						+ "Number of tests started=" + numberStarted 
						+ deltaText
						+ failureSummaryText);
				
				previousNumStartedTests = numberStarted;
				nextReportTime = firstReportTime + (numProgressUpdates * reportingFrequency);
			}
			
			// Check to see if load test execution has hung. A process is regarded as
			// being hung if there is no test progress for the number of milliseconds 
			// in INACTIVITY_LIMIT. 
			// When it's decided that a process has hung then a message in written to
			// stderr, which will result in the process monitoring code killing it.
			long newNumTestsExecuted = sumAll(passingTestCounters) + sumAll(failingTestCounters);
			if (newNumTestsExecuted > numTestsExecuted) {
				// At least 1 test has completed since last time. Reset the deadline.
				numTestsExecuted = newNumTestsExecuted;
				activityDeadline = System.currentTimeMillis() + this.testInactivityLimit;
			} else if (System.currentTimeMillis() > activityDeadline) {
			    logger.error("**POSSIBLE HANG DETECTED**");
			    activityDeadline = System.currentTimeMillis() + HUNG_MESSAGE_REPEAT_TIME;
			}
			
			Thread.sleep(100);
		}
		
		// Stop the stdout/stderr interception. Reset back to original streams 
		ExecutionLog.instance().close();
		System.setOut(originalSystemOut);
		System.setErr(originalSystemErr);
		
		// Make sure time limited runs cannot be mistaken for a crash/bug
		if (timeLimitedTest && System.currentTimeMillis() > testEndTime) {
			logger.info("Test stopped due to reaching runtime limit");
		}

		// Summarise success of the run
		long passedCount   = sumAll(passingTestCounters);
		long failedCount   = sumAll(failingTestCounters);
		long uncaughtCount = numberUncaughtExceptions.get();
		logger.info("Load test completed");
        logger.info("  Ran     : " + numberStartedTests.get());
        logger.info("  Passed  : " + passedCount);
		logger.info("  Failed  : " + failedCount);
		if (uncaughtCount > 0) {
			logger.info("  Uncaught: " + uncaughtCount);	
		}
        logger.info("  Result  : " + (failedCount+uncaughtCount == 0 ? "PASSED" : "FAILED"));
		
		if (failedCount > reportFailureLimit) {
			logger.info("Note that only the first " + reportFailureLimit + " failures have been reported");
		}
		
		return failedCount + uncaughtCount; 
	}


	private double calculatePercentageDone(long testStartTime) {
		// Work out the percentage completed for the slowest suite
		double slowestSuitePercent = 100.0;
		boolean haveFoundSlowestSuite = false;
		for (int s=0; s<suites.size(); s++) {
			double suitePercentageDone = suites.get(s).getPercentageDone();
			if (suitePercentageDone >= 0.0) {
				slowestSuitePercent = Math.min(slowestSuitePercent, suitePercentageDone);
				haveFoundSlowestSuite = true;
			} else {
				// At least one suite is not workload bound. Abandon search for slowest.
				haveFoundSlowestSuite = false;
				break;
			}
		}

		// Work out percentage completed if load test run time set		
		double timePercentDone = 0.0;
		boolean haveTimePercent = false;
		if (timeLimitedTest) {
			double elapsedTime = System.currentTimeMillis() - testStartTime;
			double totalRunTime = testEndTime - testStartTime;
			timePercentDone = (elapsedTime / totalRunTime) * 100.0;
			haveTimePercent = true;
		}

		// Decide overall percentage completed 
		double overallPercentDone;
		if (haveFoundSlowestSuite && !haveTimePercent) {
			overallPercentDone = slowestSuitePercent;
		} else if (!haveFoundSlowestSuite && haveTimePercent) {
			overallPercentDone = timePercentDone;
		} else {
			// This run has at least 1 suite running a fixed load _and_ an overall runtime limit
			overallPercentDone = Math.max(slowestSuitePercent, timePercentDone);
		}

		// Completion percentage can be 100% because tests are allowed to run
		// until completion (and therefore pushing them over the time limit) 
		overallPercentDone = Math.min(overallPercentDone, 100.0);
		return overallPercentDone;
	}

	private long sumAll(AtomicLong[] atomics) {
		long total = 0;
		
		for (int i=0; i<atomics.length; i++) {
			total += atomics[i].get();
		}
		
		return total;
	}
}