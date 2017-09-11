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

package net.adoptopenjdk.stf.results;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import net.adoptopenjdk.stf.StfException;


/**
 * This class parses test results held in the form such as:
 *   suite=maths platform=linux_86-64 result=pass class=ArgumentsTests test=test01
 * 	 suite=maths platform=linux_86-64 result=fail class=ArgumentsTests test="test -02"
 * 
 * This parsing has primarily been added so that rules, describing which test failures can be
 * treated as having passed, can be held in text files.
 * An example:
 *   # Memory test is buggy on linux
 *   platform=linux_86-64 class=MemTests test=Memory01
 *   # All cpu tests are dodgy on all platforms. note '=~' does regular expression compare
 *   test=~testCpu.*
 *   
 * If a value needs to contain a space character then the value should be enclosed in double qutoes. eg:
 *    message="Comparison failure"
 */
public class ResultsParser {
	private String resultString;

	private enum FieldNames {
		SUITE,
		PLATFORM,
		RESULT,
		CLASS,
		TEST,
		MESSAGE,
		EXCEPTION;
	}
	
	
	public ResultsParser(String resultString) {
		this.resultString = resultString;
	}
	
	
	public ResultsParser(File exclusionsFile) throws StfException {
	    this(readFile(exclusionsFile));
	}


	public static String readFile(File exclusionsFile) throws StfException {
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new FileReader(exclusionsFile));
			String line = null;
			StringBuilder stringBuilder = new StringBuilder();
			String sep = System.getProperty("line.separator");

	        while ((line = reader.readLine() ) != null) {
	            stringBuilder.append(line);
	            stringBuilder.append(sep);
	        }

