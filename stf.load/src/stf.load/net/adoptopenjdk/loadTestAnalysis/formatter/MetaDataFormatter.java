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
import net.adoptopenjdk.loadTestAnalysis.ExecutionLogMetaData;


/**
 * This formatter dumps the data which has been read from the metadata file.
 */
public class MetaDataFormatter implements FormatterInterface {
	public void start(ExecutionLogMetaData metaData, boolean verbose) {
		SimpleDateFormat formatter = metaData.getFormatter();

		System.out.println("File version: " + metaData.getVersion());
		System.out.println();
		System.out.println("Start time:   " + formatter.format(new Date(metaData.getBaseTimestamp())));
		System.out.println("Time zone:    " + metaData.getTimeZoneId());
		System.out.println();
		
		// Output suite information
		System.out.println("Number suites: "  + metaData.getNumberSuites());
		for (int suiteNum=0; suiteNum<metaData.getNumberSuites(); suiteNum++) {
			System.out.println("Suite " + suiteNum);
			System.out.println("  Number threads: " + metaData.getSuiteNumThreads(suiteNum));
			System.out.println("  Inventory     : " + metaData.getSuiteInventoryName(suiteNum));
			System.out.println("  Number tests  : " + metaData.getSuiteNumTests(suiteNum));
		}
		
		System.out.println();
		System.out.println("Total thread count: " + metaData.getTotalNumberThreads());

		// Output information about each test
		System.out.println();
		System.out.println("Tests");
		for (int testNum=0; testNum<metaData.getTotalNumberTests(); testNum++) {
			String className = metaData.getTestClassName(testNum);
			String methodName = metaData.getTestMethodName(testNum);
			if (className == null) {
				System.out.println("  #" + testNum + " <ignored>");
			} else {
				String methodDescription = methodName.isEmpty() ? "" : " method:"+methodName;
				System.out.println("  #" + testNum + " " + className + methodDescription);
			}
		}
	}

	
	public void processRecord(int logFile, ExecutionRecord record, long offset) {
	}

	public void missingLogFile(int missingLogNumber) {
	}
	
	public void end() {
	}
}