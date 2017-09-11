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

package net.adoptopenjdk.stf.environment.properties;


/**
 * This class defines a class to represent STF arguments.
 * 
 * The actual arguments themselves are defined in the STF extension which supports
 * that particular argument.
 */
public class Argument {
	// This is the name of the extension which supports this argument.
	// Only used to help with debugging.
	private String extensionName;
	
	// This is the name of the argument. ie. the value you use on the command line.
	private String argumentName;
	
	// Set if this is a boolean argument. 
	// If a value is supplied then it must be either 'true' or 'false'. 
	// If no value is supplied then the value defaults to true.
	private boolean isBooleanArgument;

	// Values do not have to be supplied for optional arguments, whereas STF will fail 
	// with an error if no value is supplied for a mandatory argument.
	private Required argumentType;
	public enum Required { OPTIONAL, MANDATORY };
	
	// This value is used to show that an argument doesn't actually have a value
	public static String EMPTY_VALUE = "null"; 
	

	/**
	 * Create an argument object.
	 * 
	 * @param extensionName is the name of the extension which supports this argument.
	 * @param argumentName
	 * @param isBooleanArg
	 * @param argumentType
	 */
	public Argument(String extensionName, String argumentName, boolean isBooleanArg, Required argumentType) {
		this.extensionName = extensionName;
		this.argumentName = argumentName;
		this.isBooleanArgument = false;
		this.argumentType = argumentType;
	}
	
	
	public String getExtensionName() {
		return extensionName;
	}
	
	
	public String getName() {
		return argumentName;
	}
	
	
	public boolean isBooleanArgument() { 
		return isBooleanArgument;
	}


	public boolean isOptional() { 
		return argumentType == Required.OPTIONAL;
	}

	
	public String toString() { 
		return "Extension=" + extensionName 
				+ " Name=" + argumentName 
				+ " isBoolean=" + isBooleanArgument 
				+ " type=" + argumentType;
	}
}