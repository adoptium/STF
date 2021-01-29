/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.loadTest;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.StfTestArguments;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginInterface;
import net.adoptopenjdk.stf.util.TimeParser; 

/** 
 * An abstract class to be extended and implemented by STF test plugins that needs the capability to run 
 * in a time-based fashion. 
 * */
public abstract class TimeBasedLoadTest implements StfPluginInterface {
	
	String defaultTimeout = "1h";
	protected String timeLimit;
	protected StfTestArguments testArgs;
	protected boolean isTimeBasedLoadTest = true;
	protected String finalTimeout = defaultTimeout; 

	public void pluginInit(StfCoreExtension stf) throws StfException {
		
		// When used as a time based test, this STF test plugin accepts the following two parameters:
		//   (1) timeLimit : Optional Time duration value (e.g. 5s, 5m, 5h) for which the load should be run. 
		//   (2) workload  : Optional workload value used in a subset of tests (e.g. DAA).
		testArgs = stf.env().getTestProperties("timeLimit=[0]","workload=[none]");
		timeLimit = testArgs.get("timeLimit");
		
		// When We are running an iteration based load test, no timeLimit is specified 
		if ( timeLimit.equals("0")) {
			isTimeBasedLoadTest = false; 
		}

		if (isTimeBasedLoadTest) { 
			long finalTimeoutInSeconds = TimeParser.parseTimeSpecification(defaultTimeout).getSeconds() + 
				TimeParser.parseTimeSpecification(timeLimit).getSeconds();
			finalTimeout = finalTimeoutInSeconds + "s"; 
		}
	}

	public void setUp(StfCoreExtension test) throws StfException {
	}

	public void tearDown(StfCoreExtension stf) throws StfException {
	}
}
