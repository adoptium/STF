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

package net.adoptopenjdk.stf.runner.modes;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginRootInterface;
import net.adoptopenjdk.stf.runner.ClassPathConfigurator;


/**
 * This class searches for test cases within a workspace, so that people
 * can get a full list of available tests.
 * Once the search is complete it produces a list of them.
 * 
 * This code is triggered by running stf with the '-list' argument.
 */
public class PluginList {
    private static final Logger logger = LogManager.getLogger(PluginList.class.getName());

	private static class TestDetails {
		String projectName;
		String testName;
		
		public TestDetails(String projectName, String testName) {
			this.projectName = projectName;
			this.testName = testName;
		}
	}
	
	private static String CLASS_VERSION_INCOMPATIBLE_MESSAGE = "<< Class-version-incompatibilities >>";
	
	
	/**
	 * Examine all classes within the supplied workspaces and list those which are test cases.
	 * 
	 * @param environmentCore gives access to stf properties.
	 * @param testRoots points to the workspaces to be searched.
	 * @throws StfException if anything goes wrong.
	 */
	public void searchAndListTests(StfEnvironmentCore environmentCore, ArrayList<DirectoryRef> testRoots) throws StfException {

		//For each test root, find a list of all tests inside it.
		ArrayList<TestDetails> tests = new ArrayList<TestDetails>();
		for (DirectoryRef testRootDir : testRoots) {
			ArrayList<TestDetails> testRootTests = searchWorkspace(environmentCore, testRootDir);
			if (testRootTests.isEmpty()) {
				throw new StfError("No test cases found in test root '" + testRootDir.getSpec() + "'" );
			}
			// Now build an ascii table to tell the user about all the tests they can run
			listTestCases(testRootDir,testRootTests);
			
			// Finally, we add the sub-list of tests in this one test root to the main list of tests in all test roots.
			tests.addAll(testRootTests);
		}
	}


	/**
	 * This method searches a workspace for test cases.
	 * i.e. searches for class files which implement StfPluginRootInterface. 
	 * 
	 * @param environmentCore gives access to stf properties.
	 * @param testRoot points to the directory containing test cases.
	 * @return an ArrayList of test details for all STF test cases found.
	 * @throws StfException
	 */
	private ArrayList<TestDetails> searchWorkspace(StfEnvironmentCore environmentCore, DirectoryRef workspace) throws StfException {
		ArrayList<TestDetails> tests = new ArrayList<TestDetails>();

		// Find all potential projects below the test-root
		File[] projectFilesInTestRoot = workspace.asJavaFile().listFiles();
		
		// Combine the potential projects from  test-root into an arrayList
		ArrayList<File> projectFiles = new ArrayList<File>();
		projectFiles.addAll(Arrays.asList(projectFilesInTestRoot));

		// Search each potential project directory to identify those that really 
		// are Java projects (and which may therefore contain test cases)
		ArrayList<File> projects = new ArrayList<File>();
		for (File file : projectFiles) {
			boolean hasStfClasspathFile = new File(file, StfConstants.STF_CLASSPATH_XML_FILE).exists();
			boolean hasClasspathFile    = new File(file, ".classpath").exists();
			if (file.isDirectory() && (hasStfClasspathFile || hasClasspathFile)) {
				projects.add(file);
			}
		}
		
		// Search each projects class files in turn
		for (File project : projects) {
			File projectBinDir = new File(project, "bin");
			if (projectBinDir.exists()) {
				// Setup the STF class loader to use the dependencies for the current project
				boolean projectUsesStf = ClassPathConfigurator.configureClassLoader(environmentCore, project.getName());

				if (!projectUsesStf) {
					logger.debug("Not searching project '" + project.getName() + "' as it doesn't use STF");
					continue;
				}
				
				// Search all child directories of this projects bin directory
				try {
					logger.debug("Looking for tests in project " + projectBinDir.getAbsolutePath());
					for (File f : projectBinDir.listFiles()) {
						if (f.isDirectory()) {
							doFindPlugins(project.getName(), f, f.getName(), tests);
						}
					}
				} catch (UnsupportedClassVersionError e) {
					// Project contains code for newer JVM version. Abandon search of this project.
					tests.add(new TestDetails(project.getName(), CLASS_VERSION_INCOMPATIBLE_MESSAGE));
				} catch (ClassFormatError e) {
					logger.debug("ClassFormatError when searching project: " + project.getName());
				} catch (LinkageError e) {
					logger.debug("LnkageError when searching project: " + project.getName());
				}
			}
		}
		
		
		// Sort the identified tests. Firstly by project name and then by test name
		Collections.sort(tests, new Comparator<TestDetails>() {
			    public int compare(TestDetails t1, TestDetails t2) {
			    	int projectCompare = t1.projectName.compareTo(t2.projectName);
			    	if (projectCompare != 0) {
			    		return projectCompare;
			    	}
			    	return t1.testName.compareTo(t2.testName);
			    }
			}
		);
		
		return tests;
	}


