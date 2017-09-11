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

package net.adoptopenjdk.loadTestAnalysis;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.adoptopenjdk.loadTest.reporting.ExecutionRecord;
import net.adoptopenjdk.loadTest.reporting.ExecutionRecord.Action;


/** 
 * This class produces ascii charts showing thread activity during a test run.
 * 
 * Here is the activity of a trivial test run, showing the columns used for run with 
 * 2 suites which each have 2 threads: 
 *    Timestamp      Delta  Event       Test                                  0   1   0   1
 *   10:16:32.820     +12ms Start   1 MarkReset                           -  1MR            
 *   10:16:32.822     +14ms Start   1 ProtectedVars                       -   |  1PV        
 *   10:16:32.823     +15ms Start   2 MarkReset                           -   |   |  2MR    
 *   10:16:32.824     +16ms Start   2 ProtectedVars                       -   |   |   |  2PV
 *   10:16:32.827     +19ms Passed  1 ProtectedVars                       -   |   V   |   | 
 *   10:16:32.827     +19ms Passed  2 ProtectedVars                       -   |       |   V 
 *   10:16:32.828     +20ms Start   1 ProtectedVars                       -   |  1PV  |     
 *   10:16:32.828     +20ms Start   2 ProtectedVars                       -   |   |   |  2PV
 *   10:16:32.829     +21ms Passed  2 ProtectedVars                       -   |   |   |   V 
 *   10:16:32.830     +22ms Passed  1 ProtectedVars                       -   |   V   |     
 *   10:16:32.830     +22ms Start   2 LineNumberReader                    -   |       |  2LN
 *   10:16:32.831     +23ms Start   1 LineNumberReader                    -   |  1LN  |   | 
 *   10:16:32.832     +24ms Passed  2 MarkReset                           -   |   |   V   | 
 *   10:16:32.832     +24ms Passed  1 MarkReset                           -   V   |       | 
 *   10:16:32.833     +25ms Passed  1 LineNumberReader                    -       V       | 
 *   10:16:32.833     +25ms Passed  2 LineNumberReader                    -               V 
 */
class RunActivitySummaryReport {
	private ArrayList<ExecutionRecord> testActivity;
	private long startTime;
	
	private LinkedHashMap<String, String> longToShortTestNames = new LinkedHashMap<String, String>();
	
	
	RunActivitySummaryReport(ArrayList<ExecutionRecord> testActivity, long startTime) {
		this.testActivity = testActivity;
		this.startTime = startTime;
	}
	
	
	String produceReport() {
		System.out.println("Producting activity report for " + this.testActivity.size() + " observations");
		createLongToShortTestNameMapping();
		
		ThreadMapper threadMapper = new ThreadMapper(testActivity);
		String activityChart = createActivityChart(threadMapper, startTime);
		String legend = createReportLegend(threadMapper);
		
		return activityChart + legend;
	}


