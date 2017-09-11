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

package net.adoptopenjdk.loadTest.adaptors;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import net.adoptopenjdk.loadTest.FirstFailureDumper;
import net.adoptopenjdk.stf.environment.PlatformFinder;


/**
 * This class allows the load test to run junit tests.
 */
public class JUnitAdaptor extends LoadTestBase {
	private final String testClass;


	public JUnitAdaptor(int testNum, String testClass, BigDecimal weighting) {
		super(testNum, testClass, null, weighting);
		
		this.testClass = testClass;
	}


	/**
	 * Gets JUnit to run the tests, and examines the results from the execution.
	 * @returns A ResultStatus object with the pass/fail result. 
	 */
	@Override
	public ResultStatus executeTest() throws Throwable {
		final LoadTestBase test = this;
    	// Find the test classes that need to be run
    	ArrayList<Class<?>> testClasses = new ArrayList<Class<?>>();
   		testClasses.add(Class.forName(testClass));

    	// The results from this run are collected into this string buffer.
    	// Using string buffer for thread safety, as the anonymous inner class may be 
    	// run in a different thread.
    	final StringBuffer resultsText = new StringBuffer();
    	final ArrayList<Throwable> failures = new ArrayList<Throwable>();
    	final String platform = PlatformFinder.getPlatformAsString();
    	
    	JUnitCore jUnitCore = new JUnitCore();
        jUnitCore.addListener(new RunListener() {
        	private boolean awaitingResult = false;
        	
        	public void testRunStarted(Description description) throws Exception {
        	}
        	public void testRunFinished(Result result) throws Exception {
        	}
        	public void testStarted(Description description) throws Exception {
        		reportProgress("testStarted : " + description);
        		awaitingResult = true;
        	}
        	public void testFinished(Description description) throws Exception {
        		reportProgress("testFinished: " + description);
        		// Only report result if test has not already failed  
        		if (awaitingResult == true) {
        			addResult("pass", platform, description, null, null);
        			awaitingResult = false;
        		}
        	}
        	public void testFailure(Failure failure) throws Exception {
        		FirstFailureDumper.instance().createDumpIfFirstFailure(test);
        		reportProgress("testFailure: " + failure);
        		failures.add(failure.getException());
        		failure.getException().printStackTrace(System.out);
       			addResult("fail", platform, failure.getDescription(), failure.getMessage(), failure.getException());
       			awaitingResult = false;
        	}
			public void testAssumptionFailure(Failure failure) {
				FirstFailureDumper.instance().createDumpIfFirstFailure(test);
				reportProgress("testAssumptionFailure: " + failure);
        		failures.add(failure.getException());
       			addResult("fail", platform, failure.getDescription(), failure.getMessage(), failure.getException());
       			awaitingResult = false;
        	}
        	public void testIgnored(Description description) throws Exception {
        		reportProgress("testIgnored: " + description);
        		addResult("ignored", platform, description, null, null);
        	}

        	private void reportProgress(String testInfo) {
        		// With extra flushing to try and keep output in approximate time order - to help with debugging
        		System.out.flush();
        		System.err.flush();
        		System.out.println(testInfo);
        		System.out.flush();
        		System.err.flush();
        	}
        	
        	// Record the progress
        	private void addResult(String status, String platform, Description description, String message, Throwable exception) {
        		String messageText   = message == null ? "" : " message=\"" + message + "\"";
        		String exceptionText = exception == null ? "" : " exception=" + exception.getClass().getName();
        		String resultLine = "platform=" + platform 
        					+ " result=" + status 
        					+ " class=" + description.getClassName() 
        					+ " test=" + description.getMethodName() 
        					+ messageText
        					+ exceptionText; 
        		resultsText.append(resultLine + "\n");
        	}
        });
        

        // Run the tests
        Request request = Request.classes(testClasses.toArray(new Class[testClasses.size()]));
		Result result = jUnitCore.run(request);
		
		// Report results
		System.out.println();
		System.out.println("JUnit Test Results for: " + testClass);
        System.out.println("  Ran    : " + result.getRunCount());
        System.out.println("  Passed : " + (result.getRunCount() - result.getFailureCount()));
        System.out.println("  Failed : " + result.getFailureCount());
        System.out.println("  Ignored: " + result.getIgnoreCount());
        System.out.println("  Result : " + (result.wasSuccessful() ? "PASSED" : "FAILED"));

        // Only throw an exception if OOM, to allow abort of test run
        for (Throwable e : failures) {
        	if (e instanceof java.lang.OutOfMemoryError) {
        		throw e;
        	}
        }
        
        ResultStatus overallResult;
        if (result.wasSuccessful()) {
        	overallResult = ResultStatus.PASS;
        } else {
        	overallResult = ResultStatus.FAIL;
        }
        return overallResult;
	}


	public ResultStatus checkTestOutput(byte[] output, int off, int len) {
		return ResultStatus.UNKNOWN;
	}
	
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!(o instanceof JUnitAdaptor)) {
			return false;
		}
		JUnitAdaptor junit = (JUnitAdaptor) o;
		return testClass.equals(junit.testClass);
	}
	
	public int hashCode() { 
		return testClass.hashCode();
	}

	public String toString() { 
		return "JUnit[" + testClass + "]";
	}
}