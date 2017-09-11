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

package net.adoptopenjdk.stf.processes.definitions.generic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;


/**
 * This class represents a single argument for an external process.
 * 
 * It aims to reduce the work needed by each process definition object. 
 * There are quite a lot of different process definitions so their repetitive
 * code, such as building a classpath, can all be contained within this class. 
 */
public class ProcessArg {
	public enum ARG_TYPE {
		STRING(false),    // For pure string values. Argument value output as-is, with no processing. eg, '--endian little'
		MULTI_VAL(true),  // For arguments with multiple values. Frequently comma separated. eg, '--add-modules mod1,mod2,mod3'
		REPEAT_ARG(true), // For multiple values arguments where the argument name is repeated, '--release-info=file1 --release-info=file2'
		CLASS(false),     // For output of the full name of a class. Contains name of package and enclosing class. eg, '--main-class net.adoptopenjdk.hello'
		FLAG(false),      // Toggle for an argument which doesn't have any argument values. eg, '--version'
		PATH(true),       // For specification of path with 1 or more directories. eg, '--class-path dir1:dir2:dir3'
		DIR(false),       // For an argument whose value must be a directory. eg, '-output /tmp/dir2' 
		FILE(false);      // For an argument whose value must be a file. eg, '-log /tmp/process.log' 
		
		boolean allowMultipleValues;
		
		ARG_TYPE(boolean allowMultipleValues) {
			this.allowMultipleValues = allowMultipleValues;
		}
	}
	
	public enum REQUIREMENT { MANDATORY, OPTIONAL };
	
	// Metadata about the argument
	private ARG_TYPE argType;
	private String nameValueSeparator;
	private String argSeparator;
	private REQUIREMENT argRule;
	private String argId;
	private String argName;

	// Data for STRING, COMMA_SEP, CLASS, PATH, and DIR arguments
	private LinkedHashSet<String> argValues;
	
	// Data for FLAG arguments
	private Boolean argEnabled;
	
	// Data for FILE arguments
	private FileRef argFile;
	
	
	/**
	 * Define a process argument.
	 * @param argType is the type of argument
	 * @param nameValueSeparator is a string used between the argument and its value. Typically " ", or "=".
	 * @param argSeparator is the string to be used between multiple argument values. eg ',' for '-addmods=mimimod,bigmod'
	 * @param argRule specifies if the argument is mandatory or optional.
	 * @param argId is a ID name for the argument. Only used for error messages.
	 * @param argName contains the text used to invoke the argument. 
	 * Use 'null' for arguments which don't have an argument name and only specify a value.
	 */
	public ProcessArg(ARG_TYPE argType, String nameValueSeparator, String argSeparator, REQUIREMENT argRule, String argId, String argName) {
		this.argType = argType;
		this.nameValueSeparator = nameValueSeparator;
		this.argSeparator = argSeparator;
		this.argRule = argRule;
		this.argId   = argId;
		this.argName = argName;
		
		// Set system specific separator for paths
		if (argType == ARG_TYPE.PATH) { 
			this.argSeparator = File.pathSeparator;
		}
		
		this.argValues = new LinkedHashSet<String>();
	}
	

	/**
	 * Define a process argument.
	 * This is simple constructor for non MULTI_VAL arguments that will be using
	 * a name/value separator of space, eg, '--output /tmp/log.txt'
	 */
	public ProcessArg(ARG_TYPE argType, REQUIREMENT argRule, String argId, String argName) {
		this(argType, " ", "", argRule, argId, argName);

		if (argType == ARG_TYPE.MULTI_VAL) {
			throw new IllegalStateException("Separator characters must be specified for multi-value arguements. Use other constructor");
		}
	}
	

	public void add(String newStringValue) throws StfException {
		if (!argType.allowMultipleValues) {
			verifyNotAlreadySet(argValues, newStringValue);
		}
		verifyArgTypeIs(ARG_TYPE.STRING, ARG_TYPE.MULTI_VAL, ARG_TYPE.REPEAT_ARG, ARG_TYPE.CLASS, ARG_TYPE.PATH, ARG_TYPE.DIR, ARG_TYPE.FILE);
		argValues.add(newStringValue);
	}


	public void add(DirectoryRef dir) throws StfException {
		add(dir.getSpec());
	}
	

	public void add(int n) throws StfException {
		try {
			add(Integer.toString(n));
		} catch (StfException e) {
			throw new StfException("Failed to convert integer to string. Value:" + n);
		}		
	}
	
	
	public void setClass(Class<?> clazz) throws StfException {
		add(clazz.getCanonicalName());
	}


