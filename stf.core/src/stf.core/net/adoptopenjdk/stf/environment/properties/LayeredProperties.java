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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;


public class LayeredProperties {
	// This array holds 1 or more layers of properties.
	// They are held in highest to lowest priority order. ie. values from, say, the first layer 
	// will override values from subsequent layers.
	private ArrayList<OrderedProperties> layers = new ArrayList<OrderedProperties>();
	
	ArrayList<Argument> supportedArguments = null;
	
	private HashSet<String> optionalProperties;
	
	
	/**
	 * Constructs a new Layer properties object.
	 * @param optionalProperties is a set of Strings listing optional properties, for which no value has to be supplied. 
	 */
	public LayeredProperties(ArrayList<OrderedProperties> allPropertyData, ArrayList<Argument> supportedArguments) {
		this.layers = allPropertyData;
		this.supportedArguments = supportedArguments;
		
		// Build up a set containing all optional properties
		optionalProperties = new LinkedHashSet<String>();
		for (Argument argument : supportedArguments) {
			if (argument.isOptional()) {
				optionalProperties.add(argument.getName());
			}
		}
	}
	
	
	public void addPropertyLayer(OrderedProperties properties) {
	    layers.add(properties);	
	}

	
	/**
	 * @returns the value for the named property.
	 * 
	 * @throws StfException if the property is not known or if it has a null value.
	 */
	public String getProperty(String propertyName) throws StfException {
		Stack<String> resolutionAuditTrail = new Stack<String>();
		return resolveProperty(propertyName, propertyName, resolutionAuditTrail);
	}


	public void updateProperty(Argument argument, String newValue) {
		OrderedProperties newLayer = new OrderedProperties("DynamicPropertyUpdate");
		newLayer.put(argument.getName(), newValue);
		layers.add(0, newLayer);
	}

	
	/**
	 * This is the key method which finds the current value for a named property.
	 * It works it's way through the property layers trying to find a value for the property.
	 * 
	 * If the value of a property contains a '${.*}' then every such occurrence is replaced 
	 * with the value of the corresponding environment variable or the value of another property.
	 * Resolution of the referenced property starts at the highest level.
	 * Example specification: 'java-execution-args=-Xmx128m ${fixed-args} ${extension-args}'
     *
	 * @param propertyName is the name of the property to find a value for. 
	 * @param originalPropertyName is the name of the property that we started looking for.
	 * @param resolutionAuditTrail lists all properties that have been followed in an attempt to 
	 *        resolve a property. This is used to prevent infinite recursion and help produce more 
	 *        sensible error messages.
	 * @return The value of the property. 
	 * @throws StfException if no value can be found for propertyName.
	 */
	private String resolveProperty(String propertyName, String originalPropertyName, Stack<String> resolutionAuditTrail) throws StfException {
		resolutionAuditTrail.push(propertyName);
		if (resolutionAuditTrail.size() > 100) {
			String trailDescription = formatTrailDescription(resolutionAuditTrail);
			throw new StfException("Excess property nesting detected for property '" + originalPropertyName + "'. "
						+ "Chain followed is: " + trailDescription);
		}
		
		// Attempt to find the value for the requested property name by stepping through 
		// all of the property layers. From topmost to lowest priority.
		for (OrderedProperties layer : layers) { 
			String value = layer.getProperty(propertyName);
			if (value != null) {
				value = value.trim();
				
				if (value.isEmpty() && !optionalProperties.contains(propertyName)) {
					throw new StfError("Empty property found for '" + propertyName + "'");
					
				} else if (value.equals(Argument.EMPTY_VALUE)) {
					if (optionalProperties.contains(propertyName)) {
						resolutionAuditTrail.pop();
						return "";
					}
					throw new StfError("No value specified for the mandatory property: '" + propertyName + "'");

				} else if (value.contains("${")) {
					// The value looks like it references at least one other variable.
					// Update the property value by replacing the reference with the actual value(s). 
			        Pattern pattern = Pattern.compile("\\$\\{.*?\\}");  // ie: ${.*?}
			        Matcher matcher = pattern.matcher(value);

			        StringBuffer buff = new StringBuffer();
			        while(matcher.find()) {
			            String referenceSpec = matcher.group(0);
			            String referenceName = referenceSpec.substring(2, referenceSpec.length()-1);
			            
			            // To find the replacement value first try using an environment variable
						String replacementValue = System.getenv(referenceName);
			            if (replacementValue == null) {
			            	// It's not a reference to an env.variable, so attempt to substitute another property value.
			            	replacementValue = resolveProperty(referenceName, originalPropertyName, resolutionAuditTrail);
			            }

			            // The upcoming 'appendReplacement' looses '\' characters! So prevent this by changing them to '\\'
			            replacementValue = replacementValue.replace("\\",  "\\\\");

			            matcher.appendReplacement(buff, replacementValue);
			        }
			        matcher.appendTail(buff);
			        value = buff.toString();
				}

				resolutionAuditTrail.pop();
				return value;
			}
		}
		
		// If an optional property references an unset environment variable then we end up here.
		if (optionalProperties.contains(originalPropertyName)) {
			// It's optional so treat property as having an empty string value.
			resolutionAuditTrail.pop();
			return "";
		}

		// Failed to find a value for propertyName
		if (propertyName.equals(originalPropertyName)) {
			throw new StfError("No value specified for property '" + propertyName + "'");
		} else {
			String trailDescription = formatTrailDescription(resolutionAuditTrail);
			throw new StfError("Unable to succesfully build value for property '" + originalPropertyName + "'. "
					+ "There is no enviroment variable or property defined for '" + propertyName + "'. " 
					+ "Chain followed is: " + trailDescription);
		}
	}


