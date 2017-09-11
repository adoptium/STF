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
import java.util.Iterator;

import net.adoptopenjdk.stf.StfException;


/**
 * This class takes a set of results and applies filtering rules.
 * 
 * Once constructed, and filtering is complete, this class makes available its 
 * results as being in 4 different categories:
 *   o passes - these are tests which passed when executed
 *   o filtered passes - which failing on execution, but matched a filtering rule. 
 *   o failures - tests which failed execution and don't match any filtering rules.
 *   o ignored - tests which have the '@Ignore' annotation, and have not been run.
 */
public class ResultsFilter {
	// Holds results after applying filters
	private ArrayList<TestStatus> passes;
	private ArrayList<TestStatus> filteredPasses;
	private ArrayList<TestStatus> failures;
	private ArrayList<TestStatus> ignored;

	
	public ResultsFilter(ArrayList<TestStatus> actualResults, ArrayList<TestStatus> expectedFailures) throws StfException {
		// All test results are going to be examined - so start with all of them
		ArrayList<TestStatus> results = new ArrayList<TestStatus>(actualResults);
		
		// Categories which results are going to be filed against
		this.passes         = new ArrayList<TestStatus>();
		this.filteredPasses = new ArrayList<TestStatus>();
		this.failures       = new ArrayList<TestStatus>();
		this.ignored        = new ArrayList<TestStatus>();

		// Attempt to apply every correction rule
		for (TestStatus failureRule : expectedFailures) {
			// Check every remaining test against the current rule
			for (Iterator<TestStatus> i = results.iterator(); i.hasNext();) {
		       TestStatus result = (TestStatus) i.next();
		       if (result.passed()) {
		    	   passes.add(result);
		    	   i.remove();
		       } else if (result.ignored()) {
		    	   ignored.add(result);
		    	   i.remove();
		       } else if (failureRule.matches(result)) {
		    	   filteredPasses.add(result);
		    	   i.remove();
		       }
			}
		}
		
		// All remaining tests have not matched a rule so are treated as failures
		this.failures = results;
	}

	
	public ArrayList<TestStatus> getPasses() {
		return passes;
	}
	
	public ArrayList<TestStatus> getFilteredPasses() {
		return filteredPasses;
	}
	
	public ArrayList<TestStatus> getFailures() {
		return failures;
	}
	
	public ArrayList<TestStatus> getIgnored() {
		return ignored;
	}
	
	public int getRunCount() {
		return passes.size() + filteredPasses.size() + failures.size();
	}
	
	public boolean wasSuccessful() {
		return failures.isEmpty();
	}
	
	public String toString() { 
		return Arrays.toString(getFailures().toArray());
	}
}