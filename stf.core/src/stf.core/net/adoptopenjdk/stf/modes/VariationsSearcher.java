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

package net.adoptopenjdk.stf.modes;

import java.io.File;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;

/**
 * This class finds all java-args definitions for a single variation name. 
 * It searches for these in:
 *   1) variations.xml file
 *   2) all testplan.xml files
 */
public class VariationsSearcher {
	private static final Logger logger = LogManager.getLogger(VariationsFileReader.class.getName());
	
	private static final String VARIATIONS_FILE = "tools.testExecution/config/variations.xml";
	private static final String TESTPLANS_DIRECTORY = "testplans";
	private static final String TESTPLAN_FILENAME = "testplan.xml";


	/**
	 * Reads variations.xml and testplan.xml files to find the java-args for the named variation.
	 * @param environmentCore 
	 * @param variationName is variation that has been set through the STF '-mode' argument. 
	 * @return ArrayList containing all matching java-arg definitions.
	 * @throws StfException if there is an internal error.
	 */
	public ArrayList<JvmArgDetails> findMode(StfEnvironmentCore environmentCore, String variationName) throws StfException {
		ArrayList<JvmArgDetails> javaArgDefinitions = new ArrayList<JvmArgDetails>();
		
		// Find any and all variation definitions from the main variations file
		File variationsFile = environmentCore.findTestFile(VARIATIONS_FILE).asJavaFile();
		ArrayList<JvmArgDetails> variationsFileDefinitions = VariationsFileReader.decodeVariationName(variationsFile, variationName);
		javaArgDefinitions.addAll(variationsFileDefinitions);
		
		// Search all testplan.xml files for definitions of this variation
		ArrayList<File> testplans = findTestplans(environmentCore.getTestRoots()); 
		for (File testplan : testplans) {
			ArrayList<JvmArgDetails> testplanDefinions = VariationsFileReader.decodeVariationName(testplan, variationName);
			javaArgDefinitions.addAll(testplanDefinions);
		}
		
		return javaArgDefinitions;
	}

	
	/**
	 * Search the test roots for all testplan.xml files. 
	 * These are assumed to live below the 'testplans' directory of each project.
	 * @param testRoots are the directories to search below.
	 * @return an ArrayList containing references to all found testplan.xml files.
	 * @throws StfException if there is an internal error.
	 */
	private ArrayList<File> findTestplans(ArrayList<DirectoryRef> testRoots) throws StfException {
		ArrayList<File> foundTestplans = new ArrayList<File>();
		for (DirectoryRef testRoot : testRoots) {
			logger.debug("Searching for testplans below: " + testRoot);
			
			// Search all projects which have a top level 'testplans' directory
			for (File project : testRoot.asJavaFile().listFiles()) {
				File testplansDir = new File(project, TESTPLANS_DIRECTORY);
				if (testplansDir.exists() && testplansDir.isDirectory()) {
					searchDirForTestplans(testplansDir, foundTestplans);
				}
			}
		}
		
		return foundTestplans;
	}


	// Recursively search a directory for testplan.xml files
	private void searchDirForTestplans(File dir, ArrayList<File> foundTestplans) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				searchDirForTestplans(f, foundTestplans);
			} else if (f.isFile() && f.getName().equals(TESTPLAN_FILENAME)) {
				foundTestplans.add(f);
			}
		}
	}
}