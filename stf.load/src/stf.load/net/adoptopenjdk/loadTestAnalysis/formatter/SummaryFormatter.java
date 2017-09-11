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
 * This formatter summaries the results from a run.
 *  
 * It lists the key data about each individual execution log and displays overall 
 * pass/fail counts.
 * 
 * Example output::
 *   Log file summaries
 *     Part 1  Covers 10:00:41.934 to 10:00:41.956  Started:57 Passed:56 Failed:0
 *     Part 2  Covers 10:00:41.956 to 10:00:41.990  Started:57 Passed:56 Failed:0
 *     Part 3  Covers 10:00:41.991 to 10:00:42.011  Started:58 Passed:55 Failed:0
 *     Part 4  Missing
 *     Part 5  Missing
 *     Part 6  Covers 10:00:42.049 to 10:00:42.059  Started:36 Passed:37 Failed:0
 *   
 *   Log file counts
 *     Number log files found  : 4
 *     Number log files missing: 2
 *   
 *   Overall test result counts. Note: Partial results due to missing log file(s)
 *     Started: 208
 *     Passed : 204
 *     Failed : 0
 */
public class SummaryFormatter implements FormatterInterface {
	SimpleDateFormat formatter;
	
	// Data on the current log file
	private int currLogFile = 0;
	private boolean haveLogData = false;
	private long logStartTime;
	private long logEndTime;
	private long numTestsStarted;
	private long numTestsFailed;
	private long numTestsPassed;
	
	// Overall test statistics
	private int numMissingLogFiles = 0;
	private long totalTestsStarted;
	private long totalTestsFailed;
	private long totalTestsPassed;
	

	public void start(ExecutionLogMetaData metaData, boolean verbose) {
		this.formatter = metaData.getFormatter();
		System.out.println();
	}

	
	public void processRecord(int logFile, ExecutionRecord record, long offset) {
		if (currLogFile != logFile) {
			summariseLogFile();
			
			totalTestsStarted += numTestsStarted;
			totalTestsFailed  += numTestsFailed;
			totalTestsPassed  += numTestsPassed;

			currLogFile = logFile;
			haveLogData = true;
			logStartTime = record.getTimestamp();
			numTestsStarted = 0;
			numTestsFailed  = 0;
			numTestsPassed  = 0;
		}
		
		if (record.getAction() == Action.STARTED) {
			numTestsStarted++;
		} else if (record.getAction().isFailure()) {
			numTestsFailed++;
		} else {
			numTestsPassed++;
		}
		
		logEndTime = record.getTimestamp();
	}
	
	
	public void missingLogFile(int missingLogNumber) {
		summariseLogFile();
		currLogFile = missingLogNumber;
		haveLogData = false;

		System.out.printf("  Part %2d  Missing\n", missingLogNumber);
		numMissingLogFiles++;
	}

	
	private void summariseLogFile() {
		if (haveLogData) {
			if (currLogFile == 1) {
				System.out.println("Log file summaries");
			}
			
			String failureIndicator = numTestsFailed > 0 ? "* " : "  ";
			String startTimestamp = formatter.format(new Date(logStartTime));
			String endTimestamp   = formatter.format(new Date(logEndTime));
			System.out.printf("%sPart %2d  Covers %s to %s  Started:%d Passed:%d Failed:%d\n", failureIndicator, currLogFile, startTimestamp, endTimestamp, numTestsStarted, numTestsPassed, numTestsFailed);
		}
	}
	
	
	public void end() {
		// Report on the final log file
		summariseLogFile();
		
		System.out.println();
		System.out.println("Log file counts");
		System.out.println("  Number log files found  : " + (currLogFile - numMissingLogFiles));
		System.out.println("  Number log files missing: " + numMissingLogFiles);

		System.out.println();
		System.out.println("Overall test result counts" + (numMissingLogFiles > 0 ? ". Note: Partial results due to missing log file(s)" : ""));
		System.out.println("  Started: " + (totalTestsStarted + numTestsStarted));
		System.out.println("  Passed : " + (totalTestsPassed + numTestsPassed));
		System.out.println("  Failed : " + (totalTestsFailed + numTestsFailed));
	}
}