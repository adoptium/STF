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

package net.adoptopenjdk.stf.processManagement;

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import net.adoptopenjdk.stf.TestArgumentProcessing;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.results.TestResultsProcessor;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test shows the execution of some JUnit tests.
 * The tests it runs are the internal STF unit tests.
 */
public class SampleJUnitTestRun implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleRunJUnit");
		help.outputText("This test demonstrates a JUnit test run.");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// List the JUnit tests to run
		Class<?>[] junitTests = {
			TestArgumentProcessing.class,
			TestResultsProcessor.class
		};
		
		// Build a description of how to run JUnit for these tests
		JavaProcessDefinition junitProcessDefinition = test.createJUnitProcessDefinition("stf.samples", null, junitTests);
		
		// Synchronously run the JUnit tests
		test.doRunForegroundProcess("Run JUnit tests", "J", ECHO_ON, 
						ExpectedOutcome.cleanRun().within("1m"), 
						junitProcessDefinition);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
