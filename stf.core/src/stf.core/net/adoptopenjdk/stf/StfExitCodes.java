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

package net.adoptopenjdk.stf;

import java.util.Arrays;
import java.util.List;


/**
 * This class holds a set of allowable exit codes.
 * 
 * It is used by test plugins to handle special cases in which a passing process 
 * may return with more than usual '0' exit code.
 */
public class StfExitCodes {
	// This holds the allowable exit codes
	private List<Integer> exitCodes;

	
	private StfExitCodes() {
	}
	
	
	/**
	 * Create a StfExitCodes object from a var-args list of allowable exit codes.
	 * 
	 * @param exitCodes contains 1 or more expected exit codes.
	 * @return a StfExitCodes object containing the supplied exit codes.
	 */
	public static StfExitCodes expected(Integer... exitCodes) {
		StfExitCodes exitCodesObj = new StfExitCodes();
		exitCodesObj.exitCodes = Arrays.asList(exitCodes);
		
		return exitCodesObj;
	}
	
	
	/**
	 * @return an ArrayList containing the allowable exit codes.
	 */
	public List<Integer> getAllowableExitCodes() { 
		return exitCodes;
	}
	
	
	public String toString() { 
		return exitCodes.toString();
	}
}
