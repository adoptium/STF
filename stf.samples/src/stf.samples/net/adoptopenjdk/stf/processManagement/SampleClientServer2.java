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

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_OFF;
import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processManagement.apps.MiniClient;
import net.adoptopenjdk.stf.processManagement.apps.MiniServer;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample is a variant of the SampleClientServer test.
 * Please see that test for the background on running multiple processes.
 * 
 * This test runs the following processes:
 *   - server process which never completes
 *   - client processes. exits with value 0.
 *   - a failing client process. exits with value of 5.
 */
public class SampleClientServer2 implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleClientServer2");
		help.outputText("This test demonstrates running several processes concurrently, including "
				+ "a client process which is expected to complete with an error");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Start a sever process. This will run indefinitely and needs to be killed at the end of the test
		StfProcess server = test.doRunBackgroundProcess("Run server", "SRV", ECHO_ON, ExpectedOutcome.neverCompletes(),
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniServer.class));
		
		// Run several client processes. 
		// These processes will exit with a '0' value when they complete.
		// Each client needs to decide if the test has worked and only exit with 0 when it has.
		StfProcess[] clients = test.doRunBackgroundProcesses("Run client", "CL", 4, ECHO_OFF, ExpectedOutcome.cleanRun().within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class));
		
		// Start a failing client
		// We expect this process to fail and finish with an exit value of 5. 
		// Any other outcome is a test failure.
		StfProcess failingClient = test.doRunBackgroundProcess("Run failing client", "CLx", ECHO_ON, ExpectedOutcome.exitValue(5).within("5s"),
				test.createJavaProcessDefinition()
				    .addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class)
					.addArg("5"));

		// Wait for clients to complete.
		test.doMonitorProcesses("Wait for clients to complete", server, clients, failingClient);
		
		// kill the server process
		test.doKillProcesses("Stop server process", server);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
