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

package net.adoptopenjdk.stf.processes.definitions.generic;

import java.util.ArrayList;
import java.util.Arrays;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;


/**
 * This class manages a collection of 1 or more ProcessArg objects.
 * 
 * It aims to hold functionality which is common to process definition objects:
 *   - Enforcing the ordering in which process definition arguments can be specified.
 *   - Building an ArrayList of strings needed to run the process. 
 */
public class ProcessArgCollection {
	// To enforce correct buildup of invocation arguments, all the addition 
	// methods fall into one of these categories.
	public static class Stage {
		String name;
		int level;
		int minimumJVM;
		public Stage(String stageName, int level, int minimumJVM) {
			this.name = stageName;
			this.level = level;
			this.minimumJVM = minimumJVM;
		}
	}

	
	private StfEnvironmentCore environmentCore;

	// Holds information about the last argument which has been set.
	// Allows for the detection of out of order setting.
	private Stage oldStage;

	// Contains the arguments for the process.
	// Held in the order in which they will be used.
	private ArrayList<ProcessArg> args;
	
	
	/**
	 * Create a ProcessArgCollection
	 * @param environmentCore references StfEnvironmentCore.
	 * @param initialStage initialises the stage ordering.
	 * @param args contains all of the arguments which can be used by the current process.
	 */
	public ProcessArgCollection(StfEnvironmentCore environmentCore, Stage initialStage, ProcessArg... args) {
		this.environmentCore = environmentCore;
		this.oldStage = initialStage;
		this.args = new ArrayList<ProcessArg>(Arrays.asList(args));
	}

	
	// Verifies that the addition method is not being called at the wrong time.
	// eg. throws exception if attempting to add to the classpath if the last
	// call was adding an application argument.
	public void checkAndUpdateLevel(Stage newStage) throws StfException {
		// Make sure calls not made out of sequence
		if (newStage.level < oldStage.level) {
			throw new StfException("Java invocation built out of sequence. "
					+ "Arguments for stage '" + newStage.name + "' cannot be set after stage '" + oldStage.name + "'");
		}
		
		// Make sure the JVM supports the feature that is being specified
		int javaVersion = environmentCore.getJavaVersion();
		int requiredJavaVersion = newStage.minimumJVM;
		if (javaVersion < requiredJavaVersion) {
			throw new StfException("Target JVM too old. "
					+ "Java module arguments are only available from java 9 onwards. "
					+ "Current JVM version: " + javaVersion + ", but need minimum of version: " + requiredJavaVersion + " for: " + newStage);
		}
		
		oldStage = newStage;
	}


	/**
	 * Convert the current argument objects into strings which can be used to invoke the process.
	 * @return ArrayList of Strings containing the arguments for the process.
	 */
	public ArrayList<String> asArgsArray() throws StfException {
		ArrayList<String> argStrings = new ArrayList<String>();
		
		for (ProcessArg arg : args) {
			ArrayList<String> argAsString = arg.asString();
			// Add in all created arguments (argAsString may be empty, but that is ok)
			argStrings.addAll(argAsString);
		}
		
		return argStrings;
	}
}