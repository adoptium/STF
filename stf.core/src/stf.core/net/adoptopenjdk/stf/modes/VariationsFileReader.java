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
import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import net.adoptopenjdk.stf.StfException;


/**
 * This class reads a variations.xml or testplan.xml file to find the java-args 
 * values for a specified variation name.
 * 
 * It basically extracts the java-args from xml such as:
 *    <variations>
 *       <variation name="jit-count30">
 *          <java-args>-Xjit:count=30</java-args>
 *          <generation-constraints>
 *          ...
 */
public class VariationsFileReader {
    private static final Logger logger = LogManager.getLogger(VariationsFileReader.class.getName());


    /**
	 * Reads a variations xml file to find the java-args for the named variation 
	 * @param variationsFile is the file to be read.
	 * @param variationName is the name of the java-args variation being used. 
	 * @return ArrayList of all matching java-arg definitions.
	 * @throws StfException if we failed to read the xml file.
	 */	
	public static ArrayList<JvmArgDetails> decodeVariationName(File variationsFile, String variationName) throws StfException {
		logger.debug("Searching variations file: " + variationsFile.getAbsolutePath());
		
		try {
			// Read the contents of the variations xml file
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			db.setEntityResolver(new EntityResolver() {
				@Override
				public InputSource resolveEntity(String publicId, String systemId) {
					// it might be a good idea to insert a trace logging here that you are ignoring publicId/systemId
					return new InputSource(new StringReader("")); // Returns a valid dummy source
				}
			});
			Document doc = db.parse(variationsFile);
				
			// Find the 'java-args' node for the named variation
			XPath xpath = XPathFactory.newInstance().newXPath();
			XPathExpression expr = xpath.compile("//variations/variation[@name=\"" + variationName + "\"]/java-args/text()");
			NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

			// Extract all found 'java-args' values
			ArrayList<JvmArgDetails> javaArgs = new ArrayList<JvmArgDetails>();
			for (int i=0; i<nl.getLength(); i++) {
				String argsSpec = nl.item(0).getNodeValue();
				JvmArgDetails argDetails = new JvmArgDetails(variationsFile, argsSpec);
				javaArgs.add(argDetails);
				logger.trace("Found java-args value: " + argsSpec);
			}
			
			return javaArgs;

		} catch (Exception e) {
			throw new StfException("Failed to read variation '" + variationName + "' from variation file: " + variationsFile.getAbsolutePath(), e);
		}
	}
}