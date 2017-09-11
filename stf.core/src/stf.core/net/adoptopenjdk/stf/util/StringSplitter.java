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

import java.util.ArrayList;


/**
 * This is a utility class which splits a string into parts.
 * It splits on spaces except if the spaces are inside double quotes.
 */
public class StringSplitter {
	private enum SplitState { NORMAL, INQUOTE }; 

	/**
	 * This method takes an argument string and splits it into individual arguments.
	 * It takes care not to split arguments inside a quoted part. 
	 * 
	 * So '-Xjit:counts="- - - - - - 1 1 1 1000 - " -Xgcpolicy:optthruput' would not 
	 * be split inside the quoted section, and return only 2 values.
	 * 
	 * @param argString is the full string containing multiple arguments.
	 * @return an ArrayList of strings containing a single argument.
	 */
	public static ArrayList<String> splitArguments(String argString) {
		argString = argString.trim();
		
		ArrayList<String> parts = new ArrayList<String>();
		
		StringBuilder output = new StringBuilder();
		SplitState state = SplitState.NORMAL;
		
		for (int i=0; i<argString.length(); i++) {
			char c = argString.charAt(i);
			
			if (state == SplitState.NORMAL && c == ' ') {
				// We have hit a space. Save the output found until this point.
				parts.add(output.toString());
				output.setLength(0);
			} else if (state == SplitState.NORMAL && c == '\"') {
				// Start of quoted section. Get ready to ignore spaces
				output.append(c);
				state = SplitState.INQUOTE;
			} else if (state == SplitState.INQUOTE && c == '\"') {
				// End of quoted section
				output.append(c);
				state = SplitState.NORMAL;
			} else {
				// Save the ordinary character
				output.append(c);
			}
		}
		
		// Tidy up any in-progress argument
		if (output.length() > 0) {
			parts.add(output.toString());
		}
		
		return parts;		
	}
}