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

package net.adoptopenjdk.stf.environment;

import java.io.File;
import java.util.ArrayList;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfException;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class represents a directory, as known to a specific test case.
 * 
 * The test plugin does not know the absolute directory represented by an instance 
 * of this class.
 * This is a deliberate choice, and is done to simplify the test cases and also 
 * ensure that they remain platform independent.
 */
public class DirectoryRef {
	private String fileName;
	
	DirectoryRef(String fileName) throws StfException {
		String trimmedFileName = fileName.trim();
		
		if (PlatformFinder.isWindows()) {
			// Replace all of the windows file separators with the linux equivalent.
			trimmedFileName = trimmedFileName.replaceAll("\\\\", "/");  // Check that the file spec is windows compliant.
			// And then we remove duplicates, as Windows paths can cause recursive backslashing.
			while (trimmedFileName.contains("//")) {
				trimmedFileName = trimmedFileName.replaceAll("//", "/");  // Check that the file spec is windows compliant.
			}
			this.fileName = trimmedFileName;
		} else {
			if (trimmedFileName.startsWith("~")) {
				String homeDir = System.getProperties().getProperty("user.home");
				this.fileName = trimmedFileName.replace("~", homeDir);
			} else if (trimmedFileName.startsWith("/")) {
				this.fileName = trimmedFileName;
			} else if (trimmedFileName.startsWith(".")) {
				this.fileName = trimmedFileName;
			} else {
				throw new StfException("Invalid directory specification: '" + fileName + "'. "
						+ "Must start with either '~', '/' or '.'");
			}
		}
		
		// Normailze paths that contain ".." where possible 
		if (this.fileName.contains("..")) { 
			if (this.fileName.endsWith("/..")) {
				// Throw an exception if we're attempting to go "up" the path from root.
				if( this.fileName.equals("/..") || this.fileName.substring(1).equals(":/..") ) {
					this.fileName = this.fileName.substring(0, this.fileName.length() - 3);
					throw new StfException("Directory " + this.fileName	+ " does not have a parent directory,"
									+ " so " + this.fileName + "/.. will not work. Setting to " + this.fileName);
				}
			}
			// If it's a relative directory, we can't be sure if it does/does not exist 
			// until we're ready to check if it exists, so ignore the ..'s.
			else if(this.fileName.startsWith("./..")) {
				return;
			// Make file names more readable by converting '/a/b/../../c/d/e/f/../..' to '/c/d', etc, where possible.
			} else {
				Path p1 = Paths.get(this.fileName);
				String normalizedPath = p1.normalize().toString();
				if (normalizedPath.length() > 0) {
					this.fileName = normalizedPath; 
					if (PlatformFinder.isWindows()) {
						// Replace all of the windows file separators with the linux equivalent.
						this.fileName = this.fileName.replaceAll("\\\\", "/");   
					}
				}			
			}
		}		
	}

	
	/**
	 * Create a reference to a file which lives below this directory reference.
	 * @param subdirs is a string describing the file. eg 'bin/java.properties'
	 */
	public FileRef childFile(String subdirs) throws StfException {
		if (subdirs.startsWith("/")) {
			subdirs = subdirs.substring(1);
		}
		
		if (subdirs.startsWith("~")) {
			throw new StfException("Invalid sub directory specification. Must not start with '~'. "
					+ "Parent directory: '" + fileName + "'. "
					+ "Invalid child directory: '" + subdirs + "'");
		}
	
		if (subdirs.contains("\\")) {
			throw new StfException("Invalid file specification. The relative file name cannot contain windows style file separators: " + subdirs);
		}
		
		String join = fileName.endsWith("/") ? "" : "/";	
		return new FileRef(fileName + join + subdirs.trim());
	}

	
	/**
	 * Create a reference to a directory which lives below this directory reference.
	 * @param subdirs is a string describing the sub directory. eg 'bin' or 'bin/java'
	 */
	public DirectoryRef childDirectory(String subdirs) throws StfException {
		if (subdirs.startsWith("~") || subdirs.startsWith("/")) {
			throw new StfException("Invalid sub directory specification. Must not start with either '~' or '/'. "
					+ "Parent directory: '" + fileName + "'. "
					+ "Invalid child directory: '" + subdirs + "'");
		}

		if (subdirs.contains("\\")) {
			throw new StfException("Invalid file specification. The relative file name cannot contain windows style file separators: " + subdirs);
		}
	
		String join = fileName.endsWith("/") ? "" : "/";	
		return new DirectoryRef(fileName + join + subdirs.trim());
	}
	

	/**
	 * This is a utility function takes in the full path of a file and returns the part of the 
	 * string which is below the current directory reference.
	 * For example,
	 *   dir ref   = /stf/results
	 *   file spec = /stf/results/12.inventory/load/mauvetests.xml
	 *   returns   = 12.inventory/load/mauvetests.xml
	 * @return the part of the file spec which is a child of the current directory.
	 * @throws StfException if the supplied file specification is a file not below the current directory reference.
	 */
	public String getSubpathOf(String fileSpec) throws StfException {
		// Get platform independent versions of the supplied file spec and the current directoryRef
		fileSpec = fileSpec.replace("\\", "/").replace("//", "/");
		String thisDirSpec = fileName;
		
		if (!fileSpec.startsWith(thisDirSpec)) {
			throw new StfException("Referenced file is not a child of the current directory reference. " 
						+ " File: " + fileSpec + " in not below: " + thisDirSpec);
		}

		String uniquePart = fileSpec.substring(thisDirSpec.length()+1);
		
		return uniquePart;
	}

	
	/**
	 * Convert the current file reference to a Java File object.
	 * Note: the Java File object is platform specific.
	 * @throws StfException 
	 */
	public File asJavaFile() throws StfException {
		return new File(getSpec());
	}
	

