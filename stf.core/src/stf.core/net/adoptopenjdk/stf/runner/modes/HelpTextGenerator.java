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

package net.adoptopenjdk.stf.runner.modes;

import java.util.ArrayList;
import java.util.StringTokenizer;


/**
 * This is a support class which aims to make it easier for extensions to 
 * provide help information, which is triggered by running stf with '-help'.
 * 
 * The generated help output copies the style of linux/unix man pages.
 */
public class HelpTextGenerator {
	private String INDENT2 = "   ";
	private String INDENT3 = "       ";
	private String INDENT4 = "              ";
	
	private int screenWidth = 79;

	private String ansiBold = "";
	private String ansiUnderline = "";
	private String ansiReset = "";

	// Counters to help add blank lines in at sensible places	
	private int numSections = 0;
	private int numArgumentsInSection = 0;
	
	
	public HelpTextGenerator(boolean isLinux) {
		if (isLinux) {
			ansiBold      = "\u001B[1m";
			ansiUnderline = "\u001B[4m";
			ansiReset     = "\u001B[0m";
		}
	}

	
	/**
	 * Outputs a heading for a new section of the man page, eg 'NAME' or 'SYNOPSIS'.
	 * This is the highest level of output.
	 * The title is shown in bold text with no indentation.
	 * 
	 * @param title contains the text to output.
	 */
	public void outputHeading(String title) {
		System.out.println();
		System.out.println(ansiBold + title.toUpperCase() + ansiReset);
		numSections = 0;
	}

	
	/**
	 * Outputs a lower priority heading.
	 * This is indented slightly, so that it fits below a 'heading'.
	 *
	 * @param text contains the text to output.
	 */
	public void outputSection(String text) {
		if (numSections > 0) {
			System.out.println();
		}
		numSections++;

		System.out.println(INDENT2 + ansiBold + text + ansiReset);
		numArgumentsInSection = 0;
	}


	/**
	 * This method outputs a block of formatted text.
	 * The text is indented to below a section title.
	 * '\n' characters can be used to force a new line.
	 * 
	 * @param text contains the text to output.
	 */
	public void outputText(String text) {
		output(INDENT3, text);
	}
	
	
	/**
	 * Outputs an argument name in a standardised format.
	 * 
	 * @param argName contains the name of the argument.
	 */
	public void outputArgName(String argName) {
		outputArgName(argName, null);
	}

	
	/**
	 * Outputs a line describing an argument and its value.
	 * For example it produces a line such as '       -max-count=NUM'.
	 * 
	 * @param argName contains the name of the argument.
	 * @param argSpec names the value that can be expected.
	 */
	public void outputArgName(String argName, String argSpec) {
		if (numArgumentsInSection > 0) {
			System.out.println();
		}
		numArgumentsInSection++;

		if (argSpec == null) { 
			System.out.println(INDENT3 + ansiBold + argName + ansiReset);
		} else {
			System.out.println(INDENT3 + ansiBold + argName + "=" + ansiReset + ansiUnderline + argSpec + ansiReset);
		}
	}

	
	/**
	 * Outputs in a standard format some text to describe an argument.
	 * The text is split as necessary and indented.  
	 * '\n' characters can be used to force a new line.
	 * 
	 * @param argDescription contains text to describe the argument.
	 */
	public void outputArgDesc(String argDescription) {
		output(INDENT4, argDescription);
	}
	
	
	/**
	 * Force the output of a blank line. 
	 * Can be sparingly used to add lines to improve readability. 
	 */
	public void outputLine() {
		System.out.println();
	}

	
	private void output(String indent, String text) {
		ArrayList<String> lines = splitIntoLines(text, screenWidth - indent.length());
		
		for (String lineText : lines) { 
			System.out.print(indent);
			System.out.println(lineText);
		}
	}

	
	/**
	 * This method takes some text and splits it into multiple lines, such that 
	 * none of the lines exceed a maximum size.
	 * It supports the use of '\n' characters which can be used to force the
	 * start of a new line. 
	 * 
	 * @param text is the text to format 
	 * @param maxLineLength is the maximum number of characters which a line can contain.
	 * @return An array list containing the text for each line.
	 */
	private ArrayList<String> splitIntoLines(String text, int maxLineLength) {
		ArrayList<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder(maxLineLength);
		
		// Split the text based on spaces and newline characters
		StringTokenizer tokenizer = new StringTokenizer(text.trim(), " \n", true);
		while (tokenizer.hasMoreTokens()) {
		    String word = tokenizer.nextToken();
		    
		    if (word.equals("\n")) {
		    	// Force a newline
		    	lines.add(line.toString());
		    	line.setLength(0);
		    	
		    } else if (!word.equals(" ")) {
		    	if (word.equals(":space:")) {
		    		word = " ";
		    	}
		    	if (line.length() + word.length() > maxLineLength) {
		    		// Need to start a new line to hold the current word
			    	lines.add(line.toString());
			    	line.setLength(0);
		    	} else if (line.length() > 0) {
		    		// Make sure there is a space before the word gets added
		    		line.append(" ");
		    	}
		    	line.append(word);
		    }
		}
		
		// Don't forget any part build output line
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		
		return lines;
	}
}