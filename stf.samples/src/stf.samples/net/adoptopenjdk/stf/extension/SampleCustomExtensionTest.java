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

package net.adoptopenjdk.stf.extension;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.StfTestArguments;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This test demonstrates the use of a test extension.
 * 
 * The extension would usually provide a range of actions that can be used by a 
 * suite of tests.
 * This is a demo test so the actions that the extension provides are only used
 * by this test.
 * 
 * The new custom extension has the same level of power and privilege
 * as the ever present StfCoreExtension. Authors of extension code should
 * take care to provide actions that support the aims of allowing clean and 
 * simple test cases. 
 * 
 * The following files are needed to implement this scenario:
 *   - ExamplePluginInterface.java
 *        Defines setup/execute/teardown methods. These all include 
 *        ExampleExtension in their definition.
 *   - ExampleExtension.java
 *        Provides methods to generate code which run perl subroutines.
 *   - SampleCustomExtensionTest.java
 *        This file. Makes use of the new actions in ExampleExtension.
 *   - stf.samples/config/example.properties
 *        Default values for the arguments that belong to ExampleExtension.
 *   - stf.samples/scripts/ExampleModule.pm
 *        Some perl code which the extension runs.
 * 
 * The extension has a '-show-files' argument. This is a first class STF 
 * argument. It's values can be provided in property files or on the command 
 * line. eg, 
 *   stf -test=SampleCustomExtensionTest -show-files
 * The extension argument is added by:
 *   1) Defining an Argument object in the extension to describe the new argument.
 *   2) The extension returns the argument as a supported one in getSupportedArguments()
 *   3) The extension provides help text about the argument in its help() method.
 *   4) Default values are provided in a <project>/config/<extension-name>.properties file.
 *   
 * This test also has a test specific argument called 'forceFailure'. It defaults 
 * to 'false' but the failure path can be triggered by running with:
 *   stf -test=SampleCustomExtensionTest -test-args=force-failure=true
 *
 * Both the extension and test arguments appear in the help for this test.
 * Run with the following command to see the help text:
 *   stf -test=SampleCustomExtensionTest -help
 */
public class SampleCustomExtensionTest implements ExamplePluginInterface {
	public void help(HelpTextGenerator help) throws Exception {
		help.outputSection("SampleCustomExtension");
		help.outputText("This test demonstrates how tests can run with a custom extension.");

		help.outputSection("SampleCustomExtension options");
		help.outputArgName("force-failure", "true|[false]");
		help.outputArgDesc("Force the test to run the failure path code.");
	}

	public void pluginInit(StfCoreExtension test, ExampleExtension customExtension) throws Exception {
	}

	public void setUp(StfCoreExtension test, ExampleExtension customExtension) throws Exception {
	}

	public void execute(StfCoreExtension test, ExampleExtension customExtension) throws Exception {
		// Pull in a test specific property that can be used to force the code into the failure path
		StfTestArguments testArgs = test.env().getTestProperties("force-failure=[false]");
		boolean forceFailure = Boolean.parseBoolean(testArgs.get("force-failure"));

		DirectoryRef resultsRoot = test.env().getResultsDir();
		
		// Create a text file holding heap data from a simulated run 
		FileRef heapLog = resultsRoot.childFile("example-heap.log");
		String heapLogData = createHeapLogData();
		test.doWriteFile("Create example heap log file", heapLog, heapLogData);	
		
		// Create a text file holding GC data from a simulated run 
		FileRef gcLog = resultsRoot.childFile("example-gc.log");
		String gcLogData = createGcData();
		test.doWriteFile("Create example gc log file", gcLog, gcLogData);
		
		// Verify that we call perl code to check the contents of the heap log
		customExtension.doVerifyHeapLog("Check heap log file", heapLog);

		// Verify that we call perl code to check the contents of the GC log
		customExtension.doVerifyGcLog("Check GC log file", gcLog);

		// If run with the '-test-args=force-failure=true' argument then deliberately cause the test to fail
		if (forceFailure) {
			// Feed a heap log file into the GC verification perl.
			// Will fail with a parsing error. 
			customExtension.doVerifyGcLog("Check GC log file", heapLog);
		}
	}

	
	private String createGcData() throws StfException {
		return "GC agent started at \n"
			 + "GC Start:\n"
			 + "GC Finish:\n"
			 + "GC Start:\n"
			 + "GC Finish:\n"
			 + "GC count at program end: 2\n";
	}

	
	private String createHeapLogData() throws StfException {
		return "HeapReference info: class_tag 123, referrer_class_tag 456, size 789, length 1112\n"
			 + "Heap agent started at \n";
	}

	
	public void tearDown(StfCoreExtension test, ExampleExtension customExtension) throws Exception {
	}
}