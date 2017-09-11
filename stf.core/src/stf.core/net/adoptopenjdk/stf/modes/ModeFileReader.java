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

package net.adoptopenjdk.stf.modes;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DynamicVariableReplacer;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;


/**
 * This class takes a mode number and returns the corresponding set of JVM options.
 * The actual JVM options are read from the modes.xml file.
 */
public class ModeFileReader {
	private static final String MODES_FILE = "config/modes.xml";


	// Lookup the contents of the modes file to translate a mode name to actual VM arguments.
	public static ArrayList<JvmArgDetails> decodeModeName(StfEnvironmentCore environmentCore, String modeName) throws StfException {
		
		// Create a file object to point to the modes.xml file
		File projectDir = environmentCore.findTestDirectory("stf.core").asJavaFile();
		File modeFile = new File(projectDir, MODES_FILE);
		
		if (!modeFile.exists()) { 
			throw new StfException("Mode configuration file does not exist: " + modeFile.getAbsolutePath());
		}
		
		try {
			// Read the contents of the modes xml file
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(modeFile);
			
			// Find the 'value' attributes to be used for the specified mode
			XPath xpath = XPathFactory.newInstance().newXPath();
			XPathExpression expr = xpath.compile("/modes/mode[@number=\"" + modeName + "\"]/settings/setting/envVar/@value");
			
			// Run the xpath query to extract the mode values
			NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			if (nl.getLength() == 0) {
				// Not found
				return new ArrayList<JvmArgDetails>();
			}
			
			// Concatenate the mode strings to produce the full list of JVM arguments
			StringBuilder modeArguments = new StringBuilder();
			for (int i=0; i<nl.getLength(); i++) {
				Node node = nl.item(i);
				modeArguments.append(node.getNodeValue() + " ");
			}
			
			// Update the modes string if it contains any references such as '-XcacheName:${resultsDir.childDir(bigCache)}'
			String modeArgumentsString = new DynamicVariableReplacer(environmentCore).process(modeArguments.toString());
		
			ArrayList<JvmArgDetails> javaArgs = new ArrayList<JvmArgDetails>();
			javaArgs.add(new JvmArgDetails(modeFile, modeArgumentsString));
			return javaArgs;
		} catch (Exception e) {
			throw new StfException("Failed to read mode '" + modeName + "' from mode file: " + modeFile.getAbsolutePath(), e);
		}
	}
}
