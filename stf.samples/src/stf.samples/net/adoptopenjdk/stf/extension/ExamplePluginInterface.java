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

import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.plugin.interfaces.StfPluginRootInterface;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This interface defines an interface for test plugins to use the ExampleExtension.
 * 
 * Note that the arguments must be the same for the init, setup and teardown methods.
 */
public interface ExamplePluginInterface extends StfPluginRootInterface {
	public void help(HelpTextGenerator help) throws Exception;

	public void pluginInit(StfCoreExtension test, ExampleExtension customExtension) throws Exception;

	public void setUp(StfCoreExtension test, ExampleExtension customExtension) throws Exception;
	
	public void tearDown(StfCoreExtension test, ExampleExtension customExtension) throws Exception;
}
