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

package net.adoptopenjdk.loadTest;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface;
import net.adoptopenjdk.loadTest.adaptors.ArbitraryJavaAdaptor;
import net.adoptopenjdk.loadTest.adaptors.JUnitAdaptor;
import net.adoptopenjdk.loadTest.adaptors.MauveAdaptor;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension;
import net.adoptopenjdk.stf.processes.definitions.LoadTestProcessDefinition;


/**
 * This class parses an XML file containing a list of tests and gives access to 
 * the resulting test objects.
 */
public class InventoryData {
    private static final Logger logger = LogManager.getLogger(InventoryData.class.getName());
    
    private String inventoryFileRef;
	
    // Set to true if this instance doesn't need to execute tests, but is only counting how many there are.
	private boolean countingOnly = false;
	
	private boolean verbose = false;
	
	// Allocation a unique number to each test. Doesn't reset for each inventory
	private final ArrayList<AdaptorInterface> testList;
	private static int nextTestNum = 0;

	// This is the multiplier to be applied to all weightings so that randomly selected 
	// tests have a distribution as close as is reasonably possible to the desired distribution.
	private BigDecimal weightingMultiplier = BigDecimal.ONE;
	
	// This is the maximum allowed size of the test lookup table (for randomly selecting tests)
	// Lower values potentially use less memory, but will sometimes mean that the exact weightings
	// cannot be honoured.
	private static int MAX_WEIGHTING_LOOKUP_SIZE = 500000;

	private static boolean dumpRequested = false; 
	
	/**
	 * Parse an XML test 'inventory' file (which lists the tests to be run for the
	 * current suite) and build the Objects for each individual test execution.
	 * It also reads lists of tests from exclusion files and removes these tests.
	 * eg, Tests can be removed from say 'mauvePt1.xml' if you have a file called 'mauvePt1_exclude_PR106330.xml'
	 * Any number of exclusion files are processed, providing that their name starts with
	 * the inventory name plus '_exclude'.
	 * 
	 * @param root is a directory below which all inventory files are referenced.
	 * @param inventoryFileRef points to an inventory file below the root directory to read.
	 * @param exclusionFiles is a list of files listing tests that are not to run. 
	 * These tests are removed from the tests listed in the inventoryFileName file.
	 * The exclusion files are also specified as paths below the root directory.
	 * @param verbose should be set to true for debug level progress reporting.
	 */
	InventoryData(ArrayList<DirectoryRef> testRoots, String inventoryFileRef, ArrayList<String> exclusionFiles, boolean countingOnly, boolean verbose, boolean dumpRequested) throws Exception {
		this.inventoryFileRef = inventoryFileRef;
		this.countingOnly = countingOnly;
		this.verbose = verbose;
		this.dumpRequested = dumpRequested; 
		
		// Read the inventory file and any which it includes
		testList = readInventoryFile(testRoots, inventoryFileRef);
		
		// Read any exclusions, and remove their tests from the main list
		for (String exclusionFileRef : exclusionFiles) {
			if (verbose) {
				logger.info("Reading exclusion file. File=" + exclusionFileRef);
			}
			ArrayList<AdaptorInterface> exclusions = readInventoryFile(testRoots, exclusionFileRef);
			testList.removeAll(exclusions);
		}
		
		this.weightingMultiplier = calculateWeightingMultiplier(testList);

		// List the tests to be used
		if (verbose) {
			logger.info("Final test list:");
			String adjustedWeightingString = " ";
			for (AdaptorInterface test : testList) {
				if (weightingMultiplier.intValue() > 1) {
					int roundedWeighting = test.getRoundedAdjustedWeighting(weightingMultiplier);
					adjustedWeightingString = "->" + roundedWeighting;
				}
				logger.info("  " + test.getTestNum() + " " + test + "  Weighting=" + test.getWeighting() + adjustedWeightingString);
			}
		}
	}

