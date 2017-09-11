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

import net.adoptopenjdk.stf.StfException;


/**
 * This class holds the results of running a single test. 
 * 
 * Results filtering also uses the same fields so this object is also used to 
 * represent a results filtering rule.
 */
public class TestStatus {
	// This inner class holds the name and value for a single field. eg testName = testMemory01
	public static class FieldDetails {
		public String name;
		public boolean isRegexComparison;
		public String value;
		
		public FieldDetails(String name, boolean isRegexComparison, String value) {
			this.name = name;
			this.isRegexComparison = isRegexComparison;
			this.value = value;
		}
	}

	private String originalText;
	
	private FieldDetails suiteName;
	private FieldDetails platform;
	private FieldDetails result;
	private FieldDetails testClass;
	private FieldDetails testName;
	private FieldDetails failureMessage;
	private FieldDetails exception;
	private FieldDetails expectation;
	

	public TestStatus(String originalText, 
			FieldDetails suiteName, FieldDetails platform, FieldDetails result, FieldDetails testClass, FieldDetails testName, 
			FieldDetails failureMessage, FieldDetails exception, FieldDetails expectation) throws StfException {
		this.originalText = originalText.trim();
		
		this.suiteName = suiteName;
		this.platform = platform;
		this.result = result;
		this.testClass = testClass;
		this.testName = testName;
		this.failureMessage = failureMessage;
		this.exception = exception;
		this.expectation = expectation;
		
		if (result != null && !(result.value.equals("pass") || result.value.equals("fail") || result.value.equals("ignored"))) {
			throw new StfException("Invalid value for 'result' field: " + result.value);
		}
	}


	public boolean passed() {
		return result.value.equals("pass");
	}
	
	
	public boolean ignored() {
		return result.value.equals("ignored");
	}


	/**
	 * Returns true if the current TestStatus object matches another TestStatus.
	 * They are only considered matching if all fields in the current object match 
	 * the corresponding field in the other object. 
	 * 
	 * @param other is the object to compare the current object against.
	 * @return true if the current object matches the other object.
	 * @throws StfException 
	 */
	public boolean matches(TestStatus other) throws StfException {
		boolean matched = match(suiteName, other.suiteName) 
				&& match(this.platform, other.platform) 
				&& match(this.result, other.result)
				&& match(this.testClass, other.testClass)
				&& match(this.testName, other.testName)
				&& match(this.failureMessage, other.failureMessage)
				&& match(this.exception, other.exception)
				&& match(this.expectation, other.expectation);
		
		return matched;
	}
	
	
	private boolean match(FieldDetails ruleValue, FieldDetails otherValue) throws StfException {
		// There is no field value for the current object so treat it as matching
		if (ruleValue == null) {
			return true;
		}
		
		if (otherValue == null) { 
			throw new StfException("Unable to match field '" + ruleValue.name + "' as no value in results data");
		}
		
		String otherValueString = otherValue.value;
		String ruleValueString  = ruleValue.value;

		if (ruleValue.isRegexComparison) {
			// Decide if the other string matches the regex spec
			return otherValueString.matches(ruleValueString);
		} else {
			// Simple equals comparison
			return (ruleValueString.equals(otherValueString));
		}
	}
	
	
	public String getClassName() {
		return testClass.value;
	}
	
	public String getTestName() {
		return testName.value;
	}

	
	public String toString() {
		return originalText;
	}
}