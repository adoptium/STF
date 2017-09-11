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

package net.adoptopenjdk.stf.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;


/**
 * This is an immutable class which takes a test plugin name (eg, 'UtilLoadTest') and finds:
 *   1) the class to run.
 *   2) the project it belongs to.
 */
public class PluginFinder {
    private static final Logger logger = LogManager.getLogger(PluginFinder.class.getName());

	private String projectName; 
	private String pluginClassName;
	
	/**
	 * This constructor searches the workspace for a named test plugin.
	 * This functionality is needed because running a test through STF requires only the 
	 * test name. Not having to specify anything more than just the test name makes it
	 * easy to run a test, but does mean that STF has to do some more work.
	 * 
	 * If the plugin class cannot be found then an exception is thrown. 
	 * 
	 * @param workspacePropertyValue contains the value of the workspace property. 
	 * @param workspaceRootRef points to the root of the workspace. ie. one level above the projects.
	 * @param testRoot points to the directory containing test cases.
	 * @param testName is the the name of the test whose plugin we want to find. eg, 'UtilLoadTest'
	 * @throws StfException if a single suitable plugin class cannot be found.
	 */
	public PluginFinder(ArrayList<DirectoryRef> testRootRefs, String testName) throws StfException {

		if (testRootRefs.isEmpty()) {
			throw new StfException("PluginFinder was initialised with an empty list of potential test roots.");
		}
		
		ArrayList<File> javaProjects = new ArrayList<File>();
		
		// Find all projects below all test-roots
		for (DirectoryRef testRootRef : testRootRefs) {
			File testRoot = testRootRef.asJavaFile(); 
			for (File file : testRoot.listFiles()) {
				if (file.isDirectory()                         // We only want to examine projects, ie. directories
						&& !file.getName().startsWith(".")     // Ignore hidden directories/projects
						&& new File(file, "bin").exists()) {   // Java projects have a 'bin' directory
					javaProjects.add(file);
				}
			}
		}
		
		// Search through all projects looking for the test plugin
		LinkedHashSet<String> matchingProjects = new LinkedHashSet<String>();
		ArrayList<String> matchingFiles = new ArrayList<String>();
		for (File project : javaProjects) {
			// Start searching in the bin directory of the current project
			File projectBin = new File(project, "bin");
			logger.debug("Searching project: " + projectBin.getAbsolutePath());
			if (findClass(testName + ".class", projectBin, "", matchingFiles)) {
				logger.info("Found test. Project: '" + project.getName() + "' class: '" + testName + ".class" + "' Dir: '" + projectBin.getAbsolutePath() + "'");
				matchingProjects.add(project.getName());
			}
		}
		
		// Fail if there is not exactly 1 matching class found 
		if (matchingFiles.isEmpty()) {
			//First we assemble a list of test roots we searched in.
			String workspaces = "";
			for (DirectoryRef oneTestRootRef : testRootRefs) {
				workspaces = workspaces + ",'" + oneTestRootRef.getSpec() + "'";
			}
			//Then we throw the error. 
			System.out.println(Thread.currentThread().getStackTrace().toString());
			throw new StfError("Could not find test plugin cpalled '" + testName + "' "
					+ "in any of these workspaces: " + workspaces.substring(1) + ". "
					+ "To fix this correct the name of the test plugin.");
		}
		if (matchingFiles.size() > 1) {
			throw new StfException("Ambiguous test name specified."
					+ " Found " + matchingFiles.size() + " classes with the that name: " + matchingFiles.toString() 
					+ " In projects: " + matchingProjects);
		}
		
		this.projectName = matchingProjects.iterator().next();
		this.pluginClassName = matchingFiles.get(0);

		logger.info("Found test. Project: '" + projectName + "' class: '" + pluginClassName + "'");
	}


	/**
	 * Recursively finds a particular class file within a project.
	 * 
	 * @param targetClassFile is the name of class to find. eg 'UtilLoadTest.class'
	 * @param dir is the directory to search.
	 * @param currPath is the path to the current directory.
	 * @param matchingFiles is an ArrayList containing details on all matching classes.
	 * @returns true if a matching class was found.
	 */
	private boolean findClass(String targetClassFile, File dir, String currPath, ArrayList<String> matchingFiles) throws StfException {
		boolean found = false;
		
		if (dir == null) { 
			throw new StfException("Internal error: Cannot search null directory: " + currPath);
		}
		
		// Produce debug output if the 'impossible' happens, and File.listFiles() returns null.
		File[] directoryContents = dir.listFiles();
		if (directoryContents == null) {
			// Abandon search of the current directory
			logger.warn("WARNING: PluginFinder.findClass() had a null return value from File.listFiles(). dir=" + dir.getAbsolutePath() + " path=" + currPath);
			return found;
		}
		
		// Search the contents of the current directory and its children
		for (File f : directoryContents) {
			if (f.isDirectory()) {
				String newPath = currPath + "." + f.getName();
				found |= findClass(targetClassFile, f, newPath, matchingFiles);
			} else if (f.getName().equals(targetClassFile)) {
				// Matching class file found. Add to results.
				String cleanedFileName = (currPath + "." + f.getName().replaceAll(".class$", ""));
				matchingFiles.add(cleanedFileName.substring(1));
				found = true;
			}
		}
		
		return found;
	}
	
	public String getProjectName() {
		return projectName;
	}

	public String getPluginClassName() {
		return pluginClassName;
	}
}