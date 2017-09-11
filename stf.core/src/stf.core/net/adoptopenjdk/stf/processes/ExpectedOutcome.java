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
import net.adoptopenjdk.stf.StfExitCodes;
import net.adoptopenjdk.stf.util.StfDuration;
import net.adoptopenjdk.stf.util.TimeParser;


/**
 * This class holds the expected outcome of running a process. 
 * 
 * The test run is deemed successful if the process behaves as expected. 
 * i.e. if a process is expected to crash, and it does then crash, then the test 
 * run is a success. However, if the process were to complete with an exit 
 * code of '0' then it has not met its expectations and would be treated as a 
 * failure.
 * 
 */
public class ExpectedOutcome {
	public enum OutcomeType {
		CLEAN_RUN,
		NON_ZERO_EXIT,
		NEVER,
		CRASHES
	};
	
	private final OutcomeType expectedOutcome;
	private final StfExitCodes expectedExitValue;
	private final StfDuration durationLimit;
	
	
	// Private to force the use of the static methods.
	private ExpectedOutcome(OutcomeType expectedOutcome, StfExitCodes expectedExitValue, StfDuration duration) {
		this.expectedOutcome = expectedOutcome;
		this.expectedExitValue = expectedExitValue;
		this.durationLimit = duration;
	}

	
	/**
	 * @return An ExcepectedOutcome object for the case in which the process is expected 
	 * to finish successfully with an exit value of '0'.
	 */
	public static ExpectedOutcome cleanRun() {
		return new ExpectedOutcome(OutcomeType.CLEAN_RUN, StfExitCodes.expected(0), null);
	}
	
	
	/**
	 * Creates an ExpectedOutcome object for the case in which the process exits with 
	 * a non-zero value.  
	 * @param expectedExitValue is the value which the process will be using on exit.
	 * This is normally a single value but in some circumstances more a process may exit 
	 * with another value. 
	 */
	public static ExpectedOutcome exitValue(Integer... expectedExitValue) {
		return new ExpectedOutcome(OutcomeType.NON_ZERO_EXIT, StfExitCodes.expected(expectedExitValue), null);
	}

	
	/**
	 * @return an ExpectedOutcome object for a process which runs indefinitely, and should 
	 * neither exit or crash.
	 */
	public static ExpectedOutcome neverCompletes() {
		return new ExpectedOutcome(OutcomeType.NEVER, StfExitCodes.expected(-1), StfDuration.ofDays(2));
	}

	
	/**
	 * @return an ExpectedOutcome object for a process which is going to crash.
	 */
	public static ExpectedOutcome crashes() {
		return new ExpectedOutcome(OutcomeType.CRASHES, StfExitCodes.expected(-1), null);
	}


	/**
	 * Allows the setting of the maximum run time for a process.
	 * If the process has not completed within the specified time then it will 
	 * be killed and the test run will have failed.
	 * @param timeSpecification is a string containing the hours, minutes and seconds
	 * time limit. eg, '1h30m' or '15s'. 
	 * @return an ExpectedOutcome object which has the maximum run time 
	 * @throws StfException if the timeSpecification string is not in the expected format.
	 */
	public ExpectedOutcome within(String timeSpecification) throws StfException {
		// Build duration of the specified value
		StfDuration duration = TimeParser.parseTimeSpecification(timeSpecification);
		return new ExpectedOutcome(expectedOutcome, expectedExitValue, duration);
	}

	
	public OutcomeType getExpectedOutcome() {
		return expectedOutcome;
	}
	
	
	/**
	 * @return a String containing the expected exit values for a child process.
	 * eg, '0' for successful run, or '2,3,4' if multiple values are allowed.
	 */
	public String getExpectedExitValue() {
		StringBuilder exitCodeSpec = new StringBuilder();
		for (int exitCode : expectedExitValue.getAllowableExitCodes()) { 
			if (exitCodeSpec.length() > 0) {
				exitCodeSpec.append(",");
			}
			exitCodeSpec.append(exitCode);
		}
		
		return exitCodeSpec.toString();
	}
	
		
	public StfDuration getDurationLimit() {
		return durationLimit;
	}
	
	
	public String toString() { 
		StringBuilder buff = new StringBuilder();
		
		buff.append(expectedOutcome.toString());
		
		if (expectedOutcome == OutcomeType.NON_ZERO_EXIT) {
			buff.append(" " + expectedExitValue);
		}
		
		if (durationLimit != null && expectedOutcome != OutcomeType.NEVER) { 
			buff.append(" within " + durationLimit);
		}
		
		return buff.toString();
	}
}