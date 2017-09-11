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
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.processes.StfProcess;


/**
 * This object captures the information needed to start a JDK tool or utility.
 * 
 * See SampleRunJDKTool.java for a runnable example.
 */
public class JDKToolProcessDefinition implements ProcessDefinition {
	private StfEnvironmentCore environmentCore;
	private String toolOrUtilityName; 
	
	private ArrayList<String> args = new ArrayList<String>();


	public JDKToolProcessDefinition(StfEnvironmentCore environmentCore) {
		this.environmentCore = environmentCore;
	}
	
	public boolean isJdkProgram() {
		return true;
	}
	
	@Override
	public JavaVersion getJavaVersion() {
		return null;
	}

	/**
	 * @param toolOrUtilityName sets the name of the JDK tool or utility to execute.
	 */
	public JDKToolProcessDefinition setJDKToolOrUtility(String toolOrUtilityName) {
		this.toolOrUtilityName = toolOrUtilityName;
		return this;
	}

	/**
	 * Adds a argument value to the tool's invocation.
	 * @param arg is an argument to be passed to the JDK tool.
	 * @return the updated JDKToolProcessDefinition.
	 */
	public JDKToolProcessDefinition addArg(String arg) {
		this.args.add(arg);		
		
		return this;
	}


	/**
	 * Adds multiple argument values to the tool's invocation.
	 * @param arg is one or more arguments to be passed to the JDK tool.
	 * @return the updated JDKToolProcessDefinition.
	 */
	public JDKToolProcessDefinition addArg(String... args) {
		for (String arg : args) { 
			addArg(arg);
		}
		
		return this;
	}

	
	/**
	 * Convenience method for adding a FileRef object as an argument
	 * @param fileRef is the object to add as an argument.
	 * @return the updated JDKToolProcessDefinition
	 */
	public JDKToolProcessDefinition addArg(FileRef fileRef) {
		return addArg(fileRef.getSpec());
	}


	/**
	 * @return the name of the JDK tool or utility to execute.
	 * @throws StfException 
	 */
	public String getCommand() throws StfException {
		// Create the command using the full path to Java or the JDK tool
		return environmentCore.getJavaHome().childFile("bin/" + toolOrUtilityName).getSpec();
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
	 * Returns all of the arguments needed to run the JDK tool or utility.
	 * @return an array list of Strings containing the arguments.
	 * @throws StfException if the JDK tool/utility has not been specified.
	 */
	public ArrayList<String> asArgsArray() throws StfException {
		ArrayList<String> allArgs = new ArrayList<String>();
		
		if (toolOrUtilityName == null) {
			throw new StfException("Can't run as Java class has not been specified (method setJDKToolOrUtility() needs to be called");
		}
		
		allArgs.addAll(args);
		
		return allArgs;
	}

	
	@Override
	public void generationCompleted(int commandSerialNum, String processMnemonic) {
	}
}