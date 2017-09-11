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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import net.adoptopenjdk.loadTest.FirstFailureDumper;
import net.adoptopenjdk.loadTest.reporting.ExecutionTracker;


public class MauveAdaptor extends LoadTestBase {
	private final String fullMauveClass;  // eg, gnu.testlet.java.lang.InheritableThreadLocal.simple

	// Define the strings that we'll see in the output which tell us
	// if a Mauve test has passed or failed
	private static byte[] PASS_INDICATOR = "PASS:".getBytes();
	private static byte[] FAIL_INDICATOR = "FAIL:".getBytes();
	

	public MauveAdaptor(int testNum, String fullClassname, BigDecimal weighting) {
		super(testNum, fullClassname, null, weighting);
		
		this.fullMauveClass = fullClassname;
	}


	// The Mauve code is not directly accessible so needs to be run using reflection.
	// This method is basically doing the following:
	//    Testlet testlet = new <fullMauveClass>();
	//    SingleTestHarness harness = new SingleTestHarness(testlet, true);
	//    testlet.test(harness);
	@Override
	public ResultStatus executeTest() throws Throwable {
		// Create an instance of the class under test
		Class<?> testletClass1 = Class.forName(fullMauveClass);
		Object testlet = testletClass1.newInstance();

		// Find the SingleTestHarness constructor. ie. 'public SingleTestHarness(Testlet t, boolean verbose)'
		Constructor<?> singleConstructor = null;
		Class<?> singleTestHarnessClass = Class.forName("gnu.testlet.SingleTestHarness");
		Class<?> gnuTestletClass = Class.forName("gnu.testlet.Testlet");
		singleConstructor = singleTestHarnessClass.getConstructor(gnuTestletClass, Boolean.TYPE);
		
		// Create an instance of SingleTestHarness
		// Equivalent to: 'new SingleTestHarness(testlet, true)'
		Object testHarness = null;
		Object args[] = { testlet, Boolean.TRUE };
		testHarness = singleConstructor.newInstance(args);

		// Find the test method
		Method testMethod = null;
		Class<?> testHarnessClass = Class.forName("gnu.testlet.TestHarness");
		testMethod = testletClass1.getDeclaredMethod("test", testHarnessClass);

		// Invoke the test method - call 'public void test (TestHarness harness)'
		try {
			Object[] testMethodArgs = { testHarness };
			testMethod.invoke(testlet, testMethodArgs);
		} catch (InvocationTargetException e) {
			// Test code caused Throwable or Exception. Let the caller handle the root cause.
			throw e.getCause();
		}
		
		// Result of Mauve test can only be discovered by parsing its output
		return ExecutionTracker.instance().getFinalResult();
	}

	
	/**
	 * Examines a line of Mauve test output to see if it starts with 'PASS:' or 'FAIL:'
	 * 
	 * @param output is the bytes of a string being output by a Mauve test.
	 * @param off is the offset to the start of the data.
	 * @param len is the length of the data in the output buffer.
	 * @returns Pass/Fail if the line contains the special Mauve output, otherwise NO_MATCH.
	 */
	public ResultStatus checkTestOutput(byte[] output, int off, int len) {
		ResultStatus resultStatus = ResultStatus.UNKNOWN;
		
		if (startsWith(output, off, len, PASS_INDICATOR)) {
			resultStatus = ResultStatus.PASS;
		} else if (startsWith(output, off, len, FAIL_INDICATOR)) {
			FirstFailureDumper.instance().createDumpIfFirstFailure(this);
			resultStatus = ResultStatus.FAIL;
		}
		
		return resultStatus;
	}

	
	/**
	 * Examines the data in the output buffer and compares it for equality with the target data.
	 * Returns true if the output starts with same data as the target, otherwise false.
	 */
	private boolean startsWith(byte[] output, int off, int len, byte[] target) {
		if (len <= target.length) {
			return false;
		}
		
		for (int i=0; i<target.length; i++) {
			if (output[off+i] != target[i]) {
				return false;
			}
		}
		
		return true;
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!(o instanceof MauveAdaptor)) {
			return false;
		}
		MauveAdaptor mauve = (MauveAdaptor) o;
		return fullMauveClass.equals(mauve.fullMauveClass);
	}
	
	public int hashCode() { 
		return fullMauveClass.hashCode();
	}

	public String toString() { 
		return "Mauve[" + fullMauveClass + "]";
	}
}