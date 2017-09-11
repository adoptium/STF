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

package net.adoptopenjdk.stf.results;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import junit.framework.TestCase;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.results.ResultsFilter;
import net.adoptopenjdk.stf.results.ResultsParser;
import net.adoptopenjdk.stf.results.TestStatus;

/**
 * This class has test cases for verifying that the results processing code is 
 * working as expected.
 * 
 * i.e. given a list of test results and a list of filtering rules we verify that 
 * some of the failing tests are correctly recategorised as filtered passes.
 */
public class TestResultsProcessor extends TestCase {
	/**
	 * This test verifies that a run with all tests passing (and a failure rule) 
	 * reports that there were no test failures.
	 */
	@Test 
	public void testAllPassed() throws StfException {
		String resultsText =
			  "suite=maths platform=linux_86-64 result=pass class=ArgumentsTests test=test01 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=ArgumentsTests test=test02";

		String filterText = 
			  "suite=jdtuirefactoring platform=linux_86-64 result=fail class=ArgumentsTests test=testMem1";

		
		ArrayList<TestStatus> results = new ResultsParser(resultsText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);
		
		assertEquals(filter.toString(), 2, filter.getPasses().size());
		assertEquals(filter.toString(), 0, filter.getFilteredPasses().size());
		assertEquals(filter.toString(), 0, filter.getFailures().size());
	}


	/**
	 * This test verifies that a run with a mix of pass and fail results can have 
	 * some filters applied, so that there are no failure results remaining.  
	 */
	@Test 
	public void testFullyFiltered() throws StfException {
		String resultsText =
			  "suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test01 message=\"expected:<51.0> but was:<5.0>\"\n"
			+ "suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test02 exception=NullPointerException \n"
			+ "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem03";

		String filterText = 
			  "class=ArgumentsTests \n"
			+ "class=MemTests test=testMem01";

		ArrayList<TestStatus> results = new ResultsParser(resultsText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);
		
		verifyResults(filter.getPasses(), 
			"suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02",
			"suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem03");
		verifyResults(filter.getFilteredPasses(),
			"suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test01 message=\"expected:<51.0> but was:<5.0>\"",
			"suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test02 exception=NullPointerException",
			"suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01");
		assertEquals(filter.toString(), 0, filter.getFailures().size());
	}

	
	/**
	 * This test verifies that the filters file will be able to run with real world 
	 * formatting, such as comment lines, blank lines and extra spacing.  
	 */
	@Test 
	public void testExpectedWithFormatting() throws StfException {
		String resultsText =
			  "suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test01 message=\"expected:<51.0> but was:<5.0>\"\n"
			+ "suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test02 exception=NullPointerException \n"
			+ "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem03";

		String filterText = 
			  "class=ArgumentsTests \n"
			+ "\n"  // empty line
			+ "# This is a random comment \n"
			+ "\t    class=MemTests\t \t     test=testMem01       ";   // Note the extra white space

		ArrayList<TestStatus> results = new ResultsParser(resultsText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);
		
		verifyResults(filter.getPasses(), 
			"suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02",
			"suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem03");
		verifyResults(filter.getFilteredPasses(),
			"suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test01 message=\"expected:<51.0> but was:<5.0>\"",
			"suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test=test02 exception=NullPointerException",
			"suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01");
		assertEquals(filter.toString(), 0, filter.getFailures().size());
	}


