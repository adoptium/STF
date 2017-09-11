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

package net.adoptopenjdk.loadTestAnalysis.formatter;

import net.adoptopenjdk.loadTest.reporting.ExecutionRecord;
import net.adoptopenjdk.loadTestAnalysis.ExecutionLogMetaData;


/**
 * This formatter summaries the results from a run.
 *  
 * It lists the key data about each individual execution log and displays overall 
 * pass/fail counts.
 * 
 * Example output:
 * Test failures:
 *   Failure 1) Test number=3 Test=net.adoptopenjdk.stf.sample.ArbitraryJavaTest:runTest()
 *   Failure 2) Test number=25 Test=net.adoptopenjdk.stf.sample.ArbitraryJavaTest:runTest()
 *   Failure 3) Test number=46 Test=net.adoptopenjdk.stf.sample.ArbitraryJavaTest:runTest()
 * or with '--verbose':
 *   Test failures:
 *     Failure 1) Test number=3 Test=net.adoptopenjdk.stf.sample.ArbitraryJavaTest:runTest()
 *   Test failed:
 *   java.lang.IllegalStateException: forced failure
 *   	at net.adoptopenjdk.stf.sample.ArbitraryJavaTest.runTest(ArbitraryJavaTest.java:55)
 *   	at sun.reflect.GeneratedMethodAccessor5.invoke(Unknown Source)
 *   	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:55)
 *   ...
 */
public class FailureFormatter implements FormatterInterface {
	private ExecutionLogMetaData metaData;
	private boolean verbose;
	
	private int numFailuresFound = 0;
	private int numMissingLogs = 0;

	public void start(ExecutionLogMetaData metaData, boolean verbose) {
		this.metaData = metaData;
		this.verbose = verbose;
	}

	
	public void processRecord(int logFile, ExecutionRecord record, long offset) {
		if (record.getAction().isFailure()) {
			// Print heading
			if (numFailuresFound == 0) {
				System.out.println("Test failures:");
			}
			numFailuresFound++;
			
			// Output details about the failure
			int testNum = record.getTestNum();
			System.out.print("  Failure " + numFailuresFound + ") Test number=" + testNum + " Test=" + metaData.getTestClassName(testNum));
			if (metaData.getTestMethodName(testNum).length() > 0) {
				System.out.print(":" + metaData.getTestMethodName(testNum) + "()");
			}
			System.out.println();
			
			if (verbose && record.getAction().hasOutput()) {
				System.out.println(record.getOutputAsString());
			}
		}
	}
	
	
	public void missingLogFile(int missingLogNumber) {
		numMissingLogs++;
	}


	public void end() {
		if (numFailuresFound == 0) {
			System.out.println("No failures found.");
		}
		if (numMissingLogs > 0) {
			System.out.println("Note: " + numMissingLogs + " log files are missing.");
		}
	}
}