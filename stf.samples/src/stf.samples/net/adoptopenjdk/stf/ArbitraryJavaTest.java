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

public class ArbitraryJavaTest {
	private enum OperationType { ADD, SUBTRACT, MULTIPLY, DIVIDE };
	private OperationType operation;
	
	public ArbitraryJavaTest() {
	}
	
	public ArbitraryJavaTest(String operator) {
		operation = OperationType.valueOf(operator);
	}
	
	public void runSimpleTest() {
		// Run a test that should never fail
		if (System.currentTimeMillis() < 666) { 
			throw new IllegalStateException("System clock wrong");
		}
	}
	
	public void runTest(int expectedResult, int v1, int v2) {
		int actualResult = 0;
		
		switch (operation) {
		case ADD:
			actualResult = v1 + v2;
			break;
		case SUBTRACT:
			actualResult = v1 - v2;
			break;
		case MULTIPLY:
			actualResult = v1 * v2;
			break;
		case DIVIDE:
			actualResult = v1 / v2;
			break;
		}
		
		if (expectedResult != actualResult) {
			throw new IllegalStateException("Math operation failed. Expected=" + expectedResult + " but actual=" + actualResult
					+ " for " + v1 + " " + operation.name() + " " + v2);
		}
	}	
}