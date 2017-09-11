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
 * This interface allows different data formatters to be used by the FormatExecutionLog program. 
 */
public interface FormatterInterface {
	/**
	 * Called before opening any load test execution log files, to allow the
	 * formatter to initialise itself. 
	 * @param metaData basically holds the contents of the .ltm metadata file.
	 * @param verbose If set to true then the formatter should provide appropriate extra information.
	 */
	void start(ExecutionLogMetaData metaData, boolean verbose);

	/**
	 * Called when a gap in execution log files is detected. It is called for each missing log file.
	 * This method is called when the gap is detected. eg, if log file 4 is 
	 * missing then the callback sequence would be 1) processRecord(last-from-log-3), 2) missingLogFile(4)
	 * and then 3) processRecord(first-from-log5).
	 * @param missingLogNumber is the number of the missing log file.
	 */
	void missingLogFile(int missingLogNumber);

	/**
	 * Allows the formatter to examine a single execution log file.
	 * @param logFile is the number of the log file containing this record.
	 * @param record contains information about a test start/end from the .ltd file.
	 * @param offset is the number of milliseconds between the start of the run and
	 * the original creation of this record. 
	 */
	void processRecord(int logFile, ExecutionRecord record, long offset);

	/**
	 * Called at the end of execution log file processing, for those formatters that 
	 * need to produce their output at the end.
	 */
	void end();
}