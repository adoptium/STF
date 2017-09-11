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
import net.adoptopenjdk.stf.processManagement.apps.SpeedyApplication;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test shows the concurrent execution of several Java processes.
 * It represents the case in which a test starts 2 or more programs and then 
 * waits for their completion. 
 * 
 * All processes are expected to complete with an exit code of 0, so unlike 
 * the SampleClientServer this sample does not need to kill any processes.
 */
public class SampleConcurrentProcesses implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleConcurrentProcesses");
		help.outputText("This test demonstrates running several processes concurrently");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Start a relatively long lived java process.
		StfProcess processA = test.doRunBackgroundProcess("Run process A", "A", ECHO_ON, ExpectedOutcome.cleanRun().within("1m"),
				test.createJavaProcessDefinition()
				    .addJvmOption("-Xmx100M")
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class));
		
		// Now start a short lived java process.
		StfProcess processB = test.doRunBackgroundProcess("Run process B", "B", ECHO_ON, ExpectedOutcome.cleanRun().within("20s"),
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(SpeedyApplication.class));
		
		// Now start a several short lived java process, all using same classpath and options.
		StfProcess[] processesC = test.doRunBackgroundProcesses("Run processes C", "C", 4, ECHO_OFF, ExpectedOutcome.cleanRun().within("20s"),
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(SpeedyApplication.class));
		
		// Wait for processes A, B and C to complete (ie. all 6 child processes).
		// This also monitors the referenced processes for core and java dumps.
		// doMonitor fails the test if any clients don't complete within the expected
		// run time.
		// If this line is removed then STF aborts the run because it determines that
		// running the test would leave an orphan process behind.
		// The test will only pass if all monitored processes complete within the
		// expected time limits and with an exit code of 0.
		test.doMonitorProcesses("Wait for clients to complete", processA, processB, processesC);
		
		// Attempt to kill the child processes.
		// Except that you can't!! When the monitor command completes then all processes 
		// must have finished, due to one of:
		//   1) all processes complete as expected (exit code 0 in this case)
		//   2) a process crashed or completed with unexpected exit code.
		//      This will kill the remaining processes, and abort the test run.
		// So STF won't allow you to kill any of the processes as it determines that 
		// they have all finished by this point.
		// The following doKill lines are listed below to show the interaction of 
		// the doStart, doMonitor and doKill methods. Uncomment one and rerun to see
	    // the error message. 
		//test.doKillProcesses("Stop process A", processA);
		//test.doKillProcesses("Stop process B", processB);
		//test.doKillProcesses("Stop process C", processesC);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
