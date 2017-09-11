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

import java.text.SimpleDateFormat;
import java.util.Date;

import net.adoptopenjdk.loadTest.reporting.ExecutionRecord;
import net.adoptopenjdk.loadTest.reporting.ExecutionRecord.Action;
import net.adoptopenjdk.loadTestAnalysis.ExecutionLogMetaData;


/**
 * This formatter produces detailed information about every record in the remaining execution logs.
 * 
 * Example output:
 *    Timestamp       Delta Thr Event      Test   Test name                                                         0  1  2  3  4  5  6
 *   11:48:37.371    +111ms  0 Started    1147 ..testlet.java.lang.ExceptionInInitializerError.classInfo.isArray -  o                   
 *   11:48:37.371    +111ms  4 Started     942 gnu.testlet.java.lang.Double.classInfo.getDeclaredAnnotations     -  |           o       
 *   11:48:37.371    +111ms  2 Started    2528 gnu.testlet.java.lang.Short.classInfo.isAssignableFrom            -  |     o     |       
 *   11:48:37.372    +112ms  5 Started     874 ..let.java.lang.ClassNotFoundException.classInfo.isAnonymousClass -  |     |     |  o    
 *   11:48:37.372    +112ms  3 Started    1557 ..let.java.lang.InheritableThreadLocal.classInfo.getCanonicalName -  |     |  o  |  |    
 *   11:48:37.375    +115ms  1 Started    1513 ..et.java.lang.IndexOutOfBoundsException.classInfo.getAnnotations -  |  o  |  |  |  |    
 *   11:48:37.375    +115ms  6 Started    3133 gnu.testlet.java.text.AttributedString.getIterator                -  |  |  |  |  |  |  o 
 *   11:48:37.383    +123ms  4 Completed   942 gnu.testlet.java.lang.Double.classInfo.getDeclaredAnnotations     -  |  |  |  |  V  |  | 
 *   11:48:37.383    +123ms  3 Passed     1557 ..let.java.lang.InheritableThreadLocal.classInfo.getCanonicalName -  |  |  |  V     |  | 
 *   11:48:37.383    +123ms  4 Started    1932 ..ang.NegativeArraySizeException.classInfo.getDeclaredConstructor -  |  |  |     o  |  | 
 */
public class DetailFormatter implements FormatterInterface {
	private SimpleDateFormat formatter;
	private boolean verbose;
	
	// Buffer the output to improve performance. This buffer almost halves execution time.
	private StringBuilder outputBuffer = new StringBuilder();

	// Create an array to keep track of whether or not each thread is working. Index by column number
    boolean[] threadRunningTest = null; 
    
