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

package net.adoptopenjdk.loadTest;


/**
 * STF load tests prevents test code from terminating a process if some of the test
 * code calls System.exit().
 * 
 * Attempts to exit the process are blocked by a custom security manager. This exception
 * is thrown to reject the exit request.
 * 
 * An attempt to exit with non-zero value is used to indicate that the test failed. 
 */
public class BlockedExitException extends SecurityException {
	private static final long serialVersionUID = 98234872344L;
	
	private int exitValue;
	
	public BlockedExitException(int exitValue) {
		super();
		this.exitValue = exitValue;
	}

	public int getExitValue() {
		return exitValue;
	}
}
