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
import java.util.regex.Pattern;

import net.adoptopenjdk.stf.StfException;


/**
 * This class represents a file, which is known to a specific test case.
 * 
 * The test plugin does not know the absolute path to the file, only it's relative 
 * location to some root directory reference.
 * This is a deliberate choice, and is done to simplify the test cases and also
 * ensure that they remain platform independent.
 */
public class FileRef {
	private String fileName;
	
	FileRef(String fileName) throws StfException {
		String trimmedFileName = fileName.trim();
		
		if (PlatformFinder.isWindows()) {
			// Make sure it's a windows style path
		    Pattern pattern = Pattern.compile("(^[a-zA-Z]:[\\\\/])|(^\\.[\\\\/])");
		    if(pattern.matcher(trimmedFileName).find()) {
		    	this.fileName = trimmedFileName;
		    } else {
				throw new StfException("Invalid file name \"" + trimmedFileName + "\". Must start with either a drive name (e.g. C:), or a full stop.");
		    }
		} else {
			if (trimmedFileName.startsWith("~")) {
				String homeDir = System.getProperties().getProperty("user.home");
				this.fileName = trimmedFileName.replace("~", homeDir);
			} else if (trimmedFileName.startsWith("/")) {
				this.fileName = trimmedFileName;
			} else if (trimmedFileName.startsWith(".")) {
				this.fileName = trimmedFileName;
			} else {
				throw new StfException("Invalid file specification. Must start with either '~', '/' or '.'");
			}
		}
	}


	/**
	 * Get a reference to the parent directory of the current file.
	 */
	public DirectoryRef parent() throws StfException {
		int lastDivider = fileName.lastIndexOf("/");
		if (lastDivider < 0) {
			throw new StfException("Can't get parent directory for file: " + fileName);
		}
		
		return new DirectoryRef(fileName.substring(0, lastDivider));
	}


	/**
	 * Return the file name that this file reference is pointing at.
	 * eg, for file reference of '/tmp/results/x.log', the file name returned would be 'x.log'
	 */
	public String getName() {
		String[] fileNameParts = fileName.split("/"); 
		return fileNameParts[fileNameParts.length-1]; 
	}
	

	/** 
	 * Convert this file reference into a Java File object.
	 */
	public File asJavaFile() {
		return new File(fileName);
	}

	public boolean exists() throws StfException {
		return asJavaFile().exists();
	}

	
	/**
	 * Return the platform specific absolute location of the current file reference.
	 */
	public String getSpec() {
		return fileName;
	}


	public String toString() {
		return getSpec();
	}
	
	
	/**
	 * Takes a file path (without a root) and a list of test roots, and tries to find out which 
	 * root contains that file. If it cannot be found, or is found multiple times, an exception 
	 * is thrown.
	 * @param fileRef       The path of the file we are trying to find, minus the root.
	 * @param testRootRefs  The potential roots of the path for the file we're trying to find.
	 * @return              A DirectoryRef for the root of the file we were trying to find.
	 * @throws StfException In case we cannot find the file, or if we found it in more than one test root.
	 */
	public static DirectoryRef findFileRoot(String fileRef, ArrayList<DirectoryRef> testRootRefs) throws StfException {
		int count = 0;
		DirectoryRef fileRoot = new DirectoryRef("./ignore");
		for (int i = 0 ; i < testRootRefs.size() ; ++i ) {
			try {
				findFile(fileRef, new ArrayList<DirectoryRef>(testRootRefs.subList(i, i+1)));
				fileRoot = testRootRefs.get(i);
				count++;
			} catch (StfException e) {
				//ignore;
			}
		}
		
		if (count != 1) {
			//This should produce the right error.
			findFile(fileRef, testRootRefs);
		}
		
		return fileRoot;
	}
	
	/**
	 * Takes a file path (without a root) and a list of test roots, and tries to find that file 
	 * inside each of our test roots. If it cannot be found, an exception is thrown.
	 * @param fileRef       The path of the file we are trying to find, minus the root.
	 * @param testRootRefs  The potential roots of the path for the file we're trying to find.
	 * @param errorPrefixes These are optional.
	 *                      The first String will be prefixed onto the StfException message if the file cannot be found.
	 *                      The second String will be prefixed onto the StfException message if the file is found more then once.
	 *                      The third+ Strings will be ignored.
	 * @return               A FileRef for the file we were trying to find.
	 * @throws StfException In case we cannot find the file, or if we found it in more than one test root.
	 */
	public static FileRef findFile(String fileRef, ArrayList<DirectoryRef> testRootRefs, String... errorPrefixes) throws StfException {
		ArrayList<DirectoryRef> matchingTestRoots = new ArrayList<DirectoryRef>();
		for (DirectoryRef oneTestRoot : testRootRefs) {
			if (oneTestRoot.childFile(fileRef).exists()) {
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
			throw new StfException(prefix + "Note: file '" + fileRef + "' could not be found in any of the supplied test roots: " + workspaces.substring(1));
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
			throw new StfException(prefix + "Note: file '" + fileRef + "' was found multiple times, in this subset of the supplied test roots: " + matchedWorkspaces.substring(1));
		}
		
		//Return the match.
		return matchingTestRoots.get(0).childFile(fileRef);
	}
}
