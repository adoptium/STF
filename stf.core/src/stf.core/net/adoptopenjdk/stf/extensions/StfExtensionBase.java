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

package net.adoptopenjdk.stf.extensions;

import java.util.ArrayList;
import java.util.List;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.StfExitCodes;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator.CommandDetails;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.ExpectedOutcome.OutcomeType;
import net.adoptopenjdk.stf.processes.definitions.ProcessDefinition;


/**
 * This class sits between the extension classes and the perl code generator.
 * Its responsibilities are:
 *   - keep track of running processes (regardless of which extension started them)
 *   - generate full list of processes for a die command (again regardless of particular extension)
 *   - holds code usable by multiple extensions.
 *   - reduces the work required by an extension to implement a 'do' operation.
 *   
 * At runtime there is a single instance of this class which is shared between all extensions.
 */
public class StfExtensionBase {
	private StfEnvironmentCore environmentCore;
	private PerlCodeGenerator generator;
	
	private ArrayList<StfProcess> childProcesses = new ArrayList<StfProcess>();
	
	
	public StfExtensionBase(StfEnvironmentCore environmentCore, PerlCodeGenerator generator) {
		this.environmentCore = environmentCore;
		this.generator = generator;
	}
	
	
	/**
	 * Adds a block of perl which makes sure that a perl variable is has the expected value.
	 * If the condition is true then an error message is produced and the process aborts.
	 * 
	 * @param command is a short description of the command for which code is being generated.
	 * @param comment is a description from the test plugin describing why the current command is being run.
	 * @param resultVariable is the perl variable whose result is being checked.
	 * @param operator is the comparison operator used to compare the resultVariable with the successValue 
	 * @param successValue contains the values which resultVariable can contain for a successful execution.
	 * @throws StfException if we fail to write to the output file.
	 */
	public void outputFailIfTrue(String command, String comment, String resultVariable, String operator, StfExitCodes successValues) throws StfException {
		List<Integer> successExitCodes = successValues.getAllowableExitCodes();
		if (successExitCodes.size() != 1) {
			throw new StfException("successValues should only contain a single exit code value: " + successValues.toString());
		}
		
		String resultValue = successExitCodes.get(0).toString();
		generator.outputFailIfTrue(command, comment, resultVariable, operator, resultValue, null, getRunningProcesses());
	}

	/**
	 * Variant of outputFailIfTrue which allows the caller to specify some extra
	 * lines of perl for the body of the if statement. 
	 * These lines are supplied in the 'extraErrorLines' argument.
	 */
	public void outputFailIfTrue(String command, String comment, 
			String resultVariable, String operator, String resultValue, 
			ArrayList<String> extraErrorLines) throws StfException {
		generator.outputFailIfTrue(command, comment, resultVariable, operator, resultValue, extraErrorLines, getRunningProcesses());
	}
	 
	/**
	 * Convenience method of outputFailIfTrue() which takes an int resultValue.
	 */
	public void outputFailIfTrue(String command, String comment, String resultVariable, String operator, int resultValue) throws StfException {
		String resultValueAsStr = Integer.toString(resultValue);
		generator.outputFailIfTrue(command, comment, resultVariable, operator, resultValueAsStr, null, getRunningProcesses());
	}


	/**
	 * Adds a block of perl which makes sure that a perl variable is set to '0'.
	 * If the perl variable has a different value then an error message is produced and the 
	 * process aborts.
	 * 
	 * @param command is a short description of the command for which code is being generated.
	 * @param comment is a description from the test plugin describing why the current command is being run.
	 * @param resultVariable is the perl variable whose result is being checked.
	 * @throws StfException if 'resultVariable' is not a valid perl variable name or if we fail to write to the output file.
	 */
	public void outputErrorCheck(String command, String comment, String resultVariable) throws StfException {
		generator.outputErrorCheck(command, comment, resultVariable, getRunningProcesses());
    }


	/**
	 * Adds a block of perl code which scans a log file and counts the number of matches for supplied strings. 
	 * 
	 * It does this by creating a while loop which opens a log and does a regex match on each line.
	 * If a match is found the count variable is incremented, this method also supports multiple expected messages and
	 * builds the if statement arguments accordingly.
	 * 
	 * @param resultVariable is the perl variable which holds the match count. eg, '$error_count'
	 * @param log is the log file whose output is scanned.
	 * @param expectedResultMessages contains the message/s that the log file is expected to contain.
	 * @throws StfException if anything goes wrong.
	 */
	public void outputCountFileMatches(String resultVariable, FileRef log, String... expectedResultMessages) throws StfException {
		generator.outputCountFileMatches(resultVariable, log, getRunningProcesses(), expectedResultMessages);
	}

	
	/**
	 * Adds a block of perl code to echo the contents of a file.
	 */
	public void outputEchoFile(FileRef targetFile) throws StfException {
		generator.outputEchoFile(targetFile, getRunningProcesses());
	}
	
	
	/**
	 * Outputs a perl die command which will kill all processes that may be running at the current time.
	 * @param dieMessage is a string describing the reason for generating the die command.
	 */
	public void outputDieCommand(String dieMessage) throws StfException {
		generator.outputDieCommand(getRunningProcesses(), dieMessage);
	}


