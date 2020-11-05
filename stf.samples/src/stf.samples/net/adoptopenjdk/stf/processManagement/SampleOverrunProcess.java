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

import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processManagement.apps.MiniClient;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test shows the synchronous running of a Java process.
 * When executed STF will:
 *   - start the processes 
 *   - monitor the process for core and java dumps.
 *   - fail the test if the process runs for longer than the expected run time.
 *   - kill the process if a test failure is detected.
 * 
 * This is the most basic scenario covered by the STF process control methods.
 */
public class SampleOverrunProcess implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleRunProcess");
		help.outputText("This test demonstrates the synchronous running of processes.");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Runs a java process with two arguments.
		// The first argument tells MiniClient to exit with a zero exit code.
		// The second argument tells MiniClient to sleep for 3 minutes.
		// doRunForegroundProcess is set to expect the process to finish within 10 seconds, which it will not
		// because it is sleeping for 3 minutes.
		// STF will then take core dumps of the running process three times at thirty second intervals before killing it.
		// This sample can therefore be used to test the hang detection and core dumping capability of STF.
		// Run via 'make test.sampleOverrunProcess'. 
		test.doRunForegroundProcess("Run client", "CL", ECHO_ON, ExpectedOutcome.exitValue(0).within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class)
					.addArg("0")
					.addArg("180000"));  // 180 secs in milliseconds
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
