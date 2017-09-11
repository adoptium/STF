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

import java.io.File;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * This class tracks the creation of binary trace logs for the load test application. 
 * 
 * It is told how many log files can be saved and deletes excess logs as they
 * build up.
 * This code attempts to retain the most useful log files, with a special emphasis
 * on keeping data on the first failure and, if possible, the test execution 
 * until that point.
 */
public class ExecutionLogManager {
    private static final Logger logger = LogManager.getLogger(ExecutionLogManager.class.getName());

	private long maxNumberLogFiles;
	
	// Hold metadata about each log file
	private static class LogFileData {
		File logFile;
		int numErrors;
	}
	private ArrayList<LogFileData> logData = new ArrayList<LogFileData>();
	
	// Track number of errors found across all log files
	private long totalErrorCount;

	// Weighings assigned to each log file to decide which is the least important
	private static int WEIGHTING_FIRST_FAILURE = 9;
	private static int WEIGHTING_LOGS_BEFORE_FIRST_FAILURE = 7;
	private static int WEIGHTING_FINAL_LOG = 5;
	private static int WEIGHTING_FAILURE_LOG = 4;
	private static int WEIGHTING_IMMEDIATELY_BEFORE_FAILING_LOG = 2;
	private static int WEIGHTING_DEFAULT = 0;
	
	
	/**
	 * Construct
	 * @param logFileRetentionLimit
	 */
	public ExecutionLogManager(long maxNumberLogFiles) {
		logger.debug("Maximum number of retained log files: " + maxNumberLogFiles);
		this.maxNumberLogFiles = maxNumberLogFiles;
	}
	
	
	public void fileCompleted(File logFile, int numErrors) {
		logger.debug("Log file completed. File=" + logFile.getAbsolutePath() + " numErrors=" + numErrors);

		// Store summary data for newest log
		LogFileData newLog = new LogFileData();
		newLog.logFile = logFile;
		newLog.numErrors = numErrors;
		logData.add(newLog);
		
		// Keep running count of total number of test failures
		totalErrorCount += numErrors;
		
		// Delete a log file if we have reached the limit
		int numLogFiles = 0;
		for (LogFileData log : logData) {
			if (log != null) {
				numLogFiles++;
			}
		}
		if (numLogFiles > maxNumberLogFiles) {
			logger.debug("Going to delete a log file");
			evict();
		}
	}

	
	private void evict() {
		if (totalErrorCount == 0) { 
			// Nothing has gone wrong yet. 
			// Evict the 2nd to last log.
			doEviction(logData.size()-2);
		
		} else {
			// Carefully select the eviction victim
			int victim = findLeastImportantLogFile();
			doEviction(victim);
		}
	}


	/**
	 * Returns the index of the least import log file.
	 * 
	 * Log file precedence, from highest to lowest, is:
	 *   1. Log with first failure.
	 *   2. Complete set of logs leading to initial failure.
	 *   3. Final log.
	 *   4. Any other logs with failures.
	 *   5. Log immediately before a failing log.
	 *   6. Other log files.
	 */
	private int findLeastImportantLogFile() {
		// Reset weightings for all log files
		int[] weightings = new int[logData.size()];
		for (int i=0; i<logData.size(); i++) {
			weightings[i] = WEIGHTING_DEFAULT;
		}
		
		// Precedence 5.
		// Attempt to keep the log files just before a failure 
		for (int i=0; i<logData.size()-1; i++) {
			LogFileData currLog = logData.get(i);
			LogFileData nextLog = logData.get(i+1);
			if (currLog != null && currLog.numErrors == 0 && nextLog != null && nextLog.numErrors > 0) {
				weightings[i] = WEIGHTING_IMMEDIATELY_BEFORE_FAILING_LOG;
			}
		}

		// Precedence 4.
		// Set weighting for all logs with at least 1 failure
		for (int i=0; i<logData.size(); i++) {
			LogFileData log = logData.get(i);
			if (log != null && log.numErrors > 0) {
				weightings[i] = WEIGHTING_FAILURE_LOG;
			}
		}
		
		// Precedence 3.
		// Try to retain the latest log
		weightings[logData.size()-1] = WEIGHTING_FINAL_LOG;

		// For precedence 2 & 1, find out if we have logs leading up to the first failure 
		int firstFailureWithPreceeding = -1;
		for (int i=0; i<logData.size(); i++) {
			LogFileData log = logData.get(i);
			if (log == null) {
				break;
			} else if (log.numErrors > 0) {
				firstFailureWithPreceeding = i;
				break;
			}
		}
		
		// Precedence 2 & 1.
		// First failure and the preceding logs are the most valuable
		if (firstFailureWithPreceeding >= 0) {
			for (int i=0; i<firstFailureWithPreceeding; i++) {
				weightings[i] = WEIGHTING_LOGS_BEFORE_FIRST_FAILURE;
			}
			weightings[firstFailureWithPreceeding] = WEIGHTING_FIRST_FAILURE;
		}

		
		// Find the log with the lowest weighting
		int lowestWeighting = Integer.MAX_VALUE;
		int lowestIndex = 0;
		for (int i=0; i<logData.size(); i++) {
			LogFileData log = logData.get(i); 
			if (log != null && weightings[i] <= lowestWeighting) {
				lowestWeighting = weightings[i];
				lowestIndex = i;
			}
		}

		return lowestIndex;
	}

	
	/*
	 * Delete a log file.
	 * This method deletes the binary log file and clears out our remaining reference to it.
	 * @param logNum is the index of the log file to delete.
	 */
	private void doEviction(int victimIndex) {
		// Delete log file
		LogFileData victim = logData.get(victimIndex);
		logger.debug("Deleting log file: " + victim.logFile.getAbsolutePath());
		victim.logFile.delete();
		
		// Clear tracking data, so that it is not considered next time
		logData.set(victimIndex, null);
	}

	
	/**
	 * @return String representing currently held data. 
	 * Uses 1 character for each log file:
	 *   ' ' No data for log. File has been deleted.
	 *   '-' Log file has no errors.
	 *   '*' Log file with at least 1 test failure.
	 */
	public String toString() {
		StringBuilder buff = new StringBuilder();
		
		buff.append("[");
		for (LogFileData log : logData) {
			if (log == null) {
				buff.append(' ');
			} else if (log.numErrors == 0) {
				buff.append('-');
			} else if (log.numErrors > 0) {
				buff.append('X');
			}
		}
		buff.append("]");
		
		return buff.toString();
	}
}