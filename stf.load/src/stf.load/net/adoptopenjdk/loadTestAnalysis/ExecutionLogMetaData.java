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

package net.adoptopenjdk.loadTestAnalysis;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;


/**
 * This class reads and holds the metadata from a load test execution log.
 * It reads the .ltm file and then makes the metadata available to output formatters.
 */
public class ExecutionLogMetaData {
	private int version;
	
	private long baseTimestamp;
	private String timeZoneId;

	// To hold data about each suite
	private class SuiteData {
		int numberThreads;
		String inventoryFileName;
		int numberTests;
		ArrayList<TestData> tests;
	}
	private ArrayList<SuiteData> suites = new ArrayList<SuiteData>();
	
	// To hold details about tests
	private class TestData {
		int testNumber;
		String className;
		String methodName;
	}
	private TestData[] tests;
	
	
	public ExecutionLogMetaData(String baseNameExecutionLog) throws IOException {
		File executionMetaDataFile = new File(baseNameExecutionLog + ".ltm");
		
		if (!executionMetaDataFile.exists()) { 
			throw new IllegalStateException("Execution log meta data does not exist: " + executionMetaDataFile);
		}
		
		DataInputStream metaDataInput = new DataInputStream(new BufferedInputStream(new FileInputStream(executionMetaDataFile)));
		
		try {
			this.version = metaDataInput.readByte();
			if (version == -1) { 
				throw new IllegalStateException("Execution log file is empty");
			} else if (version != 4) { 
				throw new IllegalStateException("Unsupported metadata version number: " + version);
			}
			
		    // Read the next 8 bytes which contain the timestamp that the load test was started.
			this.baseTimestamp = metaDataInput.readLong();

			// Read string containing the time zone that the test ran in
			this.timeZoneId = metaDataInput.readUTF();
			
			// Read suite data
			int biggestTestNumber = 0;
			int numSuites = metaDataInput.readInt();
			for (int s=0; s<numSuites; s++) {
				SuiteData suite = new SuiteData();
				suite.numberThreads     = metaDataInput.readInt();
				suite.inventoryFileName = metaDataInput.readUTF();
				suite.numberTests       = metaDataInput.readInt();
				suites.add(suite);

				// Read information about all tests for current suite
				suite.tests = new ArrayList<TestData>();
				for (int i=0; i<suite.numberTests; i++) {
					TestData test = new TestData();
					test.testNumber = metaDataInput.readInt();
					test.className  = metaDataInput.readUTF();
					test.methodName = metaDataInput.readUTF();
					suite.tests.add(test);
					biggestTestNumber = Math.max(biggestTestNumber, test.testNumber);
				}
			}

			// Chuck all tests from the suites into an array
			this.tests = new TestData[biggestTestNumber+1];
			for (SuiteData suite : suites) {
				for (TestData test : suite.tests) {
					this.tests[test.testNumber] = test;
				}
			}
		} finally {
			metaDataInput.close();
		}
	}

	/**
	 * @return a new DateFormatter set to the timezone that was used when the test ran.
	 */
	public SimpleDateFormat getFormatter() {
		SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
		formatter.setTimeZone(TimeZone.getTimeZone(timeZoneId));
		return formatter;
	}

	public long getVersion() {
		return version;
	}

	public long getBaseTimestamp() {
		return baseTimestamp;
	}

	public String getTimeZoneId() {
		return timeZoneId;
	}

	public int getNumberSuites() {
		return suites.size();
	}

	public int getTotalNumberThreads() {
		int numThreads = 0;
		for (SuiteData suite : suites) {
			numThreads += suite.numberThreads;
		}
		return numThreads;
	}
	
	public int getTotalNumberTests() {
		return tests.length;
	}
	
	public int getSuiteNumThreads(int suiteNum) {
		return suites.get(suiteNum).numberThreads;
	}
	
	public String getSuiteInventoryName(int suiteNum) {
		return suites.get(suiteNum).inventoryFileName;
	}
	
	public int getSuiteNumTests(int suiteNum) {
		return suites.get(suiteNum).numberTests;
	}

	public String getTestClassName(int testNum) {
		if (tests[testNum] == null) {
			return null;
		}
		return tests[testNum].className;
	}

	public String getTestMethodName(int testNum) {
		if (tests[testNum] == null) {
			return null;
		}
		return tests[testNum].methodName;
	}
}