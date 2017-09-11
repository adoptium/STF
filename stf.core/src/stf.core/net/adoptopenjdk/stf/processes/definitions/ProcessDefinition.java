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

public interface ProcessDefinition {
	/**
	 * @return the full path and name of the program to be executed. eg, '/opt/java/bin/java' or '/opt/java/bin/keytool', etc.
	 * @throws StfException 
	 */
	public String getCommand() throws StfException;

	/**
	 * Returns a HashMap containing links to all processes that have been identified as related to this process.
	 * @return A HashMap where each key is a unique String, and each StfProcess is a process related to this process.
	 */
	public HashMap<String, StfProcess> getRelatedProcesses();
	
	/**
	 * Returns a HashMap containing a list of data that we want to get from all processes in the relatedProcesses HashMap.
	 * @return A HashMap where each key is a unique String, and each Integer is a PERL_PROCESS_DATA key linked to a specific 
	 * 		   operation that can be performed after appending the perl variable representing a specific process.
	 */
	public HashMap<String, Integer> getRelatedProcessesData();
	
	/**
	 * When STF needs to run a process this method is called to produce
	 * the list of arguments needed to run the process.
	 * @return an array list of Strings with the values needed to run the process.
	 * @throws StfException if the implementation detects an error.
	 */
	public ArrayList<String> asArgsArray() throws StfException;

	/** 
	 * @return true if the specified program lives in the current jdk bin directory.
	 */
	public boolean isJdkProgram();
	
	/**
	 * If the process definition is going to be running Java then this method gets
	 * an object to represent the JVM to run.
	 * @return jvm details or null if the process is not running Java.
	 */
	public JavaVersion getJavaVersion();

	/**
	 * Called when code generation has completed.
	 * Allows the process definition object to do any necessary house keeping.
	 * @param commandSerialNum is the number assigned to the newly generated step.
	 * @param processMnemonic is the mnemonic used for the current invocation.
	 * @throws StfException 
	 */
	public void generationCompleted(int commandSerialNum, String processMnemonic) throws StfException;
}