	/**
	 * Return the platform specific absolute location of the current directory reference.
	 * @throws StfException 
	 */
	public String getSpec() throws StfException {
		if (PlatformFinder.isWindows()) {
			return fileName.replace("/", "\\\\");
		}
		 
		return fileName;
	}

	
	/**
	 * Return the platform independent location of the current directory reference.
	 */
	public String getPath() {
		return fileName;
	}
	

	public String toString() {
		try {
			return getSpec();
		} catch (StfException e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}


	public boolean exists() throws StfException {
		return asJavaFile().exists();
	}


	public DirectoryRef getParent() throws StfException {
		return new DirectoryRef(fileName + "/..");
	}


	// Convert a File reference of the results directory into a DirectoryRef object
	public static DirectoryRef createResultsDirectoryRef(File resultsDir) throws StfException {
		if (!resultsDir.getAbsolutePath().endsWith(StfConstants.RESULTS_DIR)) {
			throw new StfException("Invalid results directory: " + resultsDir.getAbsolutePath());
		}
		return new DirectoryRef(resultsDir.getAbsolutePath());
	}
	
	/**
	 * Takes a directory path (without a root) and a list of test roots, and tries to find out which 
	 * root contains that directory. If it cannot be found, or is found multiple times, an exception 
	 * is thrown.
	 * @param directoryRef  The path of the directory we are trying to find, minus the root.
	 * @param testRootRefs  The potential roots of the path for the directory we're trying to find.
	 * @return              A DirectoryRef for the root of the directory we were trying to find.
	 * @throws StfException In case we cannot find the directory, or if we found it in more than one test root.
	 */
	public static DirectoryRef findDirectoryRoot(String directoryRef, ArrayList<DirectoryRef> testRootRefs) throws StfException {
		int count = 0;
		DirectoryRef directoryRoot = new DirectoryRef("./ignore");
		for (int i = 0 ; i < testRootRefs.size() ; ++i ) {
			try {
				findDirectory(directoryRef, new ArrayList<DirectoryRef>(testRootRefs.subList(i, i+1)));
				directoryRoot = testRootRefs.get(i);
				count++;
			} catch (StfException e) {
				//ignore;
			}
		}
		
		if (count != 1) {
			//This should produce the right error.
			findDirectory(directoryRef, testRootRefs);
		}
		
		return directoryRoot;
	}
	

	/**
	 * Takes a directory path (without a root) and a list of test roots, and tries to find that 
	 * directory inside each of those roots. If it cannot be found, or is found multiple times, 
	 * an exception is thrown.
	 * @param directoryRef              The path of the directory we are trying to find, minus the root.
	 * @param testRootRefs              The potential roots of the path for the directory we're trying to find.
	 * @param errorPrefixes These are optional.
	 *                      The first String will be prefixed onto the StfException message if the directory cannot be found.
	 *                      The second String will be prefixed onto the StfException message if the directory is found more then once.
	 *                      The third+ Strings will be ignored.
	 * @return                          A DirectoryRef for the directory we were trying to find.
	 * @throws StfException             In case we cannot find the directory, or if we found it in more than one test root.
	 */
	public static DirectoryRef findDirectory(String directoryRef, ArrayList<DirectoryRef> testRootRefs, String... errorPrefixes) throws StfException {
		ArrayList<DirectoryRef> matchingTestRoots = new ArrayList<DirectoryRef>();
		for (DirectoryRef oneTestRoot : testRootRefs) {
			if (oneTestRoot.childDirectory(directoryRef).exists()) {
				matchingTestRoots.add(oneTestRoot);
			}
		}
		//Check to see if we only found it.
		if (matchingTestRoots.size() == 0) {
			//First we assemble a list of test roots we searched in.
			String workspaces = "";
			for (DirectoryRef oneTestRootRef : testRootRefs) {
				workspaces = workspaces + ",'" + oneTestRootRef.getSpec() + "'";
			}
			//Then we throw the error. 
			String prefix = "";
			if (errorPrefixes.length > 0) {
				prefix = errorPrefixes[0] + " ";
			}
			throw new StfException(prefix + "Note: directory '" + directoryRef + "' could not be found in any of the supplied roots: " + workspaces.substring(1));
		}
		//Check to see if we only found it once.
		if (matchingTestRoots.size() > 1) {
			//First we assemble a list of test roots we found the directory in.
			String matchedWorkspaces = "";
			for (DirectoryRef oneMatchingTestRootRef : matchingTestRoots) {
				matchedWorkspaces = matchedWorkspaces + ",'" + oneMatchingTestRootRef.getSpec() + "'";
			}
			//Then we throw the error. 
			String prefix = "";
			if (errorPrefixes.length > 1) {
				prefix = errorPrefixes[1] + " ";
			}
			throw new StfException(prefix + "Note: directory '" + directoryRef + "' was found multiple times, in this subset of the supplied test roots: " + matchedWorkspaces.substring(1));
		}
		
		//Return the match.
		return matchingTestRoots.get(0).childDirectory(directoryRef);
	}
}
