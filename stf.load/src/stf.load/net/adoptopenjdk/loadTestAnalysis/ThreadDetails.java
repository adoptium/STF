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

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Holds details about a single thread.
 * 
 * This is mostly needed because in TestNG different suites reuse the same thread names!
 * So this class is needed so that we can tell the difference between a thread running 
 * tests for suite 1 called say 'PoolService-2' and a thread with the same name
 * running tests for suite 2. 
 */
class ThreadDetails implements Comparable<ThreadDetails> {
	int suiteId;
	String threadName;
	int threadId;  

	ThreadDetails(int suiteId, String threadName) {
		this.suiteId = suiteId;
		this.threadName = threadName;

		// Extract the thread number from the thread name. eg PoolService-2 has an id of 2
        Pattern pattern = Pattern.compile(".*?([0-9]*)$");
        Matcher matcher = pattern.matcher(threadName);
       	if (!matcher.find()) { 
       		throw new IllegalStateException("Failed to extract thread number from thread name: " + threadName);
       	}
       	threadId = Integer.parseInt(matcher.group(1));
	}
	
	Object getSuiteId() {
		return suiteId;
	}

	Object getThreadName() {
		return threadName;
	}

	Object getThreadId() {
		return threadId;
	}

	Object getShortThreadName() {
		return suiteId + "." + threadId;
	}

	
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof ThreadDetails)) return false;
		ThreadDetails other = (ThreadDetails) o;
		
		return this.suiteId == other.suiteId 
				&& this.threadName.equals(other.threadName)
				&& this.threadId == other.threadId;
	}
	
	public int hashCode() { 
		int result = 17;
		result = 31 * result * suiteId;
		result = 31 * result * threadName.hashCode();
		result = 31 * result * threadId;
		return result;
	}
	
    public String toString() {
		return suiteId + " " + threadName + " " + threadId;
	}

	public int compareTo(ThreadDetails other) {
    	if (suiteId == other.suiteId) { 
    		return threadId - other.threadId;
    	}
    	return suiteId - other.suiteId;
	}
}