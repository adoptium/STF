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

import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_OFF;
import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processManagement.apps.MiniClient;
import net.adoptopenjdk.stf.processManagement.apps.MiniServer;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This sample test shows how a test can run with a primary and secondary JVM.
 * It's a variant of SampleClientServer:
 *   - server process. Runs on IBM JVM.
 *   - client processes. Run on Oracle JVM.
 *
 * This test will fail unless told about the secondary JVM by either:
 *   1) Setting the JAVA_HOME_SECONDARY environment variable, or
 *   2) The VM is explicitly supplied as test arguments.
 */
public class SampleSecondaryJvm implements StfPluginInterface {

    private static final Logger logger = LogManager.getLogger(JavaVersion.class.getName());

    public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleSecondaryJvm");
		help.outputText("This test demonstrates running different processes with different JVM's");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		JavaVersion primaryJvm   = test.env().primaryJvm();
		JavaVersion secondaryJvm = test.env().secondaryJvm();
		
		// Sample code to validate that the expected Java implementation is being used for the test.
		//primaryJvm.verifyUsingIBMJava();
		//secondaryJvm.verifyUsingOracleJava();

		// List the java version output for the two JDKs.
		logger.info("java -version output for primary JVM:\n" + primaryJvm.getRawOutput());
		logger.info("java -version output for secondary JVM:\n" + secondaryJvm.getRawOutput());
		
		// Start a server process using IBM Java on the primary JVM
		StfProcess server = test.doRunBackgroundProcess("Run server", "SRV", ECHO_ON, ExpectedOutcome.neverCompletes(), 
				test.createJavaProcessDefinition(primaryJvm)
					.addProjectToClasspath("stf.samples")
					.runClass(MiniServer.class));
		
		// Run several client processes using the secondary JVM. 
		StfProcess[] clients = test.doRunBackgroundProcesses("Run client", "CL", 4, ECHO_OFF, ExpectedOutcome.cleanRun().within("10s"), 
				test.createJavaProcessDefinition(secondaryJvm)
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class));
		
		// Wait for clients to complete, and then kill the server
		test.doMonitorProcesses("Wait for clients to complete", server, clients);
		test.doKillProcesses("Stop server process", server);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}