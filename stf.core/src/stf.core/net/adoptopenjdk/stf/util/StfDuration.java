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

package net.adoptopenjdk.stf.util;


/**
 * Java's Duration class is not available in Java6, which STF uses, so this is 
 * a mini equivalent implementation.
 * It has the same advantage as the Duration class in that it makes the units 
 * for time specification very clear.
 */
public class StfDuration {

	private long milliseconds;
	
	private StfDuration(long milliseconds) {
		this.milliseconds = milliseconds;
	}
	
	public static StfDuration ofMilliseconds(int milliseconds) {
		return new StfDuration(milliseconds);
	}
	
	public static StfDuration ofSeconds(int seconds) {
		return new StfDuration(seconds * 1000);
	}
	
	public static StfDuration ofMinutes(int minutes) {
		return new StfDuration(minutes * 60 * 1000);
	}

	public static StfDuration ofHours(int hours) {
		return new StfDuration(hours * 3600 * 1000);
	}

	public static StfDuration ofDays(int days) {
		return StfDuration.ofHours(days * 24);
	}

	public static StfDuration ofHMSandMillis(int hours, int minutes, int seconds, int milliseconds) {
		return new StfDuration(hours*3600*1000 + minutes*60*1000 + seconds*1000 + milliseconds);
	}
	
	public long getMilliseconds() {
		return milliseconds;
	}

	public long getSeconds() {
		return milliseconds / 1000;
	}

	// convert the duration to a human readable h+m+s+ms value. 
	// eg, returns values such as '3h', '45m', "2m30s, "1h30m", "3h0m0s500ms", etc
	public String toString() {
		long h = milliseconds / (60*60*1000);
		long m = (milliseconds / (60*1000)) % 60;
		long s = (milliseconds / 1000) % 60;
		long ms =  milliseconds % 1000;
		
		StringBuilder buff = new StringBuilder();
		if (h > 0) {
			buff.append(Long.toString(h) + "h");
		}
		if (m > 0 || (h > 0 && (s > 0 || ms > 0))) {
			buff.append(Long.toString(m) + "m");
		}
		if (s > 0 || ((h > 0 || m > 0) && ms >0)) {
			buff.append(Long.toString(s) + "s");
		}
		if (ms > 0) {
			buff.append(Long.toString(ms) + "ms");
		}
		
		return buff.toString();
	}
}