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

import net.adoptopenjdk.stf.StfException;


/**
 * This utility class converts human readable time descriptions into seconds.
 * 
 * Supported values are:
 *   'h' for hours
 *   'm' for minutes
 *   's' for seconds
 *   'ms' for milliseconds
 *   
 * For example '30s', '2m' or '6h'.
 * Values can be combined such as '1h15m' or '6h30m10s250ms'.
 */
public class TimeParser {
	/**
	 * Converts a human friendly time value into seconds.
	 * 
	 * @param timeSpecification is a string containing the hours, minutes and seconds. eg '1h30m'. 
	 * @return a StfDuration object representing the time from the timeSpecification argument. 
	 * @throws StfException if the timeSpecification string is not in the expected format.
	 */
	public static StfDuration parseTimeSpecification(String timeSpecification) throws StfException {
		if (timeSpecification == null || timeSpecification.isEmpty()) { 
			throw new StfException("Time specification must be non-zero");
		}
		timeSpecification = timeSpecification.trim();
		
		int hours   = 0;
		int minutes = 0;
		int seconds = 0;
		int millis  = 0;
		
		int i = 0;
		while (i < timeSpecification.length()) {
			// Parse the next time component. eg '30m'
			// Firstly, work out the character positions for the numeric part
			int digitStart = i;
			while (i < timeSpecification.length() && Character.isDigit(timeSpecification.charAt(i))) {
				i++;
			}
			int digitEnd = i;
			
			// Parse the value for the numeric part
			if (digitStart == digitEnd) {
				throw new StfException("Failed to find numeric value at offset " + digitStart + " of time specification: " + timeSpecification);
			}
			int value = Integer.parseInt(timeSpecification.substring(digitStart, digitEnd));
			
			// Extract the unit string. Should be one of 'h', 'm', 's' or 'ms'
			if (i >= timeSpecification.length()) {
				throw new StfException("No time unit specified at offset " + i + " of time specification: " + timeSpecification);
			}
			char unitChar1 = timeSpecification.charAt(i++);
			char unitChar2 = ' ';
			if (i < timeSpecification.length() && Character.isLetter(timeSpecification.charAt(i))) {
			    // Looks like the unit type has a 2nd character.
				unitChar2 = timeSpecification.charAt(i++);
			}
			
			if (unitChar1 == 'h' && unitChar2 == ' ') {
				hours += value;
			} else if (unitChar1 == 'm' && unitChar2 == ' ') {
				minutes += value;
			} else if (unitChar1 == 's' && unitChar2 == ' ') {
				seconds += value;
			} else if (unitChar1 == 'm' && unitChar2 == 's') {
				millis += value;
			} else { 
				String timeType = " " + unitChar1 + unitChar2;
				throw new StfException("Unknown time unit '" + timeType.trim() + "' in time specification: '" + timeSpecification + "'. Allowable units are 'h', 'm', 's' or 'ms'");
			}
		}
		
		// Build duration of the specified value
		StfDuration duration = StfDuration.ofHMSandMillis(hours, minutes, seconds, millis);
		return duration;
	}
}