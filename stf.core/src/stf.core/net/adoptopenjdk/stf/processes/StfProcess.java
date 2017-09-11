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

package net.adoptopenjdk.stf.processes;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.FileRef;


/** 
 * This class holds information about a process which will be controlled by 
 * the perl process management layer.
 */
public class StfProcess {
	// Holds the mnemonic name of the process, as used by the test case
	private String mnemonic;
	
	// Holds the variable name used by the perl code to refer to this process
	private String perlProcessVariable;
	
	// Contains the test cases expectation for the process. Also holds its maximum runtime.
	private ExpectedOutcome expectedOutcome;
	
	// Track the currently known process state. 
	// Used for error checking at code generation time, to spot problems with automation code.
	private enum ProcessState { RUNNING, COMPLETED, KILLED };
	private ProcessState knownState;
	
	private FileRef stderr;
	private FileRef stdout;
	
	
	public StfProcess(String mnemonic, String perlProcessVariable, ExpectedOutcome expectedOutcome, FileRef stderr, FileRef stdout) throws StfException {
		this.mnemonic = mnemonic;
		this.perlProcessVariable = perlProcessVariable;
		this.expectedOutcome = expectedOutcome;
		this.knownState = ProcessState.RUNNING;
		
		this.stderr = stderr;
		this.stdout = stdout;
	}

	
	public String getPerlProcessVariable() { 
		return perlProcessVariable;
	}
	
	public FileRef getStderrFileRef(){
		return stderr;
	}
	
	
	public FileRef getStdoutFileRef(){
		return stdout;
	}

	public String getMnemonic() {
		return mnemonic;
	}

	public void updateStateToCompleted() {
		this.knownState = ProcessState.COMPLETED;
	}

	public void updateStateToKilled() {
		this.knownState = ProcessState.KILLED;
	}

	public boolean isRunning() {
		return knownState == ProcessState.RUNNING;
	}

	public boolean processHasCompleted() {
		return knownState == ProcessState.COMPLETED;
	}

	public boolean processHasBeenKilled() {
		return knownState == ProcessState.KILLED;
	}

	public ExpectedOutcome getExpectedCompletion() {
		return expectedOutcome;
	}
	
	public String toString() {
		return mnemonic;
	}
}