	private ArrayList<AdaptorInterface> readInventoryFile(ArrayList<DirectoryRef> testRoots, String inventoryFileRef) throws SAXException, IOException,
			ParserConfigurationException, ClassNotFoundException, NoSuchMethodException, StfException {

		// Find the inventory file

		DirectoryRef root = FileRef.findFileRoot(inventoryFileRef, testRoots);

		if (verbose) {
			logger.info("Parsing inventory file. Root=" + root + " File=" + inventoryFileRef);
		}

		// Validate inventory file
		File inventoryFile = root.childFile(inventoryFileRef).asJavaFile();
		if (!inventoryFile.exists()) {
			throw new IllegalStateException("Inventory file does not exist: " + inventoryFile);
		}

		// Open inventory file as xml document
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		Document doc = dbFactory.newDocumentBuilder().parse(inventoryFile);
		
		// Process every top level 'inventory' node
		ArrayList<AdaptorInterface> tests = new ArrayList<AdaptorInterface>();
		for (int i = 0; i < doc.getChildNodes().getLength(); i++) {
			Node inventoryNode = doc.getChildNodes().item(i);
			if (!inventoryNode.getNodeName().equals("inventory") && !inventoryNode.getNodeName().equals("#comment")) {
				throw new IllegalStateException("Expected 'inventory' node but was '" + inventoryNode.getNodeName());
			}

			// Parse inventory content. Expecting a list of tests
			NodeList testNodes = inventoryNode.getChildNodes();
			int nodeNumber = 0;
			for (int j=0; j<testNodes.getLength(); j++) {
				Node testNode = testNodes.item(j);
				if (testNode.getNodeType() == Node.TEXT_NODE || testNode.getNodeType() == Node.COMMENT_NODE) {
					continue;  // Ignore text and comment nodes
				}
				nodeNumber++;
				
				String testType = testNode.getNodeName();
				if (testType.equals("mauve")) {
					// Call old style gnu java tests
					// Validate attributes
					String[] mandatoryArgs = new String[]{ "class" };
					String[] optionalArgs  = new String[]{ "weighting" };
					validateNodeContents(testNode, inventoryFileRef, nodeNumber, "mauve", optionalArgs, mandatoryArgs);
					
					// Grab the mauve class name and weighting
					String mauveFullClassname = testNode.getAttributes().getNamedItem("class").getNodeValue();
					BigDecimal weighting = parseWeighting(testNode, inventoryFileRef, nodeNumber);
					
					AdaptorInterface testcase = new MauveAdaptor(nextTestNum, mauveFullClassname, weighting, dumpRequested);
					tests.add(testcase);
					nextTestNum++;
						
				} else if (testType.equals("arbitraryJava")) {
					// Run any java method
					String[] mandatoryArgs = new String[]{ "class", "method" };
					String[] optionalArgs  = new String[]{ "constructorArguments", "methodArguments", "weighting" };
					validateNodeContents(testNode, inventoryFileRef, nodeNumber, "arbitraryJava", optionalArgs, mandatoryArgs);
					
					String classname = testNode.getAttributes().getNamedItem("class").getNodeValue();
					String methodName = testNode.getAttributes().getNamedItem("method").getNodeValue();
					BigDecimal weighting = parseWeighting(testNode, inventoryFileRef, nodeNumber);
					
					// Parse optional argument for constructor+method arguments, which can specify a comma separated list of strings
					ArrayList<String> constructorArgs = parseArgumentList(testNode, "constructorArguments");
					ArrayList<String> methodArgs = parseArgumentList(testNode, "methodArguments");
					
					AdaptorInterface testcase = new ArbitraryJavaAdaptor(nextTestNum, classname, constructorArgs, methodName, methodArgs, weighting, countingOnly, dumpRequested);
					tests.add(testcase);
					nextTestNum++;

				} else if (testType.equals("junit")) {
					// Validate attributes
					String[] mandatoryArgs = new String[]{ "class" };
					String[] optionalArgs  = new String[]{ "weighting" };
					validateNodeContents(testNode, inventoryFileRef, nodeNumber, "junit", optionalArgs, mandatoryArgs);
					
					// Get full class name and test weighting
					String junitClassName = testNode.getAttributes().getNamedItem("class").getNodeValue();
					BigDecimal weighting = parseWeighting(testNode, inventoryFileRef, nodeNumber);
					
					AdaptorInterface testcase = new JUnitAdaptor(nextTestNum, junitClassName, weighting, dumpRequested);
					tests.add(testcase);
					nextTestNum++;

				} else if (testType.equals("include")) {
					// Pull in the contents of another inventory file
					String[] mandatoryArgs = new String[]{ "inventory" };
					validateNodeContents(testNode, inventoryFileRef, nodeNumber, "include", null, mandatoryArgs);
					
					Node includeInventoryNode = testNode.getAttributes().getNamedItem("inventory");
					String includeFileName = includeInventoryNode.getNodeValue();
					if (verbose) {
						logger.info("Found included inventory. File=" + includeFileName);
					}
					ArrayList<AdaptorInterface> includeTests = readInventoryFile(testRoots, includeFileName);
					tests.addAll(includeTests);

				} else { 
					throw new IllegalStateException("Unknown test type specified '" + testType + "' in inventory: " + inventoryFile);
				}
			}
		}
		
		return tests;
	}
	
