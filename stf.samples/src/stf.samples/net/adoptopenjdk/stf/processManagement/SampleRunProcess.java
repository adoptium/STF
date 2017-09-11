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
public class SampleRunProcess implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleRunProcess");
		help.outputText("This test demonstrates the synchronous running of processes.");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Synchronously run a single java process.
		// This call with start the java process and await its completion.
		// STF will monitor the process for core and java dumps. 
		test.doRunForegroundProcess("Run client", "CL", ECHO_ON, ExpectedOutcome.exitValue(3).within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class)
					.addArg("3"));
		
		// Synchronously run 4 instances of a java process.
		// STF will wait for all 4 processes to complete with an exit value of 3.
		test.doRunForegroundProcesses("Run client", "CL", 4, ECHO_ON, ExpectedOutcome.exitValue(3).within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class)
					.addArg("3"));
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
