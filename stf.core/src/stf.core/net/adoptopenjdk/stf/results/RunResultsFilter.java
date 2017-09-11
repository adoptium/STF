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

import java.io.File;
import java.util.ArrayList;

import net.adoptopenjdk.stf.StfException;


/**
 * This class is to run test results filtering. 
 * This happens at the end of a test run and is a chance for tests which 
 * failed with known failures to be treated as having passed.
 */
public class RunResultsFilter {
	// Main method for testing 
	public static void main(String[] args) throws StfException {
		if (args.length != 2) { 
			System.out.println("RunResultsFilter <testResultsFile> <exclusionsFile.txt>");
			System.exit(127);
		}

		String testResultsFile = args[0];
		String exclusionsFile  = args[1];
		
		boolean testRunPassed = new RunResultsFilter().process(testResultsFile, new File(exclusionsFile));
		
		int exitCode = testRunPassed ? 0 : 1;
		System.exit(exitCode);
	}
	

	public boolean process(String testResultsString, File exclusionsFile) throws StfException {
		System.out.println();
		System.out.println("Using exclusions: " + exclusionsFile);
		
    	ArrayList<TestStatus> results = new ResultsParser(testResultsString).parse();
    	ArrayList<TestStatus> filters = new ResultsParser(exclusionsFile).parse();

    	ResultsFilter filteredResults = new ResultsFilter(results, filters);
    	
        System.out.println();
		System.out.println("Test Results after filtering with exclusions");
        System.out.printf("  Ran             : %4d\n", filteredResults.getRunCount());
        System.out.printf("  Clean passes    : %4d\n", filteredResults.getPasses().size());
        System.out.printf("  Filtered passes : %4d\n", filteredResults.getFilteredPasses().size());
        System.out.printf("  Failed          : %4d\n", filteredResults.getFailures().size());
        System.out.printf("  Ignored         : %4d\n", filteredResults.getIgnored().size());

        listResults("Filtered passes (failures upgraded to a pass)", filteredResults.getFilteredPasses());
        listResults("Failing tests", filteredResults.getFailures());

        System.out.printf("\nOverall result : %s\n", (filteredResults.wasSuccessful() ? "PASSED" : "FAILED"));
        
        boolean testRunPassed = filteredResults.wasSuccessful();
        return testRunPassed;
	}

    
	private static void listResults(String title, ArrayList<TestStatus> results) {
		if (!results.isEmpty()) {
		    System.out.println();
			System.out.println(title + ": " + results.size());
		    for (int i=0; i<results.size(); i++) {
		    	TestStatus ts = results.get(i);
		    	System.out.println("  " + (i+1) + ") " + ts.getClassName() + " " + ts.getTestName());
		    }
		}
	}
}