	// This method abbreviates full test names, creating short test names that will 
	// fit into the limited space of the runtime activity chart.
	// eg, abbreviates testLongMultiply for suite 2 to '2LM' 
	private void createLongToShortTestNameMapping() {
		// Work out how many suites there are
		int numSuites = 0;
		for (ExecutionRecord activity : testActivity) {
			numSuites = Math.max(numSuites, activity.getSuiteId());
		}

		// For each suite, abbreviate test names.
		for (int suiteId=1; suiteId<=numSuites; suiteId++) {
			// First build a set with the unique test names for the current suite
			LinkedHashSet<String> testNames = new LinkedHashSet<String>();
			for (ExecutionRecord activity : testActivity) {
				if (activity.getSuiteId() == suiteId) {
					testNames.add(activity.getTestName());
				}
			}
			
			// Process each test long name to create an abbreviation. eg, testStringConcatFunction -> SC
	        for (String longTestName : testNames) {
	        	// Remove the 'test' word from the testcase name
	        	String cleanedTestName = longTestName.replace("test", "");
	        	cleanedTestName = cleanedTestName.replace("Test", "");

	        	// Make sure the first character is upper case. 
	        	// So that names such as 'hashcode_Boolean' can still create a sensible abbreviation
	        	if (Character.isLowerCase(cleanedTestName.charAt(0))) {
	        		cleanedTestName = cleanedTestName.substring(0,1).toUpperCase() + cleanedTestName.substring(1); 
	        	}
	        			
	        	// Build up the possible short names, from high to low preference.
	        	// We prefer to build a 2 char short name, but there are not that many 
	        	// combinations available so also build 3 character short names.
	        	ArrayList<String> abbreviations = new ArrayList<String>();
	        	abbreviateTestName(cleanedTestName, abbreviations);
	        	
				// Remove all the vowels, and run through abbreviation again
	        	cleanedTestName = cleanedTestName.replaceAll("[aeiou]\\B", "");
	        	abbreviateTestName(cleanedTestName, abbreviations);
				
	        	// Get rid of unsuitable abbreviations (empty or too long)
	        	abbreviations = cleanAbbreviations(abbreviations);
	        	
	        	// Pick the first abbreviation that has not already been used
	        	String shortTestName = null;
	        	for (String abbr : abbreviations) { 
	        		if (!longToShortTestNames.values().contains("" + suiteId + abbr)) {
	        			shortTestName = abbr;
	        			break;
	        		}
	        	}
	        	
	        	// If none of the abbreviations were picked then generate a name.
	        	if (shortTestName == null) {
	        		for (int i=0; i<cleanedTestName.length() && shortTestName == null; i++) {
	        			char c = cleanedTestName.charAt(i);
	        			for (int j=0; j<99; j++) {
	        				String candidate = "" + suiteId + c + j;
	                		if (!longToShortTestNames.values().contains(candidate)) {
	                			shortTestName = candidate;
	                			break;
	                		}
	        			}
	        		}
	        	}
	
	        	// Add chosen abbreviation to long to short map
	           	String key   = suiteId + "." + longTestName;
				String value = suiteId + shortTestName;
				longToShortTestNames.put(key, value);
	        }
		}
	}


	private void abbreviateTestName(String cleanedTestName, ArrayList<String> abbreviations) {
    	// Extract significant camel-case letters. eg, LongTimerRun extracts 'Lo' 'Ti' and 'Ru'
    	String pairs[] = { "  ", "  ", "  " };
        Pattern pattern = Pattern.compile(".*?([A-Z][A-Za-z]).*?([A-Z][A-Za-z])?.*?([A-Z][A-Za-z])?.*?");
    	Matcher matcher = pattern.matcher(cleanedTestName);
    	int i=0;

    	while (matcher.find()) {
    		pairs[i++] = matcher.group(1);
    		if (i >= pairs.length) break;
    	}

		char c0 = pairs[0].charAt(0);
		char c1 = pairs[1].charAt(0);
		char c2 = pairs[2].charAt(0);
     	
		// Combinations of the first letter of each camel case pair
		abbreviations.add("" + c0 + c1);
		abbreviations.add("" + c1 + c2);
		abbreviations.add("" + c0 + c2);
		
		// Use camel case pairs as-is
		abbreviations.add(pairs[0]);
		abbreviations.add(pairs[1]);
		abbreviations.add(pairs[2]);

		// Long name, from all 3 pairs, or parts off
		abbreviations.add(pairs[0] + pairs[1]);
		abbreviations.add(pairs[0] + pairs[2]);
		abbreviations.add(pairs[1] + pairs[2]);
		
		// Short names, from first letter of camel case
		abbreviations.add("" + c0);
		abbreviations.add("" + c1);
		abbreviations.add("" + c2);
		
		// First and last letter of cleaned test name
		char firstChar = cleanedTestName.charAt(0);
		char lastChar  = cleanedTestName.charAt(cleanedTestName.length()-1);
		abbreviations.add("" + firstChar + lastChar);
	}


