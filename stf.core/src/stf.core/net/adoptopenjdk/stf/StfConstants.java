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

import java.util.HashMap;

/**
 * This class holds key STF constants.
 * 
 * The aim of the file is to pull together information to prevent duplication 
 * inside STF.
 */
public class StfConstants {
	// This is the name of the Eclipse project holding STF source code.
	public static String STF_PROJECT_NAME = "stf.core";

	// STF jar files must start with this prefix. 
	public static String STF_JAR_PREFIX = "stf";
	
	// The name of the directory STF creates to contain test results 
	public static String RESULTS_DIR = "results";
	
	// Project configuration can also be done through a stf classpath file. 
	// This is used by projects which require a newer version of Java than 
	// that supported by Eclipse.
	public static String STF_CLASSPATH_XML_FILE = "stfclasspath.xml";
	
	// String to be used as a prefix on all failure messages. 
	// Using a consistent text string makes it easier to search log files for failures
	public static String FAILURE_PREFIX = "**FAILED** ";

	// Inventory files are copied to a directory in results with this suffix
	public static String INVENTORY_DIR_SUFFIX = "inventory";
	
	// The following strings can be used command arguments, with the actual 
	// value being substituted in at the time of code generation.
	public static String PLACEHOLDER_STF_COMMAND_NUMBER   = "${{STF-COMMAND-NUMBER}}";    // Eg, 1, 2, 3, etc
	public static String PLACEHOLDER_STF_COMMAND_MNEMONIC = "${{STF-COMMAND-MNEMONIC}}";  // Eg, SCL or XYZ, etc
	public static String PLACEHOLDER_STF_PROCESS_INSTANCE = "${{STF-PROCESS-INSTANCE}}";  // Eg, 1, 2, etc, or ''. Multiple process instance: 1,2,3,etc, or empty string for single instance. Replacement done in perl code

	//When the Stf Java creates the perl that runs a test, sometimes we want to tell it to take data related to one process and e.g. pass it to another process.
	//This HashMap contains a list of data you can get from the perl object representing a process at run time. Like the process id of a test you just started.
	public static Integer PERL_PROCESS_PID = 1;
	public static HashMap<Integer,String> PERL_PROCESS_DATA = new HashMap<Integer,String>();
	static {
		PERL_PROCESS_DATA.put(PERL_PROCESS_PID,"->{pid}");
	}
}
