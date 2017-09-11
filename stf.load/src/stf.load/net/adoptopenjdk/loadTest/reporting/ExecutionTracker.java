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

package net.adoptopenjdk.loadTest.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface;
import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface.ResultStatus;
import net.adoptopenjdk.loadTest.reporting.ExecutionRecord.Action;


/**
 * Utility class which records key information about the test running on the 
 * current thread.
 * 
 * This is a critical class for the load test application. It uses an 
 * InheritableThreadLocal so that the information about the running test is 
 * visible from all points within the test execution (eg, intercepting the
 * stdout/stderr output).
 * 
 * It uses InheritableThreadLocal so that the same tracking object is visible
 * to any child threads that the test creates. 
 * If the child thread detects a failure it updates the same tracking
 * object, so the failure is visible to LoadTestRunner when the test completes. 
 */
public class ExecutionTracker {
	// Information about the test which the thread is running
	private int threadNum = -1;
	private int suiteNum = -1;
    private AdaptorInterface test = null;
    
    // Captures stdout/stderr output from the running test. Held in a stream as
    // it accepts bytes and most output is never needed (most tests pass)
    private ByteArrayOutputStream outputCapture;
    
	private ResultStatus testResult;  // passed or failed

	// Table to convert test pass/fail status to an action enum
	private static Action resultToActionMapping[] = new Action[5];
	static {
		resultToActionMapping[ResultStatus.UNKNOWN.ordinal()]           = Action.TEST_RESULT_UNKNOWN;          
		resultToActionMapping[ResultStatus.PASS.ordinal()]              = Action.PASSED;             
		resultToActionMapping[ResultStatus.BLOCKED_EXIT_PASS.ordinal()] = Action.BLOCKED_EXIT_PASS;
		resultToActionMapping[ResultStatus.FAIL.ordinal()]              = Action.FAILED;             
		resultToActionMapping[ResultStatus.BLOCKED_EXIT_FAIL.ordinal()] = Action.BLOCKED_EXIT_FAIL;  
	};

	
	private static final InheritableThreadLocal<ExecutionTracker> tracker = new InheritableThreadLocal<ExecutionTracker>() {
		public ExecutionTracker initialValue() {
			return new ExecutionTracker();
		}
	};	

	// Private to force access through the instance method.
	private ExecutionTracker() {
		this.outputCapture = new ByteArrayOutputStream(100000);
	}
	
	
	/**
	 * @return the ExecutionTracker for the current thread.
	 */
	public static ExecutionTracker instance() {
		return tracker.get();
	}
	
	/**
	 * Forces the creation of a new tracker object.
	 * Needed when we want a child thread to have its own object.
	 */
	public static void createNewTracker() {
		tracker.set(new ExecutionTracker());
	}
	
	
	public synchronized void checkTestOutput(byte[] buff, int off, int len) {
		// Store this piece of output, incase the test fails and it's needed for debugging
		outputCapture.write(buff, off, len);
		
		// Get the adaptor to decide if this output indicates a pass or a failure
		ResultStatus outputScanResult = test.checkTestOutput(buff, off, len);
		
		// Update result state
		testResult = determineNewStatus(testResult, outputScanResult);
	}

	
	// Work out what the actual test result is, by comparing the existing result with 
	// a new observation. 
	// This logic means that if a fail value is ever seen then the test is ultimately
	// recorded as a failure.
	// For example the sequence 'Pass, Pass, Fail, Pass' would result in a final verdict of 'Fail'.
	private ResultStatus determineNewStatus(ResultStatus existingResult, ResultStatus newResult) {
		if (newResult.ordinal() > existingResult.ordinal()) {
			// New observation takes precedence over existing result
			return newResult;
		} else {
			return existingResult;
		}
	}


	public synchronized boolean isRunningTest() { 
		return test != null;
	}
	
	
	public synchronized ByteArrayOutputStream getCapturedOutput() {
		return outputCapture;
	}

	public ResultStatus getFinalResult() {
		return testResult;
	}
	
	public synchronized void recordTestStart(AdaptorInterface test, int suiteNum, int threadNum) throws IOException {
		this.testResult = ResultStatus.UNKNOWN;
		this.suiteNum = suiteNum;
		this.threadNum = threadNum;
		this.test = test;
		this.outputCapture.reset();
		
		storeTestActivity(Action.STARTED);
	}
	
	public synchronized boolean recordTestCompletion(ResultStatus overallResult) throws IOException {
		testResult = determineNewStatus(testResult, overallResult);
		
		// Do a lookup to find the Action code that should be stored for this result.
		// This code is on the critical path so lookup much preferred over if statements.
		Action completionAction = resultToActionMapping[testResult.ordinal()];
		
		storeTestActivity(completionAction);
		
		return testResult.testPassed();
	}
	
	public synchronized void recordTestFailure() throws IOException {
		storeTestActivity(Action.FAILED);
	}

	public synchronized void recordTestFailure(Throwable t) throws IOException {
		System.err.println("Test failed:");
		t.printStackTrace();
		storeTestActivity(Action.FAILED_THROWABLE);
	}


	// Writes information about what has just happened to a binary log file.
	private void storeTestActivity(Action action) throws IOException {
		long timestamp = System.currentTimeMillis();
		ExecutionRecord startRecord = new ExecutionRecord(timestamp, action, threadNum, suiteNum, test.getTestNum(), "", outputCapture.toByteArray());
		ExecutionLog.instance().log(startRecord);
	}
}