	public void addToPath(DirectoryRef dir) throws StfException {
		verifyArgTypeIs(ARG_TYPE.PATH);
		add(dir.getSpec());
	}

	
	public void add(FileRef file) throws StfException {
		verifyArgTypeIs(ARG_TYPE.STRING, ARG_TYPE.PATH);
		add(file.getSpec());
	}

	
	public void setFlag(Boolean newValue) throws StfException {
		verifyNotAlreadySet(argEnabled, newValue);
		verifyArgTypeIs(ARG_TYPE.FLAG);
		
		this.argEnabled = newValue;
	}
	
	
	public void setFile(FileRef fileRef) throws StfException {
		verifyNotAlreadySet(argFile, fileRef);
		verifyArgTypeIs(ARG_TYPE.FILE);
		
		this.argFile = fileRef;
	}

	
	public FileRef getFileValue() throws StfException {
		verifyArgTypeIs(ARG_TYPE.FILE);
		return argFile;
	}
	
	
	private void verifyNotAlreadySet(Object currentValue, Object newValue) throws StfException {
		if (currentValue != null) {
			throw new StfException("Argument value already set for the '" + argId + "' argument. "
					+ "Current value of '" + currentValue + "' "
					+ "cannot be replaced with new value of '" + newValue + "'");
		}
	}


	private void verifyNotAlreadySet(LinkedHashSet<String> currentValues, String newValue) throws StfException {
		if (!currentValues.isEmpty()) {
			throw new StfException("Argument value already set for the '" + argId + "' argument. "
					+ "Current value of '" + currentValues + "' "
					+ "cannot be replaced with new value of '" + newValue + "'");
		}
	}


	// Throw exception if current argument is not of correct type.
	private void verifyArgTypeIs(ARG_TYPE... expectedArgTypes) throws StfException {
		for (ARG_TYPE expectedArgType : expectedArgTypes) {
			if (argType == expectedArgType) {
				return;
			}
		}

		throw new StfException("Invalid argument type for this operation. Current argument '" + argId + "' is of type '" + argType + "' but must be of type '" + Arrays.toString(expectedArgTypes) + "'");
	}


	public ArrayList<String> asString() throws StfException {
		ArrayList<String> allArgAsStrings = new ArrayList<String>();

		switch (argType) {
		case STRING:
		case CLASS:
		case DIR:
			// Output argument name and a single parameter
			if (!argValues.isEmpty()) {
				String argAsString;
				if (argName == null) {
					argAsString = formatArgumentValue(argValues.iterator().next());
				} else {
					argAsString = argName + nameValueSeparator + formatArgumentValue(argValues.iterator().next());
				}
				allArgAsStrings.add(argAsString);
			}
			break;
			
		case MULTI_VAL:
		case PATH:
			// Create path argument by concatenating together all directory references
			if (!argValues.isEmpty()) {
				StringBuilder pathArg = new StringBuilder();
				if (argName != null) { 
					pathArg.append(argName + nameValueSeparator);
				}
				boolean doneFirst = false;
				for (String arg : argValues) {
					if (doneFirst) {
						pathArg.append(argSeparator);
					}
					pathArg.append(arg);
					doneFirst = true;
				}
				allArgAsStrings.add(pathArg.toString());
			}
			break;
		
		case REPEAT_ARG:
			for (String arg : argValues) {
				String argText = argName + nameValueSeparator + arg;
				allArgAsStrings.add(argText);
			}
			break;
			
		case FLAG:
			if (argEnabled != null && argEnabled) {
				// Flag has been enabled. Output correct value to turn it on.
				allArgAsStrings.add(argName);
			}
			break;
			
		case FILE:
			if (argFile != null) {
				// File has been set. Output value.
				allArgAsStrings.add(argFile.getSpec());
			}
			break;
			
		default: 
			throw new StfException("Internal error: toString() not supported for argument '" + argId + "' of type '" + argType + "'");
		}
		
		// Fail if mandatory argument has not been set
		if (argRule == REQUIREMENT.MANDATORY  && allArgAsStrings.isEmpty()) {
			throw new StfException("No value supplied for mandatory argument '" + argId + "'");
		}
		
		return allArgAsStrings;
	}
	
	
	private String formatArgumentValue(String arg) { 
       if (arg.contains(" ")) {
          return "\"" + arg + "\"";
       }
       return arg;
    }
	
}