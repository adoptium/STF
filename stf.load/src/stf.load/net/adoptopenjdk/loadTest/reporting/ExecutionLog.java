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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;

import net.adoptopenjdk.loadTest.InventoryData;
import net.adoptopenjdk.loadTest.SuiteData;
import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface;


/**
 * The load test records what is happening in an execution log file. 
 * This class handles the creation of such a file.
 * 
 * This basically adds a single entry every time the following occurs:
 *   - Thread starts a test
 *   - A test completes (passes/fails or results unknown)
 *   - An exception or Throwable is caught from a test execution.
 *   
 * The data is written in a compact binary format for later post processing.
 */
public class ExecutionLog {
	// Execution log is a singleton as all output goes to the same file
	private static ExecutionLog instance = null;
	
	// This is the time that the run started. All logged times are relative to this.
	private long baseTimestamp = System.currentTimeMillis();
	
	// Holds the binary data for a single ExcutionRecord
	private byte[] buffer = new byte[ExecutionRecord.SIZE_IN_BYTES];
	
	// Output file to record to
	private File currentOutputFile;
	private OutputStream output;

	// To support log file rotation
	private String logFileBaseName;
	private ExecutionLogManager logManager;
	private int maxLogFileSize;
	private int nextLogFileNumber = 1;

	// Data about the current log file
	private int spaceLeftInCurrentLog;
	private int numErrorsInCurrentLog;
	
	
	/**
	 * Create a new log file.
	 * Writes fixed header information to the new file.
	 * 
	 * @param logFile Is the execution log file to write to.
	 * @throws IOException if we fail to write to the file.
	 */
	private ExecutionLog(File logFileBaseName, long maxTotalLogFileSpace, int maxSingleLogSize, ArrayList<SuiteData> suites) throws IOException {
		this.logFileBaseName = logFileBaseName.getAbsolutePath();

		this.maxLogFileSize = maxSingleLogSize;
		long maxNumLogFiles = maxTotalLogFileSpace / maxSingleLogSize;
		logManager = new ExecutionLogManager(maxNumLogFiles);

		// Create file with load test data applicable to all log files
		createLogFileMetaDataFile(suites);

		// Start the first log file
		createNewLoadTestDataFile();
	}

	
	// Create load test meta data file.
	// This holds the version number of the data format and the start timestamp.
	private void createLogFileMetaDataFile(ArrayList<SuiteData> suites) throws IOException {
		File loadTestMetaDataFile = new File(logFileBaseName + ".ltm");
		
		DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(loadTestMetaDataFile)));
		
		// Write a version number as the first byte of the file
		output.writeByte(4);
		
		// Write the base timestamp as the second value in the output file
		output.writeLong(baseTimestamp);

		// Write number of bytes in timeZoneId, and then the timeZoneId string itself
		String timeZoneId = Calendar.getInstance().getTimeZone().getID();
		output.writeUTF(timeZoneId);

		// Write out data about each suite
		output.writeInt(suites.size());
		for (SuiteData suite : suites) {
			InventoryData inventory = suite.getInventory();
			output.writeInt((int) suite.getNumberThreads());
			output.writeUTF(suite.getInventoryFileRef());
			output.writeInt((int) inventory.getNumberOfTests());
			
			// Write out names of all tests
			for (int t=0; t<inventory.getNumberOfTests(); t++) {
				AdaptorInterface test = inventory.getTest(t);
				output.writeInt(test.getTestNum());
				output.writeUTF(test.getTestName());
				output.writeUTF(test.getTestMethodName());
			}
		}
		
		output.close();
	}

	private void createNewLoadTestDataFile() throws IOException {
		// Close existing file
		if (output != null) {
			output.close();
			
			// Track data about this log file. Delete old log file if needed
			logManager.fileCompleted(currentOutputFile, numErrorsInCurrentLog);
		}
		
		// Start next log file
		currentOutputFile = new File(logFileBaseName + "." + nextLogFileNumber + ".ltd");
		output = new FileOutputStream(currentOutputFile);
		nextLogFileNumber++;
		
		spaceLeftInCurrentLog = maxLogFileSize;
		numErrorsInCurrentLog = 0;
	}


	public static synchronized void createInstance(File logFileBaseName, long maxTotalLogFileSpace, int maxSingleLogSize, ArrayList<SuiteData> suites) throws IOException {
		instance = new ExecutionLog(logFileBaseName, maxTotalLogFileSpace, maxSingleLogSize, suites);
	}

	public static synchronized ExecutionLog instance() {
		return instance;
	}

	/**
	 * Logs an ExecutionLog to the file.
	 * @param record holds information about the test event to log.
	 * @throws IOException if the write failed.
	 */
	public synchronized void log(ExecutionRecord record) throws IOException {
		byte[] data = record.convertToBytes(buffer, baseTimestamp);
		
		// Decide if the current log file has enough space for this record
		spaceLeftInCurrentLog -= data.length;
		if (spaceLeftInCurrentLog < 0) {
			// Log file is full, start another one
			createNewLoadTestDataFile();
			spaceLeftInCurrentLog = maxLogFileSize - data.length;
		}
		
		numErrorsInCurrentLog += record.getAction().getErrorIndicator();
		
		output.write(data);
		output.flush();
	}
	
	public synchronized void close() throws IOException {
		output.close();
	}
}