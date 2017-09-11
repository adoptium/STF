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
 * This sample test demonstrates how tests can validate absence of a file.
 */
public class SampleValidateFileAbsence implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws StfException {
		help.outputSection("SampleValidateFileAbsence");
		help.outputText("This test demonstrates how to validate absence of a file.");
	}

	public void pluginInit(StfCoreExtension stf) throws StfException {
	}

	public void setUp(StfCoreExtension test) throws StfException {
	}

	public void execute(StfCoreExtension test) throws StfException {
		// For demonstration purposes, create reference to a fake file
		FileRef fileToBeAbsent = test.env().getJavaHome().childFile("aFakeFile.txt");
		
		// Pass reference to a fake file to doValidateFileAbsent(). 
		// Since this test passes only if the given file is not present,Our test should pass
		test.doValidateFileAbsent("Validate file is absent", fileToBeAbsent);
	}

	public void tearDown(StfCoreExtension stf) throws StfException {
	}
}