	/**
	 * This method recursively searches for class files which are test cases.
	 * 
	 * @param projectName is the name of the project being searched.
	 * @param dir is the directory to look in.
	 * @param cleanedClassName is the name that all classes in the current directory would start with. 
	 * @param tests is an ArrayList that found test cases are added to. 
	 * @throws StfException
	 */
	private static void doFindPlugins(String projectName, File dir, String cleanedClassName, ArrayList<TestDetails> tests) throws StfException {
		logger.debug("Looking for tests in dir " + dir.getAbsolutePath());
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				doFindPlugins(projectName, f, cleanedClassName + "." + f.getName(), tests);
			} else if (f.getName().endsWith("module-info.class")) {
				return; // ignore
			} else if (f.getName().endsWith(".class")) {
				// Work out the full class name for the current file
				String testName = f.getName().replace(".class", "");
				String fullClassName = cleanedClassName + "." + testName;
				
				// Load the class
				Class<?> clazz = null;
				try {
					clazz = Class.forName(fullClassName, false, ClassLoader.getSystemClassLoader());
				} catch (ClassNotFoundException e) {
					// Don't do anything about this exception. 
					// It is only known to happen for classes with compilation 
					// problems (so no great loss if we don't list these as tests) 
					logger.debug("Failed to load class: " + fullClassName);
				}
				
				// Check to see if the current class is a test case
				if (clazz != null) {
					boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
					if (!clazz.isInterface()  
							&& !isAbstract
							&& StfPluginRootInterface.class.isAssignableFrom(clazz)) {
						tests.add(new TestDetails(projectName, testName));
					}
				}
			}
		}
	}


	/**
	 * This method writes out a table listing the project names and test names
	 * @throws StfException 
	 */
	private void listTestCases(DirectoryRef testRoot, ArrayList<TestDetails> tests) throws StfException {
		// Find the longest project and test names
		int projectNameLen = 0;
		int testNameLen    = 0;
		for (TestDetails test : tests) { 
			projectNameLen = Math.max(projectNameLen, test.projectName.length());
			testNameLen    = Math.max(testNameLen, test.testName.length());
		}
		
		// Output a header line for the table listing all test cases
		logger.info("");
		logger.info("Test automation in workspace at '" + testRoot.getSpec() + "': ");
		String titleLine = centerString("Project", projectNameLen) + " | " + centerString("Test automation", testNameLen);
		String borderString = buildRepeatingString('-', titleLine.length()+2);
		logger.info("  +" + borderString + "+");
		logger.info("  | " + titleLine + " |");
		logger.info("  |" + borderString + "|");
		
		// Output a 1 line summary for each test case
		for (TestDetails test : tests) {
		    logger.info(String.format("  | %-" + projectNameLen + "s | %-" + testNameLen + "s |", test.projectName, test.testName));
		}
		logger.info("  +" + borderString + "+");

		// Print warning if search had problems due to a class mismatch
		for (TestDetails test : tests) {
			if (test.testName.equals(CLASS_VERSION_INCOMPATIBLE_MESSAGE)) {
				logger.warn("Warning: One or more projects were not searched, as they contain classes newer that the running JVM");
				break;
			}
		}
	}
	
	
	private String buildRepeatingString(char c, int num) {
		StringBuilder buff = new StringBuilder();
		
		for (int i=0; i<num; i++) { 
			buff.append(c);
		}
		
		return buff.toString();
	}


	private String centerString(String s, int width) {
		int beforeSpaces = (width - s.length()) /2;
		int afterSpaces = width - s.length() - beforeSpaces;

		String formatSpec = "%" + beforeSpaces + "s%s%" + afterSpaces + "s";
		return String.format(formatSpec, "", s, "");
	}
}