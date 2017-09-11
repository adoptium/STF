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

import java.io.IOException;
import java.io.InputStream;


/**
 * This is a simple immutable class for holding key details about the 
 * starting or completion of a single test.
 * 
 * It also handles the conversion to and from a binary form.
 */
public class ExecutionRecord {
	public enum Action {
		//                  Code   Name       Failure  Show output
		STARTED(            'S', "Started",    false,   false),
		PASSED(             'P', "Passed",     false,   false),
		TEST_RESULT_UNKNOWN('U', "Completed",  false,   false),
		BLOCKED_EXIT_PASS(  'Z', "ExitPass",   false,   false),  // Test called System.exit with zero exit value
		BLOCKED_EXIT_FAIL(  'E', "*ExitFail*", true,    true),
		FAILED(             'F', "*Failed*",   true,    true),
		FAILED_THROWABLE(   'T', "*Failed*",   true,    true);
		
		private char code;
		private String codeAsString;
		private String name;
		private boolean isFailure;
		private int errorIndicator;
		private boolean hasOutput;
		
		private Action(char code, String name, boolean isFailure, boolean hasOutput) { 
			this.code = code; 
			this.codeAsString = "" + code;
			this.name = name;
			this.isFailure = isFailure;
			this.errorIndicator = isFailure ? 1 : 0;
			this.hasOutput = hasOutput;
		}
		
		public String getName() { return name; }
		public boolean isFailure() { return isFailure; }
		public int getErrorIndicator() { return errorIndicator; }
		public boolean hasOutput() { return hasOutput; }
		public String toString() { return codeAsString; }
	};

	
	// Binary file structure for execution log is:
	//   1 byte holding the version number of the format. Starting at version 1. 
	//   8 bytes holding a long with the start time of the run (milliseconds).
	// Then any number of 9 byte records each consisting off:
	//   4 bytes holding the time in milliseconds since the start of the run. Range 0-4,294,967,296 milli (approx 49 days)
	//   1 byte with the Action code. Started, Passed, etc.
	//   2 bytes holding the test id. 0 to 65,535
	//   2 bytes holding:
	//       Uppermost 3 bits holds the suite id. 0-7
	//       Lowermost 13 bits holding the thread id. 0-8,191
	// Records with the 'hasOutput' attribute also have the following after the 9 byte record:
	//   4 bytes with length of subsequent output.
	//   The byte output captured for the failing test
	// Byte offsets for these fields are:
	public static int SIZE_IN_BYTES = 9;
	private static int TIMESTAMP_1  = 0;
	private static int TIMESTAMP_2  = 1;
	private static int TIMESTAMP_3  = 2;
	private static int TIMESTAMP_4  = 3;
	private static int ACTION_CODE  = 4;
	private static int TEST_ID_1    = 5;
	private static int TEST_ID_2    = 6;
	private static int THREAD_ID_1  = 7;
	private static int THREAD_ID_2  = 8;
	private static int OUTPUT_LEN_1 = 9;
	private static int OUTPUT_LEN_2 = 10;
	private static int OUTPUT_LEN_3 = 11;
	private static int OUTPUT_LEN_4 = 12;
	private static int OUTPUT       = 13;

	
	private long timestamp;
	
	private Action action;
	private int threadNum;
	private int suiteNum;
	private int testNum;
	private String testName;
	private String threadName;
	private byte[] output;


	ExecutionRecord(long timestamp, Action action, int threadNum, int suiteNum, int testNum, String testName, byte[] output) {
		this.timestamp = timestamp;

		this.action = action;
		this.threadNum = threadNum;
		this.suiteNum = suiteNum;
		this.testNum = testNum;
		this.testName = testName.intern();
		this.threadName = Thread.currentThread().getName();
		this.output = output;
	}

	
	public long getTimestamp() {
		return timestamp;
	}

	public Action getAction() {
		return action;
	}

	public int getSuiteId() {
		return suiteNum;
	}

	public int getTestNum() {
		return testNum;
	}

	public String getTestName() {
		return testName;
	}

	public int getThreadNum() {
		return threadNum;
	}

	public String getThreadName() {
		return threadName;
	}

	public String getOutputAsString() {
		return new String(output);
	}

//	public String getThreadReference() {
//		return getSuiteId() + "-" + getThreadName();
//	}
	
