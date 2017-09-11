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

package net.adoptopenjdk.stf.environment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;


/**
 * This class represents the contents of STFs '-test-args' argument.
 *
 * It enforces error checking to ensure that:
 *   - values are supplied for all non defaulting arguments.
 *   - no excess arguments are supplied.
 *
 * It also allows callers to convert from a test-arg to a corresponding enum constant.
 * This is not a complex operation but using this class provides automatic error 
 * checking and generates sensible error messages for invalid values. 
 */
public class StfTestArguments {
	private HashMap<String, String> testArgs;
	
	public StfTestArguments(String testArgsString, String... expectedTestArgs) throws StfException {
		// Build a set of expected property names.
		// Also set test property defaults, if described in the expected description
		HashSet<String> expectedPropertySet = new HashSet<String>();
		testArgs = new HashMap<String, String>();
		for (String expected : expectedTestArgs) {
			// Add to the set of property names
			String[] expectedParts = expected.trim().split("=");
			String expectedName = expectedParts[0];
			expectedPropertySet.add(expectedName);
			
			// Optionally create a default name/value
			if (expectedParts.length > 1) {
				String defaultValue = expectedParts[1];
				if (!defaultValue.startsWith("[") && !defaultValue.endsWith("]")) {
					throw new StfException("Default value not enclosed in within '[' and ']': '" + expected + "'");
				}
				defaultValue = defaultValue.substring(1, defaultValue.length()-1);
				testArgs.put(expectedName, defaultValue);
			}
		}
		
		// Process the name value pairs in '-test-args'
		if (!testArgsString.isEmpty()) {
			for (String nameValuePair : testArgsString.split(",")) {
				String[] singleValue = nameValuePair.trim().split("=");
				if (singleValue.length != 2) {
					throw new StfError("Failed to parse test specific arguments as name/value pairs. Value: '" + nameValuePair + "'");
				}
				testArgs.put(singleValue[0], singleValue[1]);
			}
		}
		
		// Check for missing properties. 
		// i.e. properties which were expected but have not been supplied for this test run.
		HashSet<String> missing = new HashSet<String>(expectedPropertySet);
		missing.removeAll(testArgs.keySet());
		if (!missing.isEmpty()) {
			throw new StfError("Test specific properties not supplied: '" + missing + "'. "
					+ "Allowed arguments are: " + Arrays.toString(expectedTestArgs));
		}

		// Check for any extra properties.
		// i.e. The test has been started with test specific properties that the plugin is not expecting.
		HashSet<String> actuals = new HashSet<String>(testArgs.keySet());
		actuals.removeAll(expectedPropertySet);
		if (!actuals.isEmpty()) {
			throw new StfError("Unknown test specific argument: '" + actuals + "'. "
					+ "Allowed arguments are: " + Arrays.toString(expectedTestArgs));
		}
	}
	
	
	/**
	 * Converts an argument to it's corresponding enum equivalent.
	 * 
	 * The main benefit of this method over a simple enum lookup is that it enforces
	 * error checking and produces sensible error messages for invalid argument values.
	 *  
	 * @param argName is the name of the argument.
	 * @param enumType is the class of enums to convert the value to
	 * @return The enum constant for the value of argName.
	 * @throws StfException if no enum constant can be found for the argument.
	 */
	public <E extends Enum<E>> E decodeEnum(String argName, Class<E> enumType) throws StfException {
		String argValue = get(argName);

    	// Step through all enum values looking for a match
    	E result = null;
	    for (E enumValue : enumType.getEnumConstants()) {
		    if (argValue.equals(enumValue.name())) {
		    	result = enumValue;
		    	break; // found it
		    }
		}

	    // Fail if we weren't able to convert the argument value to an enum value
	    if (result == null) {
	    	// Build string with names of all possible enum values
	    	StringBuilder argNames = new StringBuilder();
	    	for (E enumValue : enumType.getEnumConstants()) {
	    		if (argNames.length() > 0) { 
	    			argNames.append(", ");
	    		}
	    		argNames.append(enumValue.name());
	    	}
	    	
			throw new StfError("Invalid argument value for '" + argName + "'."
					+ " Value of '" + argValue + "' is not one of : [" + argNames.toString().trim() + "]");
	    }
	    
	    return result;
	}

	
	/**
	 * Gets the value of a name test argument. 
	 * @param argName is the name of the argument to get the value of.
	 * @return the value of the specified test argument.
	 * @throws StfException if there is no value for the specified argument.
	 */
	public String get(String argName) throws StfException {
		if (!testArgs.containsKey(argName)) {
			throw new StfException("No test argument specified for '" + argName + "'");
		}
		
		return testArgs.get(argName);
	}
	

	/**
	 * Returns the value of a test argument which points to a JVM directory.
	 * 
	 * @param jvmHomeArg is the name of the argument which holds the location of the JVM.
	 * @return A DirectoryRef object pointing at the JVM directory.
	 * @throws StfException if the argument has not been provided or if it 
	 * doesn't point at a valid JVM.
	 */
	public DirectoryRef getAsJvmHomeDirectory(String jvmHomeArg) throws StfException {
		String jvmHomeString = get(jvmHomeArg);
		DirectoryRef jvmHome = new DirectoryRef(jvmHomeString);
		
		// Validate that we really are pointing at a JVM image
		if (!jvmHome.exists()) {
			throw new StfException("Argument '" + jvmHomeArg + "' doesn't point to a JVM image. Directory does not exist");
		}
		FileRef java = jvmHome.childFile("bin/java");
		FileRef javaExe = jvmHome.childFile("bin/java.exe");
		if (!(java.asJavaFile().exists() || javaExe.asJavaFile().exists())) {
			throw new StfException("Argument '" + jvmHomeArg + "' doesn't point to a JVM image. Image doesn't contain java at either '" + java + "' or '" + javaExe + "'");
		}
		DirectoryRef confDir = jvmHome.childDirectory("conf");
		if (!confDir.exists()) {
			throw new StfException("Argument '" + jvmHomeArg + "' doesn't point to a JVM image. Directory does not exist at '" + confDir + "'");
		}
		
		return jvmHome;
	}
}