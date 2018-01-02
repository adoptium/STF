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

package net.adoptopenjdk.stf.extensions.core;

import java.util.ArrayList;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.StfTestArguments;
import net.adoptopenjdk.stf.extensions.Stf;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition.JarId;


/**
 * This class provides simplified environment information to plugins using the 'Stf' extension.
 */
public class StfEnvironment {
    // Holds a reference to the full STF environment data.
	// This class is used to provide test plugins with the relevant subset of 
	// the environmental data. 
	private StfEnvironmentCore environmentCore;
	
	
	StfEnvironment(StfEnvironmentCore environmentCore) throws StfException {
		this.environmentCore = environmentCore;
	}
	
	
	/**
	 * Takes a directory path without a root, and tries to find that directory inside 
	 * each of our test roots. If it cannot be found, an exception is thrown.
	 * @param directoryRef  The path of the directory we are trying to find, minus the root.
	 * @param errorPrefixes (optional) the first string will be prefixed onto a "cannot find directory" error message.
	 *                      (optional) the second string will be prefixed onto a "found directory >once" error message.
	 *                      (optional) the third+ string/s will be ignored.
	 * @return              A DirectoryRef for the directory we were trying to find.
	 * @throws StfException In case we cannot find the directory, or if we found it in more than one test root.
	 */
	public DirectoryRef findTestDirectory(String directoryRef, String... errorPrefixes) throws StfException {
		return environmentCore.findTestDirectory(directoryRef, errorPrefixes);
	}
	
	
	/**
	 * Takes a file path without a root, and tries to find that file inside each of our test 
	 * roots. If it cannot be found, an exception is thrown.
	 * @param fileRef       The path of the file we are trying to find, minus the root.
	 * @param errorPrefixes (optional) the first string will be prefixed onto a "cannot find file" error message.
	 *                      (optional) the second string will be prefixed onto a "found file >once" error message.
	 *                      (optional) the third+ string/s will be ignored.
	 * @return              A FileRef for the file we were trying to find.
	 * @throws StfException In case we cannot find the file, or if we found it in more than one test root.
	 */
	public FileRef findTestFile(String fileRef, String... errorPrefixes) throws StfException {
		return environmentCore.findTestFile(fileRef, errorPrefixes);
	}
	
	
	/**
	 * Takes a directory path without a root, and tries to find that directory inside 
	 * each of our prereq roots. If it cannot be found, an exception is thrown.
	 * @param directoryRef  The path of the directory we are trying to find, minus the root.
	 * @param errorPrefixes (optional) the first string will be prefixed onto a "cannot find directory" error message.
	 *                      (optional) the second string will be prefixed onto a "found directory >once" error message.
	 *                      (optional) the third+ string/s will be ignored.
	 * @return              A DirectoryRef for the directory we were trying to find.
	 * @throws StfException In case we cannot find the directory, or if we found it in more than one prereq root.
	 */
	public DirectoryRef findPrereqDirectory(String directoryRef, String... errorPrefixes) throws StfException {
		return environmentCore.findPrereqDirectory(directoryRef, errorPrefixes);
	}
	
	
	/**
	 * Takes a file path without a root, and tries to find that file inside each of our prereq 
	 * roots. If it cannot be found, an exception is thrown.
	 * @param fileRef       The path of the file we are trying to find, minus the root.
	 * @param errorPrefixes (optional) the first string will be prefixed onto a "cannot find file" error message.
	 *                      (optional) the second string will be prefixed onto a "found file >once" error message.
	 *                      (optional) the third+ string/s will be ignored.
	 * @return              A FileRef for the file we were trying to find.
	 * @throws StfException In case we cannot find the file, or if we found it in more than one prereq root.
	 */
	public FileRef findPrereqFile(String fileRef, String... errorPrefixes) throws StfException {
		return environmentCore.findPrereqFile(fileRef, errorPrefixes);
	}
	
	
	/**
	 * Allows the caller to find the test cases root directory. i.e. The directory specified
	 * via the -test-root=xxx argument. 
	 */
	public ArrayList<DirectoryRef> getTestRoots() {
		return environmentCore.getTestRoots();
	}
	
	/**
	 * Allows the caller to find the prereqs directories. i.e. The locations 
	 * of the third-party executables, jars, etc.
	 * If not specified on the command line or in a properties file, the location 
	 * is found by working upwards from each of the test root directories, searching each
	 * directory for a child directory called prereqs.
	 * @return an array of DirectoryRefs pointing at prereq locations.
	 * 
	 */
	public ArrayList<DirectoryRef> getPrereqRoots() {
		return environmentCore.getPrereqRoots();
	}
	
	
	/**
	 * Tests which need temporary files or directories should create them in the 
	 * temporary directory. 
	 * Tests do not need to clean up the temporary directory, as this will 
	 * be done when the next test starts running.
	 * @return A reference to a temporary directory created for the current test.
	 */
	public DirectoryRef getTmpDir() { 
		return environmentCore.getTmpDir();
	}
	