    // Store name for each test. Possibly truncated due to lack of space
    private int MAX_TEST_NAME = 65;
    private String testNames[];
    
	
	public void start(ExecutionLogMetaData metaData, boolean verbose) {
		this.verbose = verbose;
		this.formatter = metaData.getFormatter();
		this.threadRunningTest = new boolean[metaData.getTotalNumberThreads()];

		// Build array containing names for all tests
		testNames = new String[metaData.getTotalNumberTests()];
		for (int testNum=0; testNum<metaData.getTotalNumberTests(); testNum++) {
			String className = metaData.getTestClassName(testNum);
			String methodName = metaData.getTestMethodName(testNum);
			if (className == null) {
				testNames[testNum] = "";
			} else {
				String testName = className;
				if (!methodName.isEmpty()) {
					// Must be an arbitrary java test. Add the method name onto the test name
					testName = testName + ":" + methodName + "()";
				}
				testNames[testNum] = testName;
			}
		}
		
		// Examine all test names and truncate those that are too long.
		boolean truncationDone = false;
		for (int i=0; i<testNames.length; i++) {
			String testName = testNames[i];
			if (testName.length() > MAX_TEST_NAME) {
				int trailingPartStart = Math.max(0, testName.length()-MAX_TEST_NAME+2);
				String truncatedTestName = ".." + testName.substring(trailingPartStart);
				testNames[i] = truncatedTestName;
				
				if (!truncationDone) {
					System.out.println("Truncated test names:");
					truncationDone = true;
				}
				System.out.println("  Test " + i + ": " + testName + " -> " + truncatedTestName);
			}
		}
		if (truncationDone) { 
			System.out.println();
		}

	    // Explain which suite owns which threads
		System.out.println("Ownership of " + metaData.getTotalNumberThreads() + " worker threads:");
	    int firstThreadNum = 0;
	    int lastThreadNum = 0;
	    for (int s=0; s<metaData.getNumberSuites(); s++) {
	    	lastThreadNum = firstThreadNum + metaData.getSuiteNumThreads(s) - 1;
	    	if (firstThreadNum == lastThreadNum) {
	    		System.out.println("  Suite " + s + " owns thread: " + firstThreadNum);
	    	} else {
	    		System.out.println("  Suite " + s + " owns threads: " + firstThreadNum + " to " + lastThreadNum);
	    	}
	    	firstThreadNum = lastThreadNum + 1;
	    }
	    System.out.println();
	    
    	// Output header lines to show which suites owns which threads. eg,
    	//   <--- suite 0 ----><-- suite 1 -->< suite 2 -><---3---><--4-><5>
    	//    0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20
	    if (metaData.getNumberSuites() > 1) {
	    	int leadingSpaces = 42 + MAX_TEST_NAME + 3;
	    	System.out.print(String.format("%" + leadingSpaces + "s", ""));
		    for (int s=0; s<metaData.getNumberSuites(); s++) {
		    	// Build a char array such as '<----------->'
		    	char[] header = new char[metaData.getSuiteNumThreads(s)*3];
		    	header[0] = '<';
		    	for (int i=1; i<header.length-1; i++) {
		    		header[i] = '-';
		    	}
		    	header[header.length-1] = '>';
		    	
		    	// Add the suite number to the header string
		    	if (metaData.getSuiteNumThreads(s) < 4) {
		    		// Output the suite number in the middle of the '<---->' string, eg, '<--3->'
		    		header[header.length/2] = (char)(s + '0');
		    	} else {
		    		// Add longer descriptive text and suite number, to create string such as '<-- suite 1 -->'
		    		char[] suiteLabel = (" suite " + s + " ").toCharArray();
		    		int insertPos = (header.length - suiteLabel.length) /2;
		    		System.arraycopy(suiteLabel, 0, header, insertPos, suiteLabel.length);
		    	}
		    	System.out.print(header);
		    }
		    System.out.println();
	    }
	    
	    
	    // Output banner line to describe column contents
	    System.out.print(String.format(" Timestamp       Delta Thr Event      Test   Test name %" + (MAX_TEST_NAME-11) + "s", " "));
	    for (int i=0; i<metaData.getTotalNumberThreads(); i++) {
	    	// Output thread number
	    	System.out.print(String.format("%3d", i));
	    }
	    System.out.println();
	}


	
	public void processRecord(int logFile, ExecutionRecord record, long offset) {
		// Prepare data for start of trace line
    	String formattedTime = formatter.format(new Date(record.getTimestamp()));
		Action action = record.getAction();
		int threadNumber = record.getThreadNum();
		int testNumber = record.getTestNum();

		// Output first part of event line: timestamp, testname, etc. 
		String tracePrefix = String.format("%s %+7dms %2d %-10s %4d %-" + MAX_TEST_NAME + "s - ", 
				formattedTime, offset, threadNumber, action.getName(), testNumber, testNames[testNumber]);
		outputBuffer.append(tracePrefix);
		
		// Output activity summary for each thread. 3 characters per thread.
		for (int i=0; i<threadRunningTest.length; i++) {
			if (i == threadNumber && action == Action.STARTED) {
				threadRunningTest[i] = true;
				outputBuffer.append(" o ");
			} else if (i == threadNumber && action.isFailure()) {
				threadRunningTest[i] = false;
				outputBuffer.append(" # ");
			} else if (i == threadNumber) {
				threadRunningTest[i] = false;
				outputBuffer.append(" V ");
			} else if (threadRunningTest[i]) {
				outputBuffer.append(" | ");
			} else {
				outputBuffer.append("   ");
			}
		}
		outputBuffer.append("\n");

		// Show output from failed tests when in verbose mode
		if (verbose && action.hasOutput()) {
			outputBuffer.append(record.getOutputAsString() + "/n");
		}

		// Flush output if enough has built up
		if (outputBuffer.length() > 4000) {
			System.out.print(outputBuffer);
			outputBuffer.setLength(0);
		}
	}
	
	
	public void missingLogFile(int missingLogNumber) {
		// Output text to show that there is a gap in the log files
		outputBuffer.append(String.format("%-" + (44 + MAX_TEST_NAME) + "s", "  Hole detected in log files. Missing log file number=" + missingLogNumber));
		StringBuilder perforations = new StringBuilder();
		while (perforations.length() < threadRunningTest.length*3) {
			perforations.append("^v");
		}
		outputBuffer.append(perforations + "/n");
		
		// Reset thread state, as we have no idea if they are busy or not.
		for (int i=0; i<threadRunningTest.length; i++) {
			threadRunningTest[i] = false;
		}
	}

	
	public void end() {
		// Flush any remaining output
		if (outputBuffer.length() > 0) {
			System.out.print(outputBuffer);
		}
	}
}