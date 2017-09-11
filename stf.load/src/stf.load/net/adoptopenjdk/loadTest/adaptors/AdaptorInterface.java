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

package net.adoptopenjdk.loadTest.adaptors;

import java.math.BigDecimal;

/**
 * Key interface for allowing the load test to run any test code.
 * 
 * To support a new test type:
 *   1) Add a new class which implements this interface.
 *   2) Add parsing support into InventoryData.java
 *   3) Make sure that the new subclass implements equals() and hashcode(). 
 *      Test exclusion will not work correctly without these methods.
 */
public interface AdaptorInterface {
	/**
	 * @return The name of the test case which the implementation will run.
	 */
	public String getTestName();
	
	/**
	 * For Arbitrary java we run a single named test method.
	 * @return Name of test method to run.
	 */
	public String getTestMethodName();
	
	/**
	 * Get the number of this test. 
	 * The numbers are allocated by InventoryData.java and passed to the adaptors constructor.
	 * @return an int with the number of the test.
	 */
	public int getTestNum();
	
	/**
	 * Gets the weighting value for the test. When running with random test selection 
	 * the likelihood of selecting a test can be partially controlled by adjusting its 
	 * weighting. 
	 * If not specified then each test has a default weighting of 1.
	 * Tests with a positive whole number are more likely to be selected. So a weighting 
	 * of 3 means that the test is 3* more likely to be executed.
	 * A fractional value makes it less likely that a test will run. A value of say 0.25
	 * means that on average a test would run only a quarter as frequently as it would 
	 * with a weighting of 1. 
	 * Each test In order to allow balance  
	 * @return BigDecimal with the weighting value for the test.
	 */
	public BigDecimal getWeighting();
	
	/**
	 * @return The result of weighting * weightingMultipier. 
	 */
	public BigDecimal getAdjustedWeighting(BigDecimal weightingMultiplier);

	/**
	 * @return Rounded adjusted weighting. Values less than 1 are rounded up to 1.
	 */
	public int getRoundedAdjustedWeighting(BigDecimal weightingMultiplier);

	
	// Results are ordered from lowest to highest importance.
	// A test observation of higher importance will override one of lower importance. 
	public enum ResultStatus {
		UNKNOWN(true),            // No failure detected, but also no positive confirmation of passing.
		PASS(true),               // The test passed.
		BLOCKED_EXIT_PASS(true),  // Test attempted to call System.exit() with a zero exit value.
		FAIL(false),              // Test indicated a failure, or ended in exception.
		BLOCKED_EXIT_FAIL(false); // Test attempted to call System.exit() with non-zero value (indicating failure) 
		
		private final boolean passed;
		
		private ResultStatus(boolean passed) {
			this.passed = passed;
		}		
		public boolean testPassed() {
			return passed;
		}
	};

	/**
	 * Runs the test code.
	 * The test is marked as a failure if it throws an Exception or Throwable.
	 * Test output can be checked with the checkTestOutput() method. 
	 * 
	 * @returns A ResultStatus value to indicate the success/failure of the test.
	 * @throws Throwable or Exception on failure.
	 */
	public ResultStatus executeTest() throws Throwable;
	
	/**
	 * The load test intercepts the stdout and stderr for the test.
	 * This method gives the adaptor the chance to examine the output and fail the test.
	 * One call is made to this method for each piece of intercepted output.
	 * Return results of say, 'pass, pass, fail, pass' means that the test has failed.
	 * 
	 * @return ResultStatus value. Set to NO_MATCH if you can't tell the pass/fail
	 * status from the output. 
	 */
	public ResultStatus checkTestOutput(byte[] output, int off, int len);
}