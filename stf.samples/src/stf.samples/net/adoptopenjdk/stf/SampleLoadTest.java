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

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.definitions.JavaProcessDefinition;
import net.adoptopenjdk.stf.processes.definitions.LoadTestProcessDefinition;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;
import net.adoptopenjdk.loadTest.InventoryData;

/** 
 * This sample test demonstrates the STF interface to the load test program.
 * It's a reasonably complex test as it runs 2 suites in multiple threads, and 
 * shows the usage of some of the less rarely used arguments.
 * 
 * For a much simpler example see SampleMauveLoadTest.java
 */
public class SampleLoadTest implements StfPluginInterface {
	public void help(HelpTextGenerator help) throws StfException {
		help.outputSection("Runs a load test.");
	}
	
	public void pluginInit(StfCoreExtension stfCore) throws Exception {
	}

	public void setUp(StfCoreExtension stfCore) throws Exception {
	}

	public void execute(StfCoreExtension stfCore) throws Exception {
		String inventoryFile1 = "/stf.samples/config/inventories/sampleLoadTest/sampleInventory.xml";
		String inventoryFile2 = "/stf.samples/config/inventories/sampleLoadTest/subtests/arbitraryJavaInventory.xml";
		int numTests = InventoryData.getNumberOfTests(stfCore, inventoryFile1);
				
		LoadTestProcessDefinition loadTestSpecification = stfCore.createLoadTestSpecification()
				.addProjectToClasspath("stf.samples")
				.addPrereqJarToClasspath(JavaProcessDefinition.JarId.JUNIT)
				.addPrereqJarToClasspath(JavaProcessDefinition.JarId.HAMCREST)
				.setTimeLimit("12s")				// Don't start any tests after 12 seconds
			    .setMaxTotalLogFileSpace("500M")    // Optional. Prevent logging from exceeding 500M of log files
			    .setMaxSingleLogSize("1/50")        // Optional. Run with limit of 50 logs, each up to 10M. 
				.addSuite("suite1")					// Arguments for the first suite follow
 				.setSuiteThreadCount(Runtime.getRuntime().availableProcessors()-3, 2,16)  // Leave 1 cpu for the JIT, 1 for GC, 1 for the other suite. But always run at least two threads and never more than 16
 				.setSuiteInventory(inventoryFile1)	//   Point at the file which lists the tests. There are no exclusion files.
 				.setSuiteNumTests(numTests * 10)    //   Number of tests to run varies with size of inventory file
 				.setSuiteTestRepeatCount(3)		    //   Run each test 3 times 
 				.setSuiteThinkingTime("5ms", "75ms")//   Waiting time between tests is randomly selected between 5ms and 75ms. Can also use 's' for seconds.
 				.setSuiteSequentialSelection()	    //   Not random selection. Sequential from start. eg, 0,1,2,3,4,5,0,1,...
				.addSuite("suite2")					// Add 2nd (optional) suite
				.setSuiteThreadCount(1)			    //   Run in a single thread
				.setSuiteInventory(inventoryFile2)	//   Use the sample inventory file, which has 2 exclusion files.
				.setSuiteNumTests(1000)			    //   Run 1000 tests in total
				.setSuiteTestRepeatCount(25)	    //   Run which ever test is picked 25 times before picking next test 
				.setSuiteRandomSelection();		    //   Randomly choose test to run. Note this suite doesn't have a 'thinking' time.
		
		// Run load test and wait for it to finish
		stfCore.doRunForegroundProcess("Run load test for project", "LT", ECHO_ON, ExpectedOutcome.cleanRun().within("5m"), 
				loadTestSpecification);
	}

	public void tearDown(StfCoreExtension stfCore) throws Exception {
	}
}