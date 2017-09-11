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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import net.adoptopenjdk.loadTest.reporting.ExecutionRecord;


/**
 * This class plays a key role in producing a chart which shows thread activity. 
 * It allocates columns in the chart to individual threads, and allows the caller 
 * to map between thread information and column numbers.
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
class ThreadMapper {
	private TreeSet<ThreadDetails> knownThreads = new TreeSet<ThreadDetails>();

	// Maps between thread details and a column index
	// eg, if we have some information about thread 3 for suite 1, we can use this map to find 
	// out that this goes into the 3rd column.
	private HashMap<ThreadDetails, Integer> threadToColumn = new HashMap<ThreadDetails, Integer>();
	
	// This map is the reverse of the previous map.
	// It goes from a column number back to thread information.
	private HashMap<Integer, ThreadDetails> columnToThread = new HashMap<Integer, ThreadDetails>();

	
	ThreadMapper(ArrayList<ExecutionRecord> testActivity) {
		// Build a sorted of all unique threads
		for (ExecutionRecord activity : testActivity) { 
	       	// Add to set of known threads
	       	ThreadDetails threadDetails = new ThreadDetails(activity.getSuiteId(), activity.getThreadName()); 
	        knownThreads.add(threadDetails);
		}
		
		// Thread details are now in a consistent order. Sorted by 1) suite id, from lowest to highest
		// and 2) with each suite sorted by thread number, again lowest to highest.
		// Build a map to allow a lookup of thread reference to column index, and vice versa. 
		//  
		int i=0;
	    for (ThreadDetails t : knownThreads) {
	    	threadToColumn.put(t, i);
	    	columnToThread.put(i, t);
	    	i++;
	    }
	}


	int getNumThreads() {
		return knownThreads.size();
	}

	
	/**
	 * @return the column number allocated to the supplied thread.
	 */
	int getColumnIndex(ThreadDetails threadDetails) {
		return threadToColumn.get(threadDetails);
	}
	
	
	/**
	 * @return information about the thread allocated to the referenced column number. 
	 */
	ThreadDetails getThreadDetailsForColumn(int columnIndex) {
		return columnToThread.get(columnIndex);
	}
}