	/**
	 * This test verifies that a run with a mix of pass and fail results can have 
	 * some filters applied, so that there are no failure results remaining.  
	 */
	@Test 
	public void testIntermittentFailure() throws StfException {
		String passingRunText =
			  "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem01 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02";

		String failingRunText =
			  "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01 \n"
			+ "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem02";

		String filterText = 
			  "class=MemTests test=testMem01";

		// Verify that the passing run has passed successfully
		ArrayList<TestStatus> results = new ResultsParser(passingRunText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);

		assertEquals(filter.toString(), 2, filter.getPasses().size());
		assertEquals(filter.toString(), 0, filter.getFilteredPasses().size());
		assertEquals(filter.toString(), 0, filter.getFailures().size());

		
		// Verify that the run with a failure also works
		results = new ResultsParser(failingRunText).parse();
		filter = new ResultsFilter(results, filters);

		assertEquals(filter.toString(), 1, filter.getPasses().size());
		assertEquals(filter.toString(), 1, filter.getFilteredPasses().size());
		assertEquals(filter.toString(), 0, filter.getFailures().size());
	}

	
	/**
	 * Processes some run results with all possible outcomes, including a faling test which 
	 * is not filtered out, leading to an actual failure.  
	 */
	@Test 
	public void testMandatoryFailure() throws StfException {
		String resultsText =
			  "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem01 \n"
			+ "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem02 \n"
			+ "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem03";

		String filterText = "test=testMem02";

		ArrayList<TestStatus> results = new ResultsParser(resultsText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);
		
		verifyResults(filter.getPasses(),         "suite=maths platform=linux_86-64 result=pass class=MemTests test=testMem01");
		verifyResults(filter.getFilteredPasses(), "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem02");
		verifyResults(filter.getFailures(),       "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem03");
	}

	
	/**
	 * Verify that filter criteria can match on regular expressions
	 */
	@Test
	public void testRegexMatching() throws StfException {
		String resultsText =
			  "suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01 \n"  // match on the platform name
			+ "suite=maths platform=win_x86-64  result=fail class=MemTests test=testMem02 \n"  // match on the '0' in the test name 
			+ "suite=stuff platform=win_x86-64  result=fail class=MemTests test=0testCpu \n"      // also match on the 0 in the test name
			+ "suite=stuff platform=win_x86-64  result=fail class=MemTests test=testGraphics \n"  // really fails. doesn't match any regex
			+ "suite=stuff platform=linux_86-64 result=pass class=MemTests test=testCpu02";       // clear pass

		String filterText = 
				  "platform=~linux_86-(32|64)\n"
				+ "test=~.*0.*";

		// match with regex for platform name
		ArrayList<TestStatus> results = new ResultsParser(resultsText).parse();
		ArrayList<TestStatus> filters = new ResultsParser(filterText).parse();
		ResultsFilter filter = new ResultsFilter(results, filters);

		assertEquals(filter.toString(), 1, filter.getPasses().size());
		assertEquals(filter.toString(), 3, filter.getFilteredPasses().size());
		assertEquals(filter.toString(), 1, filter.getFailures().size());
		
		verifyResults(filter.getPasses(),         "suite=stuff platform=linux_86-64 result=pass class=MemTests test=testCpu02");
		verifyResults(filter.getFilteredPasses(), 
				"suite=maths platform=linux_86-64 result=fail class=MemTests test=testMem01",
				"suite=maths platform=win_x86-64  result=fail class=MemTests test=testMem02",
				"suite=stuff platform=win_x86-64  result=fail class=MemTests test=0testCpu");
		verifyResults(filter.getFailures(),       "suite=stuff platform=win_x86-64  result=fail class=MemTests test=testGraphics");
	}
	
	private void verifyResults(ArrayList<TestStatus> actual, String ... expected) {
		String actualStr = Arrays.toString(actual.toArray());
		
		StringBuilder expectedBuilder = new StringBuilder();
		expectedBuilder.append("[");
		for (int i=0; i<expected.length; i++) {
			expectedBuilder.append(expected[i].toString());
			if (i < expected.length-1) {
				expectedBuilder.append(", ");
			}
		}
		expectedBuilder.append("]");
				
		checkStrings(expectedBuilder.toString(), actualStr);
		assertEquals(expectedBuilder.toString(), actualStr);
	}


	// This method helps with debugging by making string differences more readable.
	private void checkStrings(String expected, String actual) {
		if (expected.equals(actual)) { 
			return;
		}
	
		int firstDiff = Integer.MAX_VALUE;
		StringBuilder statusLine = new StringBuilder();
		
		int max = Math.max(expected.length(), actual.length());
		for (int i=0; i<max; i++) {
			char e = i < expected.length() ? expected.charAt(i) : ' ';
			char a = i < actual.length() ? actual.charAt(i) : ' ';
			
			if (e == a) {
				statusLine.append('.');
			} else { 
				statusLine.append('*');
				firstDiff = Math.min(firstDiff, i);
			}
		}
		
		// Report on the differences between the string.
		System.out.println("Differences detected. First diff at offset " + firstDiff);
		System.out.println("Expected:" + expected);
		System.out.println("Actual:  " + actual);
		System.out.println("Status:  " + statusLine.toString());
		
		assertEquals(expected, actual); // Will cause the test to fail
	}
}