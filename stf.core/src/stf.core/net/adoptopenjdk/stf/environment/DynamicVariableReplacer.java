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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.adoptopenjdk.stf.StfException;


/**
 * This class takes incoming string and updates references to
 *   1) A child directory of the results or tmp directory. eg ${resultsDir.childDir(cacheData)}
 *   2) A reference to an environment variable, eg ${JAVA_HOME}
 *   3) A reference to another property, eg ${apps-root}
 *   
 * For example the incoming string may be '-XcacheName:${resultsDir.childDir(cache)}' which
 * would evaluate to perhaps '/tmp/stf/TestName/results/cache' on linux 
 * and 'C:\temp\stf\TestName\results\cache' on Windows
 */
public class DynamicVariableReplacer {
	private StfEnvironmentCore environmentCore;
	
	
	public DynamicVariableReplacer(StfEnvironmentCore environmentCore) {
		this.environmentCore = environmentCore;
	}
	

	/**
	 * Updates references in the incoming string and returns a string with such values
	 * replaced with their resolved values.
	 */
	public String process(String original) throws StfException { 
		if (!original.contains("${")) {
			return original;
		}
		
		// The value looks like it references at least one other variable.
		// Update the property value by replacing the reference with the actual value(s). 
        Pattern pattern = Pattern.compile("\\$\\{.*?\\}");  // ie: look for ${.*?}
        Matcher matcher = pattern.matcher(original);

        StringBuffer buff = new StringBuffer();
        while (matcher.find()) {
            String referenceSpec = matcher.group(0);
            String referenceName = referenceSpec.substring(2, referenceSpec.length()-1);
            
            // Stage 1. A child of the results or tmp directory
            String replacementValue = processRelativeDir(referenceName);
            
            // Stage 2. Try replacement using an environment variable
            if (replacementValue == null) {
            	replacementValue = System.getenv(referenceName);
            }
            
            // Stage 3. Try replacement using system property
            if (replacementValue == null) {
            	replacementValue = System.getProperty(referenceName);
            }
            
            // Stage 4. It's not a reference to an env.variable, so attempt to substitute another property value.
            if (replacementValue == null) {
	            replacementValue = environmentCore.getProperty(referenceName);
            }

            matcher.appendReplacement(buff, replacementValue);
        }
        matcher.appendTail(buff);
        
        return buff.toString();
	}


	/*
	 * Attempts to evaluate a string which references a directory which is a child of the results or tmp dir.
	 * For example the reference may be 'resultsDir.childDir(cache)' which would return '/tmp/stf/TestName/result/cache'
	 */
	private String processRelativeDir(String reference) throws StfException {
        Pattern pattern = Pattern.compile("^(.*?)\\.(.*?)\\((.*?)\\)$");  // ie: look for "<name> . <method> ( <string> )"
        Matcher matcher = pattern.matcher(reference);
        
        if (!matcher.find()) {
        	return null;  // doesn't match the reference format
        }

        // Use regex to grab the different parts of the reference
        String objectName = matcher.group(1);
        String methodName = matcher.group(2);
        String childName  = matcher.group(3);

        // Work out which directory is being referenced
        DirectoryRef dirRef = null;
        if (objectName.equals("resultsDir")) {
        	dirRef = environmentCore.getResultsDir();
        } else if (objectName.equals("tmpDir")) {
        	dirRef = environmentCore.getTmpDir();
        } else {
        	throw new StfException("Unknown object name in reference: " + reference);
        }
        
        // Valid the method name being used
        if (!methodName.equals("childDir")) {
        	throw new StfException("Unknown method in reference: " + reference);
        }
        
        String resolvedReference = dirRef.childDirectory(childName).getSpec();
        return resolvedReference;
	}
}