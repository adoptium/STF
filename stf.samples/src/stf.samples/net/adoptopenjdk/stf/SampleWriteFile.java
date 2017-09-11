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

import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test makes use of the api to write a file.
 */
public class SampleWriteFile implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws StfException {
		help.outputSection("SampleWriteFile");
		help.outputText("This test demonstrates file writing.");
	}

	public void pluginInit(StfCoreExtension stf) throws StfException {
	}

	public void setUp(StfCoreExtension test) throws StfException {
	}

	public void execute(StfCoreExtension test) throws StfException {
		// Create some sort of content which needs to be written to an output file.
		String fileContent = 
				  "userName=bob\n"
				+ "password=secret\n"
				+ "outputPort = 233\n"
				+ "PATH=C:\\path\\\\temp\\\\\\\\rest\n";  // 1, 2 & 4 backslash characters
		
		// Write the dummy configuration to a new file
		FileRef outputFile = test.env().getTmpDir().childFile("SampleConfig.properties");
		test.doWriteFile("Sample file write", outputFile, fileContent);
	}

	public void tearDown(StfCoreExtension stf) throws StfException {
	}
}