	public String getTestReference() {
		return getSuiteId() + "." + getTestName();
	}
	
	
	public byte[] convertToBytes(byte[] buffer, long baseTimestamp) {
		// Most output fits in standard size supplied buffer, but allocate new one 
		// the has output that needs to be logged
		if (action.hasOutput) {
			buffer = new byte[SIZE_IN_BYTES + 4 + output.length];

			// Add output/failure message to the buffers
			buffer[OUTPUT_LEN_1] = (byte) (output.length & 0xFF);   
		    buffer[OUTPUT_LEN_2] = (byte) ((output.length >> 8) & 0xFF);   
		    buffer[OUTPUT_LEN_3] = (byte) ((output.length >> 16) & 0xFF);   
		    buffer[OUTPUT_LEN_4] = (byte) ((output.length >> 24) & 0xFF);
		    System.arraycopy(output, 0, buffer, OUTPUT, output.length);
		}

		// First 4 bytes hold the time offset from the start of the run
		long timeOffset = timestamp - baseTimestamp;
		buffer[TIMESTAMP_1] = (byte) (timeOffset & 0xFF);   
	    buffer[TIMESTAMP_2] = (byte) ((timeOffset >> 8) & 0xFF);   
	    buffer[TIMESTAMP_3] = (byte) ((timeOffset >> 16) & 0xFF);   
	    buffer[TIMESTAMP_4] = (byte) ((timeOffset >> 24) & 0xFF);
		
	    buffer[ACTION_CODE] = (byte) action.code;

	    // Test id held in next 2 bytes
		buffer[TEST_ID_1]   = (byte) (testNum & 0xFF);
		buffer[TEST_ID_2]   = (byte) ((testNum >> 8) & 0xFF);
		
	    // Create an int with the following structure:
	    //    00000000 00000000 sssttttt tttttttt 
	    // where 
	    //   s is a suiteNum
	    //   t is a threadNum
	    int suiteAndThreadNums = (suiteNum << 13) + threadNum;
	    buffer[THREAD_ID_1] = (byte) ((suiteAndThreadNums >> 8) & 0xFF);   
	    buffer[THREAD_ID_2] = (byte) (suiteAndThreadNums & 0xFF);

	    return buffer;
	}
	
	
	/**
	 * Converts from binary execution record data to an ExecutionRecord object.
	 *  
	 * @param input is the stream to read from. The method reads only the bytes 
	 * necessary to create the ExceptionRecord object.
	 * @param baseTimestamp is the time that the load test run started.
	 * @return an ExecutionRecord object.
	 * @throws IOException if a file read failed.
	 */
	public static ExecutionRecord createFromBytes(InputStream input, long baseTimestamp) throws IOException {
		byte[] buffer = new byte[ExecutionRecord.SIZE_IN_BYTES];
		int numRead = input.read(buffer);
		if (numRead == -1) {
			return null; // EOF
		}
	
		// Read the timestamp offset
		long offset = (buffer[TIMESTAMP_1] & 0xFF) 
		 		 | ((buffer[TIMESTAMP_2] & 0xFF) << 8) 
				 | ((buffer[TIMESTAMP_3] & 0xFF) << 16) 
				 | (((long) buffer[TIMESTAMP_4] & 0xFF) << 24);
		long timestamp = baseTimestamp + offset;
		
		
		// Read the action
		char actionCode = (char) buffer[ACTION_CODE]; 
		Action action = null;
		for (Action possibleAction : Action.values()) {
			if (possibleAction.code == actionCode) {
				action = possibleAction;
			}
		}
		if (action == null) {
			throw new IllegalStateException("Unknown action: " + actionCode);
		}
		
		// Read the timestamp offset
		int testNum = ((int) buffer[TEST_ID_1] & 0xFF) 
		 		   | (((int) buffer[TEST_ID_2] & 0xFF) << 8);

		// Extract the suite and thread numbers which are packed into the next 2 bytes
	    int suiteAndThreadNums = (((int) buffer[THREAD_ID_1] & 0xFF) << 8)
	    					    | ((int) buffer[THREAD_ID_2] & 0xFF);
	    int suiteNum = (suiteAndThreadNums >> 13) & 0xFF;
	    int threadNum = (suiteAndThreadNums) & 0x1FFF;

	    // If test produced output read it in 
	    byte[] output = null;
	    if (action.hasOutput) {
	    	// Find out how long the output is
	    	byte[] bufferLen = new byte[4];
	    	input.read(bufferLen);
	    	int outputLen = (int) ((bufferLen[0] & 0xFF) 
		 		 | ((bufferLen[1] & 0xFF) << 8) 
				 | ((bufferLen[2] & 0xFF) << 16) 
				 | (((long) bufferLen[3] & 0xFF) << 24));
	    	
	    	// Read the actual output data
	    	output = new byte[outputLen];
	    	input.read(output);
	    }
		
	    String testName = "";	
		return new ExecutionRecord(timestamp, action, threadNum, suiteNum, testNum, testName, output);
	}
}