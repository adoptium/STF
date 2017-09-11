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
 * This sample test shows how to control several concurrent processes.
 * It represents the fairly common client/server style test. This typically has 2 
 * main parts: 
 *   - server process which never completes
 *   - client processes. These use the server and exit on completion.
 * 
 * It is important that there are no processes still running at the end of the test, so 
 * note that the test kills the server process before completion.
 * 
 * STF takes cares of many details when running concurrent processes:
 *   - prevents orphan processes by aborting a run in which a process is left running.
 *   - tries to help ensure clean logical code by only allowing monitoring of running processes.
 *   - kills all running processes if a test run aborts due to an error.
 *   - monitors running process and captures core files.
 *   - fails a test run if a running process exceeds its allowed run time.
 */
public class SampleClientServer implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleClientServer");
		help.outputText("This test demonstrates running several processes concurrently");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Start a sever process. This will run indefinitely and needs to be killed at the end of the test
		StfProcess server = test.doRunBackgroundProcess("Run server", "SRV", ECHO_ON, ExpectedOutcome.neverCompletes(), 
				test.createJavaProcessDefinition()
				    .addJvmOption("-Xmx100M")
					.addProjectToClasspath("stf.samples")
					.runClass(MiniServer.class)
					.addArg("99"));	// Argument is not used. Only added to show usage	
		
		// Run several client processes. 
		// These processes will exit with a '0' value when they complete.
		// Each client needs to decide if the test has worked and only exit with 0 when it has.
		StfProcess[] clients = test.doRunBackgroundProcesses("Run client", "CL", 4, ECHO_OFF, ExpectedOutcome.cleanRun().within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class));
		
		// Wait for clients to complete.
		// This also monitors the server and client processes for core and java dumps.
		// Also fails the test if the clients don't complete within the expected run time.
		test.doMonitorProcesses("Wait for clients to complete", server, clients);
		
		// kill the server process
		test.doKillProcesses("Stop server process", server);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