	/*
	 * Returns an ArrayList containing references to all processes which may currently be running.
	 * 
	 * If a process has been killed then it is not returned as it must have been terminated.
	 * A process which has been through a monitor step, and is due to complete with an exit 
	 * code or crash is not returned as it must have completed.
	 * 
	 * In all other cases the process _may_ still be running and therefore needs to be killed.
	 * The perl layer kill command is tolerant of being asked to kill a process which is not 
	 * running. It needs to be tolerant as the process may of course have completed at any time
	 * between the last monitor command and the current point in time. 
	 */
	private ArrayList<StfProcess> getRunningProcesses() {
		ArrayList<StfProcess> runningProcesses = new ArrayList<StfProcess>();
		for (StfProcess p : childProcesses) {
			if (p.isRunning()) {
				runningProcesses.add(p);
			}
		}
		return runningProcesses;
	}

	
	/**
	 * Generates perl code for synchronously running a single process.
	 */
	public StfProcess runForegroundProcess(String comment, String processMnemonic, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		StfProcess[] processes = generateProcessExecute(comment, processMnemonic, 1, echoSetting, "run_process", false, expectedOutcome, processDetails);
		return processes[0];
	}


	/**
	 * Generates perl code for synchronously running multiple processes.
	 */
	public StfProcess[] runForegroundProcesses(String comment, String processMnemonic, int numInstances, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		if (numInstances <= 1) {
			throw new StfException("Invalid number of concurrent process instances '" + numInstances + "'. "
					+ "To run a single process call doRunForegroundProcess()");
		}
		
		return generateProcessExecute(comment, processMnemonic, numInstances, echoSetting, "run_processes", false, expectedOutcome, processDetails);
	}


	/**
	 * Generates perl code to start a single process.
	 */
	public StfProcess runBackgroundProcess(String comment, String processMnemonic, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		StfProcess[] processes = generateProcessExecute(comment, processMnemonic, 1, echoSetting, "start_process", true, expectedOutcome, processDetails);
		return processes[0];
	}

