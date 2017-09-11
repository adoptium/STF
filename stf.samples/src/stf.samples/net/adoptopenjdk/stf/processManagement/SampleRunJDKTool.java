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

import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processManagement.apps.MiniClient;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This simple example runs one of the JDK tools, which ships with the JVM.
 * It also runs several java processes in the background.
 * 
 * If you need to start a JDK tool/utility which will run in the background 
 * until the end of the test then you'll probably need to kill it at the end 
 * of the test. See SampleClientServer.java for an example of running with a 
 * process which will never complete, and therefore needs killing at the end 
 * of the run.  
 */
public class SampleRunJDKTool implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleRunJDKTool");
		help.outputText("This test runs the keytool application.");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		// Create a keystore using a background process. 
		FileRef keystoreFile = test.env().getTmpDir().childFile("mykeystore");
		StfProcess keytool = test.doRunBackgroundProcess("Run keytool", "KEY", ECHO_ON, ExpectedOutcome.cleanRun().within("10s"),
			    test.createJDKToolProcessDefinition()
				        .setJDKToolOrUtility("keytool")
				        .addArg("-genkeypair")
				        .addArg("-dname", "\"cn=John Example, ou=Java, o=IBM, c=UK\"")
				        .addArg("-alias", "ks1")
				        .addArg("-keypass", "private-key-password")
				        .addArg("-keystore", keystoreFile.getSpec())
				        .addArg("-storepass", "keystore-password")
				        .addArg("-validity", "365"));
		
		// Start 4 java processes in the background. 
		// These processes will exit with a '0' value when they complete.
		// Each client needs to decide if the test has worked and only exit with 0 when it has.
		StfProcess[] clients = test.doRunBackgroundProcesses("Run client", "CL", 4, ECHO_OFF, ExpectedOutcome.cleanRun().within("10s"), 
				test.createJavaProcessDefinition()
					.addProjectToClasspath("stf.samples")
					.runClass(MiniClient.class));
		
		// Wait for keytool and all of the java processes to complete.
		// This also monitors the keytool and java processes for core and java dumps.
		// Also fails the test if the clients don't complete within the expected run time.
		test.doMonitorProcesses("Wait for clients to complete", keytool, clients);
		
		// Verify that the keystore file now exists
		test.doValidateFileExists("Check keystore created", keystoreFile);
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