	private String formatTrailDescription(Stack<String> resolutionAuditTrail) {
		StringBuilder trailDescription = new StringBuilder();
		
		boolean first = true;
		for (String item : resolutionAuditTrail) {
			if (!first) {
				trailDescription.append(" -> ");
			}
			first = false;
			trailDescription.append(item);
		}
		
		return trailDescription.toString();
	}

	
	/**
	 * @return an set containing the names of all arguments.
	 */
	private LinkedHashSet<String> getAllArgumentNames() {
		LinkedHashSet<String> allArgumentNames = new LinkedHashSet<String>();
	
		for (OrderedProperties layer : layers) {
			for (Object key : layer.getEnvironmentNeutralkeys()) {
				String argName = (String) key;
				if (!allArgumentNames.contains(argName)) {
					allArgumentNames.add(argName);
				}
			}
		}

		return allArgumentNames;
	}

	
	// Verifies that every property, from every layer, is known.
	// For a property to be known it must be a supported property of an extension 
	// that is used by the current test.
	// An exception is thrown if an unknown property is found.
	// This method makes sure that all supplied properties are spelt correctly.
	public void checkForUnknownProperties() throws StfException {
		// Find all of the properties that are used in the current STF run
		HashSet<String> actualProperties = getAllArgumentNames();
		
		// From the list of the actual properties subtract all allowed arguments.
		for (Argument supportedArgument : supportedArguments) {
			actualProperties.remove(supportedArgument.getName());
		}
		
		// Fail if there are any properties left, as these are not supported by any extension.
		if (!actualProperties.isEmpty()) {
			throw new StfError("Unknown argument(s) supplied: " + actualProperties);
		}
	}

	
	// Write a list of all properties, with their resolved values, to a file.
	// They are written out in the same order as the list at the top of this class.
	public void dumpResolvedProperties(ArrayList<Argument> allArguments, File debugFile) throws StfException {
		StringBuilder builder = new StringBuilder();
		
		// Add title describing the contents of the file
		builder.append("# \n");
		builder.append("# Resolved property values.\n");
		builder.append("# \n");
		builder.append("# This file contains the actual value to be used for all properties.\n");
		builder.append("# \n\n");
		
		// Add actual name/value pairs for all properties. 
		for (Argument argument : allArguments) {
			String argumentName = argument.getName();
			builder.append(argumentName + " = " + getProperty(argumentName) + "\n");
		}

		writeFile(debugFile, builder.toString());
	}
	

	// Writes textual values of all properties to a file. 
	// The properties are written layer by layer, from highest to lowest priority.
	// The values are written as-is. ie. their values are not resolved.
	public void dumpAllProperties(File outputFile) throws StfException {
		String fileHeader = 
				  "# \n"
				+ "# This file contains the values for all properties in all layers.\n"
				+ "# The layers are listed from highest to lowest priority\n"
				+ "# \n"
				+ "# See 'resolvedProperties.txt for the actual value resolved for each property.\n"
				+ "# \n\n";
		
		writeFile(outputFile, fileHeader + this.toString());
	}


	// Writes a string to an output file
    private void writeFile(File outputFile, String content) throws StfException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(outputFile));
            output.write(content);
        } catch (IOException e) {
            throw new StfException("Failed to write to file: " + outputFile.getAbsolutePath(), e);
        } finally {
            if (output != null) {
            	try {
					output.close();
				} catch (IOException e) {
					throw new StfException("Failed to close file: " + outputFile.getAbsolutePath(), e);
				}
            }
        }
    }


	public String toString() {
		StringBuilder builder = new StringBuilder();

		int layerNumber = 1;
		for (OrderedProperties layer : layers) {
			builder.append(layerNumber + ") ");
			builder.append(layer.toString());
			builder.append("\n");
			layerNumber++;
		}
		
		return builder.toString();
	}
}