	/**
	 * Generates perl code to start multiple process.
	 */
	public StfProcess[] runBackgroundProcesses(String comment, String processMnemonic, int numInstances, Echo echoSetting, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		if (numInstances <= 1) {
			throw new StfException("Invalid number of concurrent process instances '" + numInstances + "'. "
					+ "To run a single process call doRunBackgroundProcess()");
		}
		
		return generateProcessExecute(comment, processMnemonic, numInstances, echoSetting, "start_processes", true, expectedOutcome, processDetails);
	}

	
	/**
	 * Core method for generating perl code to run processes, either synchronously or asynchronously. 
	 * @param comment is the description explaining why the process is being started.
	 * @param processMnemonic is a 3 character mnemonic for the process(es). Used as prefix for output produced by the process.
	 * @param numInstances is the number of instances to be started.
	 * @param echoSetting turns on/off process echoing.
	 * @param perlMethod is the name of the perl method that is to be invoked.
	 * @param asynchronousProcess if true process will be run in the background (and require later monitoring or killing) 
	 * @param expectedOutcome
	 * @param processDetails
	 * @return
	 * @throws StfException
	 */
	private StfProcess[] generateProcessExecute(String comment, String processMnemonic, int numInstances, Echo echoSetting, String perlMethod,
			boolean asynchronousProcess, ExpectedOutcome expectedOutcome, ProcessDefinition processDetails) throws StfException {
		processMnemonic = processMnemonic.trim();

		// Make sure the mnemonic string is not too long
		int maximumMnemonicLen = numInstances == 1 ? 4 : 3;
		if (processMnemonic.length() > maximumMnemonicLen) {
			throw new StfException("Process mnemonic too long. Maximum length is " + maximumMnemonicLen + ": " + processMnemonic);
		}
		
		// Build the command to be run
		CommandDetails command;
		if (processDetails.isJdkProgram()) {
			command = generator.buildJvmCommand(processMnemonic, numInstances, processDetails);
		} else {
			String[] args = processDetails.asArgsArray().toArray(new String[0]);
			command = generator.buildCommand(processMnemonic, numInstances, null, processDetails.getCommand(), args);
		}
		
		//Add any related processes to the CommandDetails object so the Perl generator can use them if needed.
		command.setRelatedProcesses(processDetails.getRelatedProcesses(),processDetails.getRelatedProcessesData());

		// Generate code to run java process
		StfProcess[] processes = generator.generateRunProcess2(comment, command.getArgumentComment(), processMnemonic, numInstances, echoSetting, perlMethod, expectedOutcome, command);
		// Keep track of the state of all processes. To make sure they are monitored or killed.
		for (StfProcess process : processes) {
			childProcesses.add(process);
		}
		
		// Generate code to check the return code
		StfExitCodes runCommandSuccess = StfExitCodes.expected(0);
		outputFailIfTrue(processDetails.getCommand(), comment, "$rc", "!=", runCommandSuccess);

		// Synchronous process must now be complete. They have either 
		//   1) Completed as expected (success, error, crash, etc), or 
		//   2) Exceeded their timeout, and have been killed by outputResultCheck().
		if (!asynchronousProcess) {
			for (StfProcess process : processes) {
				process.updateStateToCompleted();
			}
		}
		
		// Give the process definition a chance to do any extra work for this invocation
		processDetails.generationCompleted(command.getCommandSerialNum(), processMnemonic);
		
		return processes;
	}

	
	/**
	 * Generates the code for a monitor_process java call.
	 * @param comment is a description summarising the purpose of the monitor call.
	 * @param processesToMonitor is a list of processes which need to be monitored.
	 * @throws StfException if a logic error in the automation code is detected, 
	 * or if code generation fails.
	 */
	public void internalDoMonitorProcesses(String comment, ArrayList<StfProcess> processesToMonitor) throws StfException {
		// Don't allow monitoring of processes which are not running
		for (StfProcess p : processesToMonitor) {
			if (p.processHasCompleted()) {
				throw new StfException("Can't monitor process '" + p.getMnemonic() + "' in step '" + comment + "' "
						+ "as it has already finished.");
			}
			if (p.processHasBeenKilled()) {
				throw new StfException("Can't monitor process '" + p.getMnemonic() + "' in step '" + comment + "' "
						+ "as it has already been killed.");
			}
		}

		// Make sure that at least one of of the monitored processes is actually going to finish.
		// The monitor call does not make any sense otherwise, so abort if the caller is attempting to 
		// monitor processes which are going to run forever.
		boolean foundTerminalProcess = false;
		for (StfProcess p : processesToMonitor) {
			if (p.getExpectedCompletion().getExpectedOutcome() != OutcomeType.NEVER) {
				foundTerminalProcess = true;
			}
		}
		if (!foundTerminalProcess) {
			throw new StfException("Can't monitor process list for step '" + comment + "'" 
						+ " as none of the processes are expected to finish.");
		}

		// Generate the code to kill all processes which may still be running
		generator.monitorProcesses(processesToMonitor);
		
		// Check the return code
		StfExitCodes expectedExitCode = StfExitCodes.expected(0);
		outputFailIfTrue("java", comment, "$rc", "!=", expectedExitCode);
		
		// Update the known process state
		for (StfProcess p : processesToMonitor) {
			if (p.getExpectedCompletion().getExpectedOutcome() != OutcomeType.NEVER) {
				p.updateStateToCompleted();
			}
		}
	}


	/**
	 * Generates the code for a kill command.
	 * @param processesToKill is a list of processes which are to be killed.
	 */
	public void internalDoKillProcess(String comment, ArrayList<StfProcess> processesToKill) throws StfException {
		// Verify that all of the processes are still running
		for (StfProcess p : processesToKill) {
			if (!p.isRunning()) {
				throw new StfException("Can't kill process '" + p.getMnemonic() + "' "
						+ "in test " + environmentCore.getSourceFileName() + " as it not running at this point");
			}
		}
		
		// Update known process state
		for (StfProcess p : processesToKill) {
			p.updateStateToKilled();
		}

		// Generate the code to monitor the processes
		generator.outputKillProcesses(processesToKill, "$rc = ");
		
		// Check the return code
		StfExitCodes expectedExitCode = StfExitCodes.expected(0);
		outputFailIfTrue("java", comment, "$rc", "!=", expectedExitCode);
	}

	
	public void verifyNoOrphanChildProcesses(String sourceFileName) throws StfException {
		for (StfProcess p : childProcesses) { 
			if (p.isRunning()) {
				throw new StfException("Possible orphan process in '" + environmentCore.getSourceFileName() + ".java'. "
						+ "The '" + p.getMnemonic() + "' process has neither completed monitoring or been killed");
			}
		}
	}
}
