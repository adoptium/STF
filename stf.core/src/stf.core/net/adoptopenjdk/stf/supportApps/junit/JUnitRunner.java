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

package net.adoptopenjdk.stf.supportApps.junit;

import java.io.File;
import java.util.ArrayList;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.PlatformFinder;
import net.adoptopenjdk.stf.results.RunResultsFilter;


/** 
 * This class runs some JUnit tests and reports the results.
 * 
 * The main benefit of this class is that it is easy to identify 
 * hanging/crashing methods because this class reports the entry 
 * and exit of each test method. 
 */
public class JUnitRunner {
    public static void main(String... args) throws ClassNotFoundException, StfException {
    	int firstClassNameArg = 0;
    	
    	// The first argument can optionally be the name of a file containing test exclusions
    	boolean useExclusions = false;
    	File exclusionsFile = new File(args[0]);
    	if (exclusionsFile.exists()) {
    		System.out.println("Going to use test exclusions at: " + exclusionsFile.getAbsolutePath());
    		useExclusions = true;
    		firstClassNameArg = 1;
    	}
    	
    	// Find the test classes that need to be run
    	System.out.println("Test classes to run:");
    	ArrayList<Class<?>> testClasses = new ArrayList<Class<?>>();
    	for (int i=firstClassNameArg; i<args.length; i++) {
    		String className = args[i];
    		System.out.println("  " + className);
    		testClasses.add(Class.forName(className));
    	}
    	System.out.println();

    	// The results from this run are collected into this string buffer.
    	// Using string buffer for thread safety, as the anonymous inner class may be 
    	// run in a different thread.
    	final StringBuffer resultsText = new StringBuffer();
    	
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
        		reportProgress("testFailure: " + failure);
        		failure.getException().printStackTrace(System.out);
       			addResult("fail", platform, failure.getDescription(), failure.getMessage(), failure.getException());
       			awaitingResult = false;
        	}
			public void testAssumptionFailure(Failure failure) {
				reportProgress("testAssumptionFailure: " + failure);
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
		if (useExclusions) { 
			System.out.println("Test Results before filtering:");
		} else {
			System.out.println("Test Results:");
		}
        System.out.println("  Ran    : " + result.getRunCount());
        System.out.println("  Passed : " + (result.getRunCount() - result.getFailureCount()));
        System.out.println("  Failed : " + result.getFailureCount());
        System.out.println("  Ignored: " + result.getIgnoreCount());
        System.out.println("  Result : " + (result.wasSuccessful() ? "PASSED" : "FAILED"));
        boolean testRunPassed = result.wasSuccessful();

        // Optionally apply rules which allow failed tests to be re-categorised as having passed.
        if (useExclusions) {
        	RunResultsFilter runResultsFilter = new RunResultsFilter();
			testRunPassed = runResultsFilter.process(resultsText.toString(), exclusionsFile);
        }
        
        System.exit(testRunPassed ? 0 : 1);
    }
}