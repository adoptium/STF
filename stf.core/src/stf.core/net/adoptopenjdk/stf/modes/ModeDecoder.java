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

package net.adoptopenjdk.stf.modes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.extensions.Stf;


/**
 * Returns a set of JVM arguments for a given mode.
 * eg, '-mode=105', '-mode=Random' or '-mode=Random_s26654791' 
 */
public class ModeDecoder {
    private static final Logger logger = LogManager.getLogger(ModeDecoder.class.getName());

    // Lookup the contents of the modes file to translate a mode name to actual VM arguments.
	public static String decodeModeName(StfEnvironmentCore environmentCore, String modeName) throws StfException {
		logger.debug("Attempting to find java-args for mode/variation '" + modeName + "'");
		
		if (!modeName.equals("NoOptions") && !environmentCore.isUsingIBMJava()) {
			throw new StfError("Modes are not supported on non-IBM JVMs");
		}
		
		String args;
		if (modeName.equals("NoOptions")) {
			args = "";
			logger.info("Using Mode " + modeName + ". Values = '" + args.trim() + "'");

		} else if (modeName.toLowerCase().startsWith("random")) {
			// Extract optional count and seed values, eg -mode=random_c3_s34543566
			long count       = extractOptionalNumber(modeName, "_c(\\d+)", 1);
			long initialSeed = extractOptionalNumber(modeName, "_s(\\d+)", -1);
			
			// Generate a new set of random JVM arguments
			int wordSize = environmentCore.getWordSize();
			RandomModesGenerator randModes = new RandomModesGenerator(wordSize, initialSeed, (int) count);
			args = randModes.getRandomModeData(wordSize, initialSeed, (int) count);
			
			// Capture the seed used, to allow later reproduction
		  	String argumentComment = ", using seed " + randModes.getSeed();
			environmentCore.updateProperty(Stf.ARG_JAVA_ARGS_EXECUTE_COMMENT, argumentComment);

			logger.info("Using Mode " + modeName + ". Values = '" + args.trim() + "'");
			
		} else {
			// Decode the named mode/variation
			// Look at both the modes.xml file and the potential multiple values from the testplan files
			ArrayList<JvmArgDetails> modeJavaArgs = ModeFileReader.decodeModeName(environmentCore, modeName);
			ArrayList<JvmArgDetails> testplanJavaArgs = new VariationsSearcher().findMode(environmentCore, modeName);
			
			// Amalgamate the results of searching modes.xml and the testplan variations
			ArrayList<JvmArgDetails> allJavaArgs = new ArrayList<JvmArgDetails>();
			allJavaArgs.addAll(modeJavaArgs);
			allJavaArgs.addAll(testplanJavaArgs);
			logger.debug("Found " + allJavaArgs.size() + " definitions for mode/variation: " + modeName);

			// Fail if mode doesn't exist
			if (allJavaArgs.isEmpty()) {
				throw new StfError("Unknown mode/variation: '" + modeName + "'. Check modes.xml or variations.xml files");
			}
			
			// Make sure that discovered java-args are the same
			HashSet<String> argsSet = new HashSet<String>();
			for (JvmArgDetails mode : allJavaArgs) {
				argsSet.add(mode.getJavaArgs());
			}
			if (argsSet.size() > 1) {
				StringBuilder errorText = new StringBuilder("Mode/variation '" + modeName + "' has ambiguous java-args. " + argsSet.size() + " different definitions found:");
				int argSetNum = 1;
				for (String argStr : argsSet) {
					errorText.append("\n" + argSetNum + ") " + argStr);
					argSetNum++;
					for (JvmArgDetails argDetails : allJavaArgs) {
						if (argDetails.getJavaArgs().equals(argStr)) {
							errorText.append("\n       Used in: " + argDetails.getOriginatingFile().getAbsolutePath());
						}
					}
				}
				throw new StfError(errorText.toString());
			}
			
			// To reach this point all java-args must have the same value, so just use the first set of values
			args = allJavaArgs.get(0).getJavaArgs();

			// Show java-args that are going to be used and where they have come from
			String filesText = allJavaArgs.size() == 1 ? "file" : "files";
			logger.info("Using Mode " + modeName + ". Values = '" + args.trim() + "', which was found in " + allJavaArgs.size() + " " + filesText + ":");
			for (JvmArgDetails argDetails : allJavaArgs) {
				logger.info("  " + argDetails.getOriginatingFile().getAbsolutePath());
			}
		}
		
		return args;
	}


	// Attempts to parse a number for optional mode arguments
	private static long extractOptionalNumber(String modeName, String regex, int defaultValue) {
		long result = defaultValue;
		
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(modeName);
		while (m.find()) {
			result = Long.parseLong(m.group(1));
		}
		
		return result;
	}
}
