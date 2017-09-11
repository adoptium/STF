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

import junit.framework.TestCase;


/** 
 * This simple junit test will deliberately fail on the 3rd to 10th invocation.
 * Expected behaviour:
 *   1st invocation - all pass.
 *   2nd invocation - all pass.
 *   3rd invocation - testPi fails.
 *   4th invocation - all pass.
 *   5th to 9th invocation - testAdding fails.
 *   10th and subsequent - all pass.
 */
public class FailingJUnitTest extends TestCase {
	private static int testPiCount = 0;
	private static int testAddingCount = 0;
	
	public void testPi() {
		testPiCount++;
		if (testPiCount == 3) {
			assertEquals("Wrong value of pi", 3.0d, Math.PI);
		}
	}
	
	public void testAdding() {
		testAddingCount++;
		if (testAddingCount >= 5 && testAddingCount <=9) {
			assertEquals("Addition failed", 5, 2+2);
		}
	}
}