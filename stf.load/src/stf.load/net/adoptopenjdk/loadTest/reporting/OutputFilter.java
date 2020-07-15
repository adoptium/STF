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

import java.io.PrintStream;


/**
 * This class examines the output which would normally be written to stdout and stderr.
 * 
 * The principle reasons for intercepting the output are:
 *   1) Tests such as Mauve write output with magic 'Pass:' and 'Fail:' lines. These
 *      need to be examined to tell if the test has passed or failed.
 *   2) Allows the output from a single test to be captured (rather than intermingled 
 *      with the output from many other concurrent threads).
 *   3) Some tests simply produce too much output to be realistically held (eg, Mauve).
 *      Logging all output can also have a significant performance impact. 
 */
public class OutputFilter extends PrintStream {
	private boolean echoToOriginal;

	/**
	 * Create a new stream object to intercept output.
	 * 
	 * @param originalStream is the stream to examine. Either stdout or stderr.
	 * @param echoToOriginal if set to true then the output is passed to the original stream.
	 */
	public OutputFilter(PrintStream originalStream, boolean echoToOriginal) {
		super(originalStream);
		this.echoToOriginal = echoToOriginal;
	}

	
	/**
	 * The stream has been told to write out some data.
	 * Allow the test to examine the output. 
	 * Note: Time critical method, as caller synchronises all output.
	 */
	public void write(byte buf[], int off, int len) {
		// Find out what the load test has told this thread to do
		ExecutionTracker executionTracker = ExecutionTracker.instance();

		if (executionTracker.isRunningTest()) {
			try {
				// Allow the test adaptor to examine the output, and decide if test is passing/failing
				executionTracker.checkTestOutput(buf, off, len);
			} catch (MauveTestFailureException e) {}

			if (echoToOriginal) {
				// Write to the original stdout/stderr
				super.write(buf, off, len);
			}
			
		} else {
			// Thread is not running a test. Write the output to stdout/stderr
			super.write(buf, off, len);  
		}
	}

	// To help debug this class, the specified string is written to the output
	@SuppressWarnings("unused")
	private void debug(String debugStr) {
		byte[] bytes = debugStr.getBytes();
		super.write(bytes, 0, bytes.length);  // write to stdout/stderr
	}

	public void flush() {
		super.flush();
	}
}