	private BigDecimal parseWeighting(Node testNode, String inventoryFile, int nodeNumber) throws StfException {
		Node weightingNode = testNode.getAttributes().getNamedItem("weighting");
		
		if (weightingNode == null) {
			return new BigDecimal(1);
		}

		String weightingStr = weightingNode.getNodeValue();
		BigDecimal weighting;
		try {
			weighting = new BigDecimal(weightingStr);
		} catch (NumberFormatException e) {
			throw new StfException("Invalid format for 'weighting' attribute. "
					+ "Value='" + weightingStr + "' at index '" + nodeNumber + "' of file '" + inventoryFile + "'");
		}

		return weighting;
	}

	/**
	 * Validates attributes for an inventory node.
	 * Throws an exception if:
	 *   - mandatory argument is missing.
	 *   - attribute found which is not on the mandatory or optional list.
	 */
	private void validateNodeContents(Node node, String inventoryFile, int index, String nodeType, String[] optionalAttributes, String... mandatoryAttributes) throws StfException {
		// Verify that values are specified for all mandatory attributes
		for (String attribute : mandatoryAttributes) {
			Node attributeNode = node.getAttributes().getNamedItem(attribute);
			if (attributeNode == null) {
				throw new StfException("Failed to find mandatory attribute '" + attribute + "' for '" + nodeType + "' node. "
						+ "Parsing index '" + index + "' of file '" + inventoryFile + "'");
			}
		}
		
		// Build set of all valid attribute names
		LinkedHashSet<String> allowedAttributeNames = new LinkedHashSet<String>();
		allowedAttributeNames.addAll(Arrays.asList(mandatoryAttributes));
		allowedAttributeNames.addAll(optionalAttributes == null ? new ArrayList<String>() : Arrays.asList(optionalAttributes));

		// Build set of all used attribute names
		LinkedHashSet<String> actualAttributeNames = new LinkedHashSet<String>();
		for (int i=0; i<node.getAttributes().getLength(); i++) {
			actualAttributeNames.add(node.getAttributes().item(i).getNodeName());					
		}
		
		// Fail if unexpected attribute name found
		actualAttributeNames.removeAll(allowedAttributeNames);
		if (!actualAttributeNames.isEmpty()) {
			throw new StfException("Unexpected attribute(s) found '" + actualAttributeNames  + "' for '" + nodeType + "' node. "
						+ "Parsing index '" + index + "' of file '" + inventoryFile + "'");
		}
	}


	// Reads the value of an argument and converts it to an ArrayList of strings.
	// If the value exists then it should be in a comma separated form.
	private ArrayList<String> parseArgumentList(Node testNode, String argumentName) {
		ArrayList<String> argValues = new ArrayList<String>();
		Node argumentNode = testNode.getAttributes().getNamedItem(argumentName);
		if (argumentNode != null) {
			String[] args = argumentNode.getNodeValue().split(",");
			for (String arg : args) {
				argValues.add(arg.trim());
			}
		}
		
		return argValues;
	}

	
	/**
	 * @return the number of tests held in this inventory file.
	 */
	public int getNumberOfTests() {
		return testList.size();
	}

	public String getInventoryFileRef() {
		return inventoryFileRef;
	}

