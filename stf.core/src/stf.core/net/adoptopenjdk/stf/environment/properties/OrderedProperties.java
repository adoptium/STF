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


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.PlatformFinder;

/**
 * Properties class which keeps properties in the same order as read from the property file
 */
public class OrderedProperties extends Properties 
{
	private static final long serialVersionUID = 2138723788L;

	// To help with debugging keep a note of where the properties have come from
	private String sourceFileName;
	
	private LinkedHashSet<Object> environmentNeutralkeys = new LinkedHashSet<Object>();
	
	
	// Inner class to hold details about a property file
	public static class PropertyFileDetails { 
		public File file;
		public boolean mandatory;

		public PropertyFileDetails(File propertyFile, boolean mandatory) {
			this.file = propertyFile;
			this.mandatory = mandatory;
		}

		public String toString() { 
			return file.getAbsolutePath() + " " + mandatory;
		}
	}


	public OrderedProperties() {
	}
	
	
	public OrderedProperties(String sourceFileName) {
		this.sourceFileName = sourceFileName;
	}
	
	
	public Set<Object> getEnvironmentNeutralkeys() {
		return environmentNeutralkeys;
	}
	
	
	public Object put(Object fullName, Object valueObject)
	{
		String name  = ((String) fullName).trim();
		String value = ((String) valueObject).trim();
		
		// Remove a leading '-' from the name
		if (name.startsWith("-")) {
			name = name.substring(1);
		}

		// Keep note of the property name without platform name for later error checking
		String cleanedName = PlatformFinder.removePlatformSuffix(name);
		getEnvironmentNeutralkeys().add(cleanedName);
				
		return super.put(name, value);
	}
	
	@Override
	public String getProperty(String propertyName) {
		// First try to find a value for the current platform.
		try {
			String platformedKey = propertyName + "." + PlatformFinder.getPlatform().getShortName();
			String propertyValue = super.getProperty(platformedKey);
	
			if (propertyValue == null) {
				// No platform specific value, so look for version without the platform specified.
				propertyValue = super.getProperty(propertyName);
			}
			
			return propertyValue;
		} catch (StfException e) {
			throw new IllegalStateException("Failed to get property: " + propertyName, e);
		}
	}
	
	@Override
	public Set<String> stringPropertyNames() 
	{
		LinkedHashSet<String> keysAsStrings = new LinkedHashSet<String>();

		for (Object key:keySet()) {
	    	keysAsStrings.add((String) key);
		}
		
		return keysAsStrings;
	}
	
	
	/** Reads in a set of properties from a file
	 * 
	 * @param fileName
	 * @return
	 * @throws StfException
	 */
	public static OrderedProperties loadFromFile(PropertyFileDetails propertyFile) throws StfException { 
		String propertyFileName = propertyFile.file.getAbsolutePath();
		OrderedProperties properties = new OrderedProperties(propertyFileName);
		
		if (propertyFile.mandatory && !propertyFile.file.exists()) {
			throw new StfException("Mandatory property file does not exist: " + propertyFileName);
		}
		
		if (propertyFile.file.exists()) {
			// Read in the contents of the property file
			String propertyFileContents;
		    BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new FileReader(propertyFile.file));
			  
				StringBuffer fileContents = new StringBuffer();
				String line = null;
				while ((line = bufferedReader.readLine()) != null) {
				   fileContents.append(line).append("\n");
			    }
				propertyFileContents = fileContents.toString();
			} catch (FileNotFoundException e) {
				throw new StfException(e);
			} catch (IOException e) {
				throw new StfException(e);
			} finally {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					throw new StfException("Failed to close file: " + propertyFile.file);
				}
			}

			// Fix windows specific problem when reading file specifications from property files.
			// Java property files containing backslash characters loose their backslash characters 
			// when they are read in. 
			// The workaround is to replace single backslash characters with double backslashes. 
			// eg, 'c:\tmp\stf' from the original file is updated to 'c:\\tmp\\stf' which is read in as 'c:\tmp\stf'
			propertyFileContents = propertyFileContents.replace("\\", "\\\\");

			// Load properties from the corrected string
			try {
				properties.load(new ByteArrayInputStream(propertyFileContents.getBytes()));
			} catch (IOException e) {
				throw new StfException("Failed to load properties file: " + propertyFileName, e);
			}
		}
		
		return properties;
	}
	
	
	public synchronized String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("PropertyFile: " + sourceFileName + "\n");
		
		if (!new File(sourceFileName).exists()) {
			builder.append("   No properties loaded. File does not exist\n");
		}
		
		for (Object key : keySet()) { 
			builder.append("  " + key + " = " + super.getProperty((String) key) + "\n");
		}

		return builder.toString();
	}
}