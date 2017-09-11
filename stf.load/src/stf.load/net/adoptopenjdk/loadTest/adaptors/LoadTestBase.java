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
import java.math.RoundingMode;


/**
 * This is a base class for all types of test that are runnable by the STF Load test.
 * 
 * It holds attributes which are common to all tests:
 *   - their name
 *   - the test number 
 *   - the test weighting (defaults to 1)
 */
public abstract class LoadTestBase implements AdaptorInterface {
	private final int testNum;
	private final String testName;
	private final String testMethodName;
	private final BigDecimal weighting;

	public LoadTestBase(int testNum, String testName, String testMethodName, BigDecimal weighting) {
		this.testNum = testNum;
		this.testName = testName;
		this.testMethodName = testMethodName;
		this.weighting = weighting;
	}

	@Override
	public int getTestNum() {
		return testNum;
	}
	
	@Override
	public String getTestName() {
		return testName;
	}
	
	@Override
	public String getTestMethodName() {
		if (testMethodName == null) { 
			return "";
		}
		return testMethodName;
	}
	
	@Override
	public BigDecimal getWeighting() {
		return weighting;
	}

	/**
	 * Return the weighting for this test once multiplied by a weighting multiplier.
	 * @param weightingMultiplier holds the multiplier needed to balance out the weightings.
	 * @return The result of weighting * weightingMultipier. 
	 */
	public BigDecimal getAdjustedWeighting(BigDecimal weightingMultiplier) {
		return weighting.multiply(weightingMultiplier);
	}

	/**
	 * Returns the rounded and adjusted weighting. 
	 * For example a weighting of 1.3 and a multiplier of 3 results in a value of 3.9, which is 
	 * rounded to 4.
	 * @param weightingMultiplier holds the multiplier needed to balance out the weightings.
	 * @return Rounded adjusted weighting. Values less than 1 are rounded up to 1.
	 */
	public int getRoundedAdjustedWeighting(BigDecimal weightingMultiplier) {
		BigDecimal adjustedWeighting = getAdjustedWeighting(weightingMultiplier);
		BigDecimal roundedWeighting = adjustedWeighting.setScale(0, RoundingMode.HALF_UP);
		roundedWeighting = roundedWeighting.max(BigDecimal.ONE);
		return roundedWeighting.intValue();
	}

	/**
	 * Subclasses must implement equals() and hashcode() so that InventoryData can
	 * use ArrayList and Set manipulation (eg, ArrayList.removeAll) on collections of tests. 
	 */
	public abstract boolean equals(Object other);
	public abstract int hashCode();
	
	/**
	 * Subclasses must implement toString() so that tests can be listed and described.
	 */
	public abstract String toString();
}