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

package net.adoptopenjdk.stf;

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.LoadTestProcessDefinition;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This is a sample test which demonstrates a failing load test.
 * It also shows the usage of the load test arguments to control error reporting 
 * and aborting when there are too many failures:
 *    LoadTestProcessDefinition.setReportFailureLimit(int)
 *    LoadTestProcessDefinition.setAbortAtFailureLimit(int)
 * 
 * It can be used as a STF regression test to verify:
 *   - STF reporting of first failure.
 *   - 2nd and subsequent failures not reported in detail.
 *   - Load test exit when failure limit reached.
 *   - Load test requests javacore on first failure.
 *   - Process management passing a test which fails (ie, as expected).
 *
 */
public class SampleFailingLoadTest implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws StfException {
		help.outputSection("Runs a failing load test.");
	}
	
	public void pluginInit(StfCoreExtension stfCore) throws Exception {
	}

	public void setUp(StfCoreExtension stfCore) throws Exception {
	}

	public void execute(StfCoreExtension stfCore) throws Exception {
		String inventoryFile = "/stf.samples/config/inventories/sampleLoadTest/sampleFailingInventory.xml";
		
		LoadTestProcessDefinition loadTestSpecification = stfCore.createLoadTestSpecification()
				.addProjectToClasspath("stf.samples")
				.addPrereqJarToClasspath(JavaProcessDefinition.JarId.JUNIT)
				.addPrereqJarToClasspath(JavaProcessDefinition.JarId.HAMCREST)
				.setReportFailureLimit(1) 			// Only report the first failure in detail
				.setAbortAtFailureLimit(4)			// Abandon the run after 4 failures 
				.addSuite("suite")
				.setSuiteInventory(inventoryFile)	// Point to inventory file which has failing test
 				.setSuiteThreadCount(1)				// Run with only 1 worker thread
 				.setSuiteNumTests(25)				// Plan to run 25 tests
 				.setSuiteSequentialSelection();	    // Sequential run test. Only 1 test so a bit academic
		
		// Run load test and wait for it to finish
		stfCore.doRunForegroundProcess("Run load test for project", "LT", ECHO_ON, ExpectedOutcome.exitValue(1).within("1m"), 
				loadTestSpecification);
		
		// Verify expected tests failed 
		FileRef loadTestStdout = stfCore.env().getResultsDir().childFile("1.LT.stdout");
		stfCore.doCountFileMatches("Verify first failure reported", loadTestStdout, 1, "junit.framework.AssertionFailedError: Wrong value of pi");
		stfCore.doCountFileMatches("Verify subsequent failure not reported", loadTestStdout, 0, "junit.framework.AssertionFailedError: Addition failed");
		stfCore.doCountFileMatches("Verify 3 failures detected but not reported", loadTestStdout, 3, "Test failed. Details recorded in execution log");
		stfCore.doCountFileMatches("Verify overall failure count", loadTestStdout, 1, "Failed  : 4$");
		
		// Verify javacore requested
		FileRef loadTestStderr = stfCore.env().getResultsDir().childFile("1.LT.stderr");
		if (stfCore.env().primaryJvm().isIBMJvm()) {
			stfCore.doCountFileMatches("Verify javacore requested", loadTestStderr, 1, "User requested Java dump");
		} else {
			stfCore.doCountFileMatches("Verify javacore requested", loadTestStdout, 1, "Not creating dumps as not running on an IBM JVM");
		}
	}

	public void tearDown(StfCoreExtension stfCore) throws Exception {
	}
}