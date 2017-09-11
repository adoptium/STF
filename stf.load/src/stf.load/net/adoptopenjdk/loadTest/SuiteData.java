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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.loadTest.adaptors.AdaptorInterface;


/**
 * Holds data on a single suite.
 * 
 * Also decides on the ordering of test execution for the suites tests.  
 */
public class SuiteData {
    private static final Logger logger = LogManager.getLogger(SuiteData.class.getName());
	
	public enum SelectionMode { SELECTION_SEQUENTIAL, SELECTION_RANDOM };

	// Suite level parameters 
	private final int suiteId;
	private final long numThreads;
	private final long numberTests;
	private final int repeatCount;  // How many times to run each test before picking the next one
	private final long minThinkingTime;
	private final long maxThinkingTime; 
	private final SelectionMode selection;

	// The tests which this suite will run
	private final InventoryData inventory;
	
	// Data to keep track of test selection.
	// Allows consistent and repeatable choice of tests
	private Random rnd;
	private long outstandingTestCount;
	private AdaptorInterface currentTest = null;
	private int outstandingRepeats = 0;
	
	// Data for sequential selection
	private int lastUsedTest = -1;

	// Data for random test selection
	private AdaptorInterface weightedTestMap[];
	private int numWeightedTests;


	SuiteData(int suiteNum, int numThreads, long seed, InventoryData inventory, long numberTests, int repeatCount, long minThinkingTime, long maxThinkingTime, SelectionMode selection) {
		this.suiteId = suiteNum;
		this.numThreads = numThreads;
		this.inventory = inventory;
		this.numberTests = numberTests;
		this.repeatCount = repeatCount;
		this.minThinkingTime = minThinkingTime;
		this.maxThinkingTime = maxThinkingTime;
		this.selection = selection;
		
		this.rnd = new Random(seed);
		this.outstandingTestCount = numberTests;
		
		if (selection == SelectionMode.SELECTION_RANDOM) {
			weightedTestMap = createWeightedTestMap(inventory);
			numWeightedTests = weightedTestMap.length;
		}
	}
	

	public int getSuiteId() {
		return suiteId;
	}
	
	public String getInventoryFileRef() {
		return inventory.getInventoryFileRef();
	}

	public long getNumberThreads() {
		return numThreads;
	}

	public InventoryData getInventory() {
		return inventory;
	}

	/**
	 * @return double containing percentage completed for this suite, or '-1'
	 * if not workload bound.
	 */
	public synchronized double getPercentageDone() {
		if (numberTests == -1) {
			return -1.0;
		}
		
		double numberStarted = numberTests - outstandingTestCount;
		double percentageDone = (numberStarted / (double) numberTests) * 100.0;
		return percentageDone;
	}

	public synchronized long getMinThinkingTime() {
		return minThinkingTime;
	}

	public synchronized long getMaxThinkingTime() {
		return maxThinkingTime;
	}


	/**
	 * @return The next test to run, or null if it's time to finish load testing.
	 */
	synchronized AdaptorInterface getNextTest() {
		if (outstandingTestCount == 0) {
			return null;  // No more tests to run
		}
		
		// Execution only gets here if there is either 1) some time left or 2) some more tests to run
		if (outstandingRepeats == 0) {
			// Pick the next test
			if (selection == SelectionMode.SELECTION_SEQUENTIAL) {
				if (++lastUsedTest >= inventory.getNumberOfTests()) {
					lastUsedTest = 0;
				}
				currentTest = inventory.getTest(lastUsedTest);
			} else {
				// Random mode
				int testNum = rnd.nextInt(numWeightedTests);
				currentTest = this.weightedTestMap[testNum];
			}
			outstandingRepeats = repeatCount;
		}

		if (outstandingTestCount != -1) {
			outstandingTestCount--;  // Test run is limited by test count
		}
		outstandingRepeats--;
		
		return currentTest;
	}
	

	// Create a weighting adjusted test lookup table.
	// This supports fast test selection whilst still honouring the weighting for each test.
	//
	// For example if we have the following test inventory:
	//    test A, weighting = 1
	//    test B, weighting = 0.5
	//    test C, weighting = 1.25
	// Then InventoryData holds:
	//    [0] test A, adjustedWeighting = 4
	//    [1] test B, adjustedWeighting = 2
	//    [2] test C, adjustedWeighting = 5
	// So the array used to randomly pick tests with the correct distribution is:
	//    00001122222
	private AdaptorInterface[] createWeightedTestMap(InventoryData inventory) {
		ArrayList<AdaptorInterface> weightedMap = new ArrayList<AdaptorInterface>();
		
		BigDecimal weightingMultiplier = inventory.getWeightingMultiplier();
		
		for (int i=0; i<inventory.getNumberOfTests(); i++) {
			AdaptorInterface test = inventory.getTest(i);
			int testWeighting = test.getRoundedAdjustedWeighting(weightingMultiplier);
			for (int repeat=0; repeat<testWeighting; repeat++) {
				weightedMap.add(test);
			}
		}
		
		// Convert to map to an array of ints (for faster access)
		AdaptorInterface[] weightedArray = new AdaptorInterface[weightedMap.size()];
	    for (int i=0; i<weightedMap.size(); i++) {
	        weightedArray[i] = weightedMap.get(i);
	    }
	   
    	logger.debug("Weighted array:");
    	for (int x=0; x<weightedArray.length; x++) {
    		logger.debug("  [" + x + "] " + weightedArray[x]);
	    }

	    return weightedArray;
	}
}