	        return stringBuilder.toString();
	    } catch (IOException e) {
	    	throw new StfException("Failed to read from file: " + exclusionsFile.getAbsolutePath(), e);
		} finally {
	        try {
				reader.close();
			} catch (IOException e) {
		    	throw new StfException("Failed to close file: " + exclusionsFile.getAbsolutePath(), e);
			}
	    }
	}
	
	
	/**
	 * Parse the test results
	 * @return an array list with one entry for each test result
	 */
	public ArrayList<TestStatus> parse() throws StfException {
		ArrayList<TestStatus> results = new ArrayList<TestStatus>();
		
		for (String line : resultString.split("\n")) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				continue;
			}
			if (trimmedLine.startsWith("#")) {
				continue;
			}
			
			TestStatus status = parseLine(trimmedLine);
			results.add(status);
		}
		
		return results;
	}

	
	// States used for parsing name/value pair lines. See comments below.
	private enum ParseState  { START, IN_NAME, CONDITION, IN_VALUE, IN_QUOTED_VALUE, FIELD_END };
	
	/**
	 * This method parses the name/value pairs for a line into a TestStatus object.
	 * It used to use a regular expression to extract these values but this was proving to 
	 * be unreliable on both IBM and Oracle JVMs when parsing long or complex lines.
	 * Such lines would sometimes fail with stack overflows.
	 * So the code now uses a state machine.
	 * 
	 * The parsing is more complex that may be assumed because:
	 *   1) The condition can be either '=' or '=~'
	 *   2) The value may be say 'test=t1' or optionally enclosed in double quotes as 'test="t1"'
	 *   3) Values in double quotes can contain whitespace.
	 *   4) Values in double quotes can contain quotes, which are encoded as \"
	 *   5) Error checking needs to be able to spot badly formatted lines.
	 *   
	 * Here is a simple example of the sort of line which has forced the conversion
	 * to a state machine. Note that the 2nd value contains whitespace, equals sign 
	 * and an encoded double-quote: test=BigTest2 message="Unknown user name: User=\"X\"" 
	 */
	private TestStatus parseLine(String line) throws StfException {
		//System.out.println(line);
		HashMap<String, TestStatus.FieldDetails> fields = new HashMap<String, TestStatus.FieldDetails>();
		
		String name = null;
		String condition = null;
		String value = null;
	
		ParseState state = ParseState.START;
		
		int i;
		char c;
		char next;
		StringBuilder buff = new StringBuilder();
		for (i=0; i<=line.length(); i++) {
			// Find the current and next character.
			// Force the use of '\n' character if at/past the end of the line.
			c = (i<line.length()) ? line.charAt(i) : '\n';
			next = (i+1 < line.length()) ? line.charAt(i+1) : '\n';

			boolean processedChar = false;
			while (!processedChar) {
				//System.out.printf("  %3d '%c' '%c' %-17s '%s'", i, c=='\n'?'\\':c, next=='\n'?'\\':next, state, buff.toString());
				switch (state) {
				case START:
					if (c == ' ' || c == '\t' || c == '\n') {
						// Absorb leading white space
						processedChar = true;
					} else {
						// This character starts a new name
						state = ParseState.IN_NAME;
					}
					break;
	
				case IN_NAME:
					if (c == '=') {
						// Name has finished. Capture and move to condition.
						name = buff.toString();
						buff.setLength(0);
						state = ParseState.CONDITION;
					} else {
						// Grab the next character in this name
						buff.append(c);
						processedChar = true;
					}
					break;
				
				case CONDITION:
					if (c == '=' && next == '~') {
						condition = "=~";
						i++;
					} else if (c == '=') {
						condition = "=";
					}
					processedChar = true;
					state = ParseState.IN_VALUE;
					break;
				
				case IN_VALUE:
					if (buff.length() == 0  &&  c == '\"') {
						// This value is enclosed in a double quote
						processedChar = true;
						state = ParseState.IN_QUOTED_VALUE;
					} else if (c == ' ' || c == '\t' || c == '\n') {
						// Have hit the end of the field
						state = ParseState.FIELD_END;
					} else {
						// This character is part of the value
						buff.append(c);
						processedChar = true;
					}
					break;

				case IN_QUOTED_VALUE:
					if (c == '\"') {
						// Have hit the end of the field
						state = ParseState.FIELD_END;
					} else if (c == '\\' && next == '\"') {
						// It's an encoded double-quote. ie. \"
						buff.append('"');
						i++;
					} else {
						// This character is part of the value
						buff.append(c);
					}
					processedChar = true;
					break;
					
				case FIELD_END:
					// Have reached the end of the value part. Capture it, and reset the state machine.
					value = buff.toString();
					buff.setLength(0);
					state = ParseState.START;
					
					// Field is complete, so store into hashMap
					TestStatus.FieldDetails fieldDetails = createFieldObject(line, name, condition, value);
					if (fields.containsKey(name)) {
				    	throw new StfException("Duplicate field used: '" + name + "' in: " + line);
				    }
				    fields.put(name, fieldDetails);
					//System.out.print("  Field completed. Name:'" + name + "' Condition:'" + condition + "' Value:'" + value + "'");
					break;
				}
				//System.out.println();
			}
		}
		
		if (state != ParseState.START) {
			throw new StfException("Malformed results line. Parsing failed at about character " + i + " on line: " + line);
		}
		
		// Build an object to represent the contents of this line
		TestStatus status = new TestStatus(line, fields.get("suite"), fields.get("platform"), fields.get("result"), fields.get("class"), fields.get("test"), fields.get("message"), fields.get("exception"), fields.get("expectation"));
		return status;
	}


	private TestStatus.FieldDetails createFieldObject(String line, String name, String comparison, String value)
			throws StfException {
		// Remove encoding for double-quotes. ie. return \" to ". 
		value = value.replace("\\\"", "\"");
		
		// Check and store the field name+value
		try {
			FieldNames.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new StfException("Found invalid field name: '" + name + "' on Line: " + line);
		}
		TestStatus.FieldDetails fieldDetails = new TestStatus.FieldDetails(name, comparison.equals("=~"), value);
		return fieldDetails;
	}
}