	/**
	 * Gives access to information about a single test.
	 * 
	 * @param testIndex specifies the test which is required. Numbered from 0.
	 * @return details about the test.
	 */
	public AdaptorInterface getTest(int testIndex) {
		return testList.get(testIndex);
	}
	
	
	/**
	 * Static method to find the number of tests in the specified inventory file.
	 * The number of tests is found by loading the inventory file and then subtracting 
	 * the tests listed in any exclude files.
	 * 
	 * @param stfCore so that the workspace root can be discovered.
	 * @param inventoryFileRef points at the inventory file within the workspace. 
	 * @return the final number of tests.
	 * @throws StfException 
	 */
	public static int getNumberOfTests(StfCoreExtension stfCore, String inventoryFileRef) throws StfException {
		ArrayList<DirectoryRef> testRoots = stfCore.env().getTestRoots();
		try {
			ArrayList<String> exclusionFiles = LoadTestProcessDefinition.findExclusionFiles(testRoots, inventoryFileRef);
			
			InventoryData inventory = new InventoryData(testRoots, inventoryFileRef, exclusionFiles, true, false, dumpRequested);
			return inventory.getNumberOfTests();
		} catch (Exception e) {
			throw new StfException("Failed to parse inventory file", e);
		}
	}

	
	// Weightings allow fractional values, so find the best multiplier which will 
	// allow fast selection of tests whilst still preserving the desired selection
	// probabilities.
	// 
	// For example, with the following tests:
	//    test A, weighting = 1
	//    test B, weighting = 0.5
	//    test C, weighting = 1.25
	// A multiplier of 4 will allow the specified probabilities to be achieved: AAAABBCCCCC
	// So randomly picking for this weighting array 11 times would, on average, result in
	// 4A's, 2B's and 5C's. This exactly matches the specified distribution.
	// 
	// It's not always possible to pick a multiplier to get a perfect balance for all multipliers
	// so this selection algorithm chooses the closest multiplier.
	// For example, if the test list were:
	//    test X, weighting = 0.41
	//    test Y, weighting = 0.39
	//    test Z, weighting = 0.201
	// If we weren't allowing a multiplier higher than 10 then the best choice is 5, which produces 
	// a selection array of XXYYZ:
	//    Test X, Weighting=0.41,  *5 = 2.05,  RoundedTo=2  Error=0.05
	//    test Y, Weighting=0.39,  *5 = 1.95,  RoundedTo=2  Error=0.05
	// 	  test Z, Weighting=0.201, *5 = 1.005  RoundedTo=1  Error=0.005
	// Note that in this simple example the maximum difference the actual and perfect values is 0.05
	//
	// If all tests have a weighting of 1 then a multiplier of 1 is returned.
	private BigDecimal calculateWeightingMultiplier(ArrayList<AdaptorInterface> testList) {
		boolean bestMultiplierSet = false;
		int bestMultiplier = 1;
		BigDecimal biggestErrorForBestMultiplier = null;
		BigDecimal totalErrorForBestMultiplier = null;
		
		// Find the best multiplier by stepping through them
    	for (int multiplier=1; multiplier<1000; multiplier++) {
    		BigDecimal biggestError = BigDecimal.ZERO;
    		BigDecimal totalError = BigDecimal.ZERO;
    		long totalWeightings = 0;
    		
    		// Convert the multiplier to big decimal as it gets used a lot
    		BigDecimal multiplierBD = new BigDecimal(multiplier);
    		
    		for (AdaptorInterface test : testList) {
    			if (test.getWeighting().equals(BigDecimal.ONE)) {
    				totalWeightings += multiplier;
    			} else {
    				// Calculate actual weighting for current multiplier
    				BigDecimal proposedWeighting = test.getAdjustedWeighting(multiplierBD);
    				// Round actual weighting to whole number (at least 1)
        			BigDecimal roundedWeighting = new BigDecimal(test.getRoundedAdjustedWeighting(multiplierBD));
        			// Work out how big the actual rounding error is. 
        			// eg, weighting=1.5 for multiplier 3 gives actual weighting of 4.5. This gets rounded to 5 so 'error' is 0.5.
        			BigDecimal roundingError = roundedWeighting.subtract(proposedWeighting).abs();
					
					// Update counters
					biggestError = biggestError.max(roundingError);
					totalError = totalError.add(roundingError);
					totalWeightings += roundedWeighting.longValue();
    			}
    		}
    		
    		// Stop searching if the current multiplier will result in a test lookup table which is too big
    		if (totalWeightings > MAX_WEIGHTING_LOOKUP_SIZE) {
    			break;
    		}
    		
    		// Stop searching if perfect multiplier found
    		if (biggestError.equals(BigDecimal.ZERO)) {
    			bestMultiplier = multiplier;
    			break;
    		}
    		
    		// Decide if current multiplier gives better results than the best so far
    		boolean foundBetter = false;
    		if (!bestMultiplierSet) {
    			// First time round. Use this multiplier
    			foundBetter = true;
    		} else if (biggestError.compareTo(biggestErrorForBestMultiplier) < 0) {
    			// Use the current multiplier, as its maximum error is less than than the previous best
    			foundBetter = true;
    		} else if (biggestError.compareTo(biggestErrorForBestMultiplier) == 0  &&  totalError.compareTo(totalErrorForBestMultiplier) < 0) {
    			// Maximum error is the same, but this multiplier is preferred because the total of all errors is better
    			foundBetter = true;
    		}
    		
    		if (foundBetter) {
    			// Current multiplier has smaller selection balance error than the current best multiplier.
    			bestMultiplier = multiplier;
    			biggestErrorForBestMultiplier = biggestError;
    			totalErrorForBestMultiplier = totalError;
    			bestMultiplierSet = true;
    		}
    	}
    	
		return new BigDecimal(bestMultiplier);
	}
	
	
	/**
	 * @return an int containing the multiplication factor that should be applied to weightings, to make
	 * sure they are selected according to the probabilities controlled by their weightings. 
	 */
	public BigDecimal getWeightingMultiplier() { 
		return weightingMultiplier;
	}
}