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

import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processManagement.apps.SpeedyApplication;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test demonstrates how tests can echo the contents of a file.
 */
public class SampleEchoFile implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws StfException {
		help.outputSection("SampleEchoFile");
		help.outputText("This test demonstrates how to echo a file.");
	}

	public void pluginInit(StfCoreExtension stf) throws StfException {
	}

	public void setUp(StfCoreExtension test) throws StfException {
	}

	public void execute(StfCoreExtension test) throws StfException {
		// Run a short lived java process
		JavaProcessDefinition speedyAppDefinition = test.createJavaProcessDefinition()
			.addProjectToClasspath("stf.samples")
			.runClass(SpeedyApplication.class);
		StfProcess speedyAppProcess = test.doRunForegroundProcess("Run speedy app", "SA", ECHO_ON, ExpectedOutcome.cleanRun().within("20s"), speedyAppDefinition);
		
		// Show output from SpeedyApplication on the STF standard output
		FileRef targetFile = speedyAppProcess.getStdoutFileRef();
		test.doEchoFile("Echo speedy app output", targetFile);
	}

	public void tearDown(StfCoreExtension stf) throws StfException {
	}
}
