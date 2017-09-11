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

package net.adoptopenjdk.stf.processes.definitions;

import java.util.ArrayList;
import java.util.HashMap;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.processes.StfProcess;


/**
 * This object captures the information needed to start a system process.
 * 
 * This is a powerful command which has potential to produce fragile 
 * tests which are system dependent.
 * Use only after consideration about other options. 
 */
public class SystemProcessDefinition implements ProcessDefinition {
	private String command; 
	
	private ArrayList<String> args = new ArrayList<String>();


	public static SystemProcessDefinition create() {
		return new SystemProcessDefinition();
	}
	
	public boolean isJdkProgram() {
		return false;  // The command is on the system path 
 	}

	@Override
	public JavaVersion getJavaVersion() {
		return null;
	}

	/**
	 * @param command sets the name of the program to execute.
	 */
	public SystemProcessDefinition setProcessName(String command) {
		this.command = command;
		return this;
	}

	/**
	 * Adds a argument value to the programs invocation.
	 * @param arg is an argument to be passed to the program.
	 * @return the updated SystemProcessDefinition.
	 */
	public SystemProcessDefinition addArg(String arg) {
		this.args.add(arg);		
		
		return this;
	}


	/**
	 * Adds multiple argument values to the programs invocation.
	 * @param arg is one or more arguments to be passed to the program.
	 * @return the updated SystemProcessDefinition.
	 */
	public SystemProcessDefinition addArg(String... args) {
		for (String arg : args) { 
			addArg(arg);
		}
		
		return this;
	}


	/**
	 * @return the name of the program to execute.
	 */
	public String getCommand() {
		return command;
	}
	
	/**
	 * Returns a HashMap containing links to all processes that have been identified as related to this process.
	 * @return A HashMap where each key is a unique String, and each StfProcess is a process related to this process.
	 */
	public HashMap<String, StfProcess> getRelatedProcesses() {
		return new HashMap<String, StfProcess>();		
	}
	
	/**
	 * Returns a HashMap containing a list of data that we want to get from all processes in the relatedProcesses HashMap.
	 * @return A HashMap where each key is a unique String, and each Integer is a PERL_PROCESS_DATA key linked to a specific 
	 * 		   operation that can be performed after appending the perl variable representing a specific process.
	 */
	public HashMap<String, Integer> getRelatedProcessesData() {
		return new HashMap<String, Integer>();
	}
	
	/**
	 * Returns all of the arguments needed to run the program.
	 * @return an array list of Strings containing the arguments.
	 * @throws StfException if the program name has not been specified.
	 */
	public ArrayList<String> asArgsArray() throws StfException {
		ArrayList<String> allArgs = new ArrayList<String>();
		
		if (command == null) {
			throw new StfException("Can't run as program name has not been specified");
		}
		
		allArgs.addAll(args);
		
		return allArgs;
	}
	

	
	@Override
	public void generationCompleted(int commandSerialNum, String processMnemonic) {
	}
}