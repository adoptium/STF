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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import net.adoptopenjdk.stf.StfException;


/**
 * This class can be used after an test has run to decide if the run passed or failed.
 * It also supports a results exclusions file, which can be used to decide if a test
 * failure is a known issue, and can therefore be treated as having passed.
 * 
 * It takes 3 arguments:
 *   - then name of the test suite.
 *   - the results directory, in which the xml results file can be found.
 *   - the file name of an exclusion results file.
 *   
 * It returns 0 if all test passed, or were treated as having passed following the application 
 * of the rules in the exclusions file.
 */
public class ReportFilteredTestResults {

	public static void main(String[] args) throws StfException {
		if (args.length != 2) { 
			System.out.println("ReportFilteredTestResults <results-dir> <exclusions-file.txt>");
			System.exit(127);
		}

		String resultsDirString = args[0];
		String filterFileString = args[1];	

		// Validate parameters
		File resultsDir = new File(resultsDirString);
		if (!(resultsDir.exists() && resultsDir.isDirectory())) {
			throw new StfException("Results directory does not exist, or is not a directory: " + resultsDirString);
		}
		File filterFile = new File(filterFileString);
		if (!(filterFile.exists() && filterFile.isFile())) {
			throw new StfException("Filter file does not exist, or is not a file: " + filterFileString);
		}
		
		// Find all the .tr result files
		ArrayList<File> trResultFiles = new ArrayList<File>();
		for (File trResultFile : resultsDir.listFiles()) {
			if (trResultFile.isFile() && trResultFile.getName().endsWith(".tr")) {
				trResultFiles.add(trResultFile);
			}
		}

		if (trResultFiles.isEmpty()) {
			throw new StfException("Failed to find any .tr file in directory: " + resultsDir.getAbsolutePath());
		}

		// Sort result files, from oldest to newest
		Collections.sort(trResultFiles, new Comparator<File>() {
	        public int compare(File file1, File file2)
	        {
	        	int cmp = Long.valueOf(file1.lastModified()).compareTo(Long.valueOf(file2.lastModified()));
	        	if (cmp == 0) {
	        		cmp = file1.getName().compareTo(file2.getName());
	        	}
	        	return cmp;
	        }
	    });
		
		// Read the contents of the results file, and turn it into a big single result string
		StringBuilder testResults = new StringBuilder();
		for (File trFile : trResultFiles) {
			System.out.println("Reading tr result file: " + trFile.getAbsolutePath());
			testResults.append(readFile(trFile));
		}
		
		// Apply the exclusions and report on the success/failure of the run 
		boolean testRunPassed = new RunResultsFilter().process(testResults.toString(), filterFile);
		
		int exitCode = testRunPassed ? 0 : 1;
		System.exit(exitCode);
	}

	private static String readFile(File file) throws StfException {
	    BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new FileReader(file));
		  
			StringBuffer fileContents = new StringBuffer();
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
			   fileContents.append(line).append("\n");
		    }
			return fileContents.toString();
		} catch (FileNotFoundException e) {
			throw new StfException(e);
		} catch (IOException e) {
			throw new StfException(e);
		} finally {
			try {
				bufferedReader.close();
			} catch (IOException e) {
				throw new StfException("Failed to close file: " + file.getAbsolutePath());
			}
		}
	}
}