	// Takes a list of possible abbreviations and returns a cleaned up list
	private ArrayList<String> cleanAbbreviations(ArrayList<String> abbreviations) {
		ArrayList<String> cleaned = new ArrayList<String>();
		
		for (String s : abbreviations) {
			s = s.trim();
			if (s.length() > 3) {
				cleaned.add(s.substring(0,3));  // It's a long name. Only use first 3 characters
			} else if (s.length() >= 1) {
				cleaned.add(s); // Usable, as between 1 and 3 chars long
			}
		}
		
		return cleaned;
	}


	private String createActivityChart(ThreadMapper threadMapper, long startTime) {
		StringBuilder chart = new StringBuilder();
		SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

		// Find the length of the longest test name - so help with table formatting
		int longestTestNameLength = -1;
		for (ExecutionRecord activity : this.testActivity) { 
			longestTestNameLength = Math.max(longestTestNameLength, activity.getTestName().length());
		}
		
	    // Build banner text to describe column contents
	    StringBuilder banner = new StringBuilder();
	    String bannerFormat = " Timestamp      Delta  Event       Test %" + (longestTestNameLength-5) + "s";
	    banner.append(String.format(bannerFormat, " "));
	    for (int i=0; i<threadMapper.getNumThreads(); i++) { 
	    	String threadId = String.format("%4d", threadMapper.getThreadDetailsForColumn(i).getThreadId());
	    	banner.append(threadId);
	    }
	    
	    chart.append(banner + "\n");
	    
	    // Create an array to keep track of whether or not each thread is working. Index by column number
	    boolean[] threadBusy = new boolean[threadMapper.getNumThreads()]; 
	    
	    // Produce a 1 line summary of what's going on when each start/stop event was logged
	    for (ExecutionRecord activity : this.testActivity) { 
	    	int threadIndex = threadMapper.getColumnIndex(new ThreadDetails(activity.getSuiteId(), activity.getThreadName()));
	    	String testShortName = longToShortTestNames.get(activity.getTestReference());

	    	// Prepare data for start of trace line. Summarises the reason for the current line.
	    	String formattedTime = dateFormatter.format(new Date(activity.getTimestamp()));
			long elapsedTime = activity.getTimestamp() - startTime;
			String action = "Start";
			if (activity.getAction() == Action.PASSED) {
				action = "Passed";
			} else if (activity.getAction() == Action.FAILED) {
				action = "Failed";
			}

			String tracePrefix = String.format("%s %+7dms %-6s %2d %-" + longestTestNameLength + "s - ", 
					formattedTime, elapsedTime, action, activity.getSuiteId(), activity.getTestName());
			chart.append(tracePrefix);
			
			// Output activity summary for each thread. 4 characters per thread.
			for (int i=0; i<threadBusy.length; i++) {
				if (i == threadIndex && activity.getAction() == Action.STARTED) {
					threadBusy[i] = true;
					chart.append(String.format("%4s", testShortName));
				} else if (i == threadIndex && activity.getAction() == Action.PASSED) {
					threadBusy[i] = false;
					chart.append("  V ");
				} else if (i == threadIndex && activity.getAction() == Action.FAILED) {
					threadBusy[i] = false;
					chart.append("  # ");
				} else if (threadBusy[i]) {
					chart.append("  | ");
				} else {
					chart.append("    ");
				}
			}
			chart.append("\n");
	    }
	    
	    chart.append(banner + "\n");
	    
	    return chart.toString();
	}
	
	
	private String createReportLegend(ThreadMapper threadMapper) {
		StringBuilder builder = new StringBuilder();
		
		builder.append("\nThreadnames to short name mappings:\n");
		for (int i=0; i<threadMapper.getNumThreads(); i++) {
			ThreadDetails t = threadMapper.getThreadDetailsForColumn(i);
			String threadInfo = String.format("  %s %s -> %s\n", t.getSuiteId(), t.getThreadName(), t.getShortThreadName());
			builder.append(threadInfo);
		}

		// Produce listing showing test name abbreviations
		builder.append("TestNames:\n");
		for (Entry<String, String> machineEntry : longToShortTestNames.entrySet()) {
			String longTestName  = machineEntry.getKey();
			String shortTestName = machineEntry.getValue();
			builder.append("  " + longTestName + " -> " + shortTestName + "\n");
		}
		
		return builder.toString();
	}
}