	/**
	 * Test results and similar information should be written to the results directory.
	 * @return a reference to the results directory for the current test.
	 */
	public DirectoryRef getResultsDir() { 
		return environmentCore.getResultsDir();
	}
	
	/**
	 * Any data which may be helpful for debugging can be written to this directory.
	 * @return a directory reference to a directory which holds debugging information.
	 */
	public DirectoryRef getDebugDir() { 
		return environmentCore.getDebugDir();
	}
	
	/**
	 * @return a string containing the platform name, eg 'linux_x86-64'
	 * @throws StfException if there was a problem interpreting the current platform.
	 */
	public String getPlatform() throws StfException {
		return environmentCore.getPlatform();
	}
	
	/**
	 * This method provides access to the test specific properties. The argument 
	 * values are given to STF at runtime through the '-test-args' parameter.
	 * This takes 1 or more comma separated values. For example: 
	 *   stf -test=MyTest -test-args="arg1=value1,arg2=value2"
	 * 
	 * Each value for expectedPropertyNames can be specified in 1 of 2 forms:
	 *   - Either a string containing the name of the property. This makes the
	 *     property mandatory, so if not supplied the test will fail with a runtime 
	 *     error. eg, "suite"
	 *   - or, a String containing a default value. A value can be supplied at runtime
	 *     but if not then the default value is used. eg, "reporter=[LIVE]"
	 * 
	 * @param expectedPropertyNames is the names of the properties which the test expects.
	 * @return a StfTestArguments object holding the name/value pairs of the 'test-args'.
	 * @throws StfException if either:
	 *    1) The test expects properties but they have not been supplied. ie. missing properties. 
	 *    2) Properties were supplied but the test doesn't expect them. ie. extra properties.
	 */
	public StfTestArguments getTestProperties(String... expectedPropertyNames) throws StfException {
		return environmentCore.getTestProperties(expectedPropertyNames);
	}

	/** 
	 * Returns the osgi.os name.
	 * Expected return values are win32, linux, aix, macosx
	 */
	public String getOsgiOperatingSystemName() throws StfException {
		return environmentCore.getOsgiOperatingSystemName();
	}

	/** 
	 * Returns the osgi.ws value.
	 * Expected return values are win32, gtk
	 */
	public String getOsgiWindowingSystemName() throws StfException {
		return environmentCore.getOsgiWindowingSystemName();
	}

	/** 
	 * Returns the osgi.arch name.
	 * Expected return values are x86, x86_64, ppc, ppc64, arm
	 */
	public String getOsgiProcessorArchitecture() throws StfException {
		return environmentCore.getOsgiProcessorArchitecture();
	}
	
	/**
	 * Returns object to represent the primary JVM available to the test.
	 * @throws StfException if the JVM is not specified.
	 */
	public JavaVersion primaryJvm() throws StfException { 
		return environmentCore.primaryJvm();
	}
	
	/**
	 * Returns an object to represent the secondary JVM.
	 * @throws StfException if secondary JVM not configured. 
	 */
	public JavaVersion secondaryJvm() throws StfException { 
		return environmentCore.secondaryJvm();
	}
	
	/**
	 * @return true if a secondary JVM has been configured.
	 * @throws StfException if argument resolution failed.
	 */
	public boolean secondaryJvmConfigured() throws StfException {
		String javahome = environmentCore.getProperty(Stf.ARG_JAVAHOME_EXECUTE_SECONDARY);
		return javahome.length() > 0;
	}
	
	/**
	 * Shortcut method for checking that the primary JVM is using IBM Java.
	 * @throws StfException 
	 */
	public void isIBMJvm() throws StfException {
		primaryJvm().isIBMJvm();
	}

	/**
	 * Shortcut method for verifying that the primary JVM is using IBM Java.
	 * @throws StfException 
	 */
	public void verifyUsingIBMJava() throws StfException {
		primaryJvm().verifyUsingIBMJava();
	}
	
	/**
	 * Returns the directory of Java home for the currently executing stage.
	 * @return a DirectoryRef pointing at the current Java home.
	 * @throws StfException if called during the initialisation stage.
	 */
	public DirectoryRef getJavaHome() throws StfException {
		return environmentCore.getJavaHome();
	}
	
	/**
	 * Allows a test to obtain a Jar path given a Jar Id. 
	 * @return the jar path in String of a given Jar id. 
	 * @throws StfException if the jar file is not found. 
	 */
	public FileRef getJarLocation(JarId id) throws StfException {
		String relativeJarLocation = environmentCore.getProperty(id.getArgument()).replace("/systemtest-prereqs/", "");
		FileRef jarFileRef = environmentCore.findPrereqFile(relativeJarLocation);
		if (!jarFileRef.asJavaFile().exists()) {
			throw new StfException("Jar file does not exist: " + jarFileRef.getSpec());
		} 
		return jarFileRef;
    }
}
