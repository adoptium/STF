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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import net.adoptopenjdk.loadTest.reporting.ExecutionRecord;
import net.adoptopenjdk.loadTestAnalysis.formatter.DetailFormatter;
import net.adoptopenjdk.loadTestAnalysis.formatter.FailureFormatter;
import net.adoptopenjdk.loadTestAnalysis.formatter.FormatterInterface;
import net.adoptopenjdk.loadTestAnalysis.formatter.MetaDataFormatter;
import net.adoptopenjdk.loadTestAnalysis.formatter.SummaryFormatter;


/**
 * This class provides a human readable dump of the load test execution logs.
 * 
 * It reads the test start time from the .ltm (load test metadata) file.
 * Depending on the command line arguments it produces different type of dump/analysis of the data. 
 */
public class FormatExecutionLog {
    // Command line arguments control the output mode to use
	private enum OutputMode {
		MODE_DETAIL,	// Dumps every start + stop log file entry
		MODE_FAILURES,  // Lists test failures
		MODE_SUMMARY,   // Produces stats on number of tests ran, passed, etc.
		MODE_METADATA,  // Dump metadata from .ltm file
	};
	
	
	public static void main(String[] args) throws IOException {
		ArrayList<OutputMode> specifiedModes = new ArrayList<OutputMode>();
		boolean verbose = false;
		String executionLog = null;
		
		int i = 0;
		while (i < args.length) {
			String argName = args[i++];
			
			if (argName.equals("--detail") || argName.equals("-d")) {
				specifiedModes.add(OutputMode.MODE_DETAIL);
				
			} else if (argName.equals("--summary") || argName.equals("-s")) {
				specifiedModes.add(OutputMode.MODE_SUMMARY);
					
			} else if (argName.equals("--failures") || argName.equals("-f")) {
				specifiedModes.add(OutputMode.MODE_FAILURES);
				
			} else if (argName.equals("--metadata") || argName.equals("-m")) {
				specifiedModes.add(OutputMode.MODE_METADATA);
				
			} else if (argName.equals("--verbose") || argName.equals("-v")) {
				verbose = true;

			} else if (i == args.length) {
				executionLog = argName;
				
			} else {
				usage("Unknown argument '" + argName + "' " + i + " " + args.length);
				System.exit(2);
			}
		}

		
		// Validate the mode
		if (specifiedModes.isEmpty()) {
			usage("No mode argument supplied.");
			System.exit(2);
		} else if (specifiedModes.size() > 1) {
			usage("Only 1 mode argument can be supplied.");
			System.exit(2);
		}
		OutputMode mode = specifiedModes.get(0);
		
		// Validate execution log name
		if (executionLog == null) {
			usage("Load test execution log file must be specified.");
			System.exit(2);
		}
		if (executionLog.endsWith(".ltm") || executionLog.endsWith(".ltd")) {
			// Remove the extension, so that we can keep running
			executionLog = executionLog.substring(0, executionLog.length()-4);
			System.out.println("Truncating log name to: " + executionLog);
		}

		// Finally output some of the ltm/ltd file content
		FormatterInterface formatter = createFormatter(mode);
		new FormatExecutionLog().dump(formatter, verbose, executionLog);
	}


	private static FormatterInterface createFormatter(OutputMode mode) {
		if (mode == OutputMode.MODE_DETAIL) {
			return new DetailFormatter();
		} else if (mode == OutputMode.MODE_FAILURES) {
			return new FailureFormatter();
		} else if (mode == OutputMode.MODE_SUMMARY) {
			return new SummaryFormatter();
		} else if (mode == OutputMode.MODE_METADATA) {
			return new MetaDataFormatter();
		} else {
			throw new IllegalStateException("Failed to create formatter for mode: " + mode);
		}		
	}


	private static void usage(String errorMessage) {
		System.out.println("FormatExecutionLog failed to parse arguments.");
		System.out.println("Error: " + errorMessage);
		System.out.println("Usage: FormatExecutionLog --summary -s --chart -c <execution-log>");
		System.out.println("Where:");
		System.out.println("  --detail, -d   Lists detailed test stop/start data.");
		System.out.println("  --summary, -s  Produce summary information about the log file.");
		System.out.println("  --failures, -f List test failures.");
		System.out.println("  --metadata, -m Dumps metadata from the .ltm file.");
		System.out.println("  --verbose, -v  Lists extra details when available.");
		System.out.println("  <execution-log>  Points to a load test execution log.");
		System.out.println("                   For example, '/stf/SampleLoadTest/results/1.LT.executionlog'");
	}
	

	// Returns an array with the numbers of load test data files.
	// Data is returned in oldest to newest order.
	// There may be holes in the sequence following log deletion to keep
	// log files within disk space limits.
	// eg, could return: 1, 2, 3, 8, 10, 11
	private ArrayList<Integer> findDataFiles(String baseNameExecutionLog) {
		ArrayList<Integer> dataFileNumbers = new ArrayList<Integer>();
		
		// Build ArrayList of the numbers of all .ltd files
		File baseNameExecutionLogFile = new File(baseNameExecutionLog).getAbsoluteFile();
		File containingDir = baseNameExecutionLogFile.getParentFile();
		for (String fileName : containingDir.list()) {
			if (fileName.endsWith(".ltd")  &&  fileName.startsWith(baseNameExecutionLogFile.getName())) {
				String nameWithExtension = fileName.substring(0, fileName.length()-".ltd".length());
				String partNumString = nameWithExtension.substring(nameWithExtension.lastIndexOf('.')+1);
				int partNum = Integer.parseInt(partNumString);
				dataFileNumbers.add(partNum);
			}
		}
		
		// Sort .ltd file numbers from oldest to newest
		Collections.sort(dataFileNumbers);
		
		return dataFileNumbers;
	}
	

	// Reads contents of all execution log files and gives the contents to the formatter.
	// Note that there may be hundreds of megabytes of log file data, but this record-by-record
	// processing keeps the memory footprint very low.
	private void dump(FormatterInterface formatterClass, boolean verbose, String baseNameExecutionLog) throws IOException {
		System.out.println("Formatting execution log for: " + baseNameExecutionLog);
		System.out.println();
		
		// Find out when the test started
		// Note that the timestamp formatter is set to the time zone that the test executed in 
		ExecutionLogMetaData metaData = new ExecutionLogMetaData(baseNameExecutionLog);

		long baseTimestamp = metaData.getBaseTimestamp();
		formatterClass.start(metaData, verbose);
		
		// Find out which log file numbers still exist
		ArrayList<Integer> dataFiles = findDataFiles(baseNameExecutionLog);
		
		// Dump content of all remaining log files
		int lastFileNum = 0;
		for (int fileNum : dataFiles) {
			// Report any log file gaps
			for (int i=lastFileNum+1; i<fileNum; i++) {
				formatterClass.missingLogFile(i);
			}
			lastFileNum = fileNum;
			
			// Process all records in current log file
			File dataFile = new File(baseNameExecutionLog + "." + fileNum + ".ltd");
			InputStream dataFileInput = new BufferedInputStream(new FileInputStream(dataFile), 64*1024);
			try {
				while (true) {
					// Read the next record
					ExecutionRecord record = ExecutionRecord.createFromBytes(dataFileInput, baseTimestamp);
					if (record == null) {
						break; // EOF
					}

					// Allow the formatter to process the current record
					long offset = record.getTimestamp() - baseTimestamp;
					formatterClass.processRecord(fileNum, record, offset);					
				}
			} finally {
				dataFileInput.close();
			}
		}
		
		formatterClass.end();
	}
}