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

package net.adoptopenjdk.stf.extensions.interfaces;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.extensions.StfExtensionBase;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * All STF extension classes must implement this interface.
 */
public interface StfExtension {
	/**
	 * When this method is called the extension must return an array of the arguments 
	 * which it understands and supports.
	 * 
	 * The project should also contain a property file in which all of the supported 
	 * arguments have default values defined, even if the value is only 'null'.
	 * The property file should live in the config directory of the extensions 
	 * project, and be placed in a file '<extension-name>.properties', where 
	 * 'extension-name' is the name of the java file containing the extension.
	 * 
	 * Note that STF will throw an exception if 2 or more extensions report that they
	 * support the same argument.
	 */
	public Argument[] getSupportedArguments();
	
	/**
	 * Stf has a help option and to produce full help each extension must describe the 
	 * options which it supports. 
	 * @param help is a utility class which formats and outputs the help information. 
	 */
	public void help(HelpTextGenerator help);
			
	/**
	 * This method will be called when an extension needs to initialise itself.
	 * The extension should fail if it detects any failure whatsoever.
	 * 
	 * @param environmentCore provides access to the current STF environment.
	 * @param extensionBase is the base layer shared across all extensions.
	 * @param generator gives access to the current perl output file.
	 * @throws StfException if initialisation fails in any way.
	 */
	public void initialise(StfEnvironmentCore environmentCore, StfExtensionBase extensionBase, PerlCodeGenerator generator) throws StfException;
}
