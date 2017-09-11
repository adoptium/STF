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

import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.ModuleRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processes.definitions.JmodDefinition;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This sample test shows the execution of the Java 9 jmod utility.
 */
public class SampleRunJmod implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleRunJmod");
		help.outputText("This test demonstrates the running of a jmod process.");
	}

	public void pluginInit(StfCoreExtension stf) throws Exception {
	}

	public void setUp(StfCoreExtension test) throws Exception {
	}

	public void execute(StfCoreExtension test) throws Exception {
		DirectoryRef dir1 = test.env().findTestDirectory("pmb.module/mods/com.greetings");
		DirectoryRef dir2 = test.env().findTestDirectory("pmb.module/mods/org.astro");
		
		// Run jmod with the minimal possible arguments.
		ModuleRef jmodRef1 = test.doCreateJmod("Create minimalist jmod",
			new JmodDefinition()
				.doJmodCreate("com.greetings.jmod")
				.addDirectoryToClassPath(dir1));
		
		// Verify that a jmod file was created
		// NB: Test code does not need to do this. The test will fail if the jmod file was not created.
		test.doValidateFileExists("Check miniMod created", jmodRef1.getJarFileRef());

		// Delete the first jmod, so that another with the same name can be created
		test.doRm("Delete first module", jmodRef1.getJarFileRef());
		
		
		// Now run the jmod command with virtually all possible options.
		// Note: This sample aims to demonstrate the different methods available. 
		// It does aim to represent a realistic jmod command!
		ModuleRef jmodRef = test.doCreateJmod("Run jmod with lots of arguments",
			new JmodDefinition()
				.doJmodCreate("com.greetings.jmod")
				.addDirectoryToClassPath(dir1)
				.addDirectoryToClassPath(dir2)
				.addDirectoryToCmdsPath(dir1)
				.addDirectoryToCmdsPath(dir2)
				.addDirectoryToConfigPath(dir1)
				.addDirectoryToConfigPath(dir2)
				.setExcludePattern("excludePattern")
				.setHashModulesPattern("HDP")
				.addDirectoryToLibsPath(dir1)
				.addDirectoryToLibsPath(dir2)
				.setMainClass(StfCoreExtension.class)
				.setModuleVersion("1.2")
				.addDirectoryToModulepath(dir1)
				.addDirectoryToModulepath(dir2)
				.setOsArchToCurrentPlatform()
				.setOsNameToCurrentPlatform()
				.setOsVersionToCurrentPlatform());
		
		// Verify that a jmod file was created
		test.doValidateFileExists("Check jmod created", jmodRef.getJarFileRef());
		
		// Run 'jmod list' against the new 'com.greetings.jmod'
		test.doCreateJmod("Run jmod list", new JmodDefinition().doJmodList(jmodRef));
		
		// Run 'jmod describe' against the new 'com.greetings.jmod'
		test.doCreateJmod("Run jmod describe", new JmodDefinition().doJmodDescribe(jmodRef));
	}

	public void tearDown(StfCoreExtension stf) throws Exception {
	}
}
