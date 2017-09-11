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

package net.adoptopenjdk.stf.plugin.interfaces;

import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/** 
 * An interface to provide the simplest possible interface for test plugins.
 * 
 * This can be used by plugins which don't need to make use of any extensions 
 * beyond 'StfCore'.
 */
public interface StfPluginInterface extends StfPluginRootInterface {

	public void help(HelpTextGenerator help) throws Exception;
	
	public void pluginInit(StfCoreExtension svt) throws Exception;

	public void setUp(StfCoreExtension svt) throws Exception;
	
	public void tearDown(StfCoreExtension svt) throws Exception;
}