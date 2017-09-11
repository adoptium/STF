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
import java.util.Arrays;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * This class generates random sets of JVM options for testing purposes. 
 * The options generated will always contain 1 jit and 1 gc option.
 *  
 * To run within STF the mode string needs to start with 'random...'.
 * If mode string is 'random_cXXX' where X is numeric, then XXX options will be generated
 * If mode string is 'random_sXXX' where X is numeric, then XXX will be used to seed
 * the random number generator that drives selection of mode options.
 * Combination options such as 'random_c21_s1234' can also be used.
 *
 * This code currently only generates IBM SDK options.
 */
public class RandomModesGenerator {
    private static final Logger logger = LogManager.getLogger(RandomModesGenerator.class.getName());

    private static Random rnd = null;
	private long seed;
	
	private int bitSize;

	
	/*
	 * Will return an array containing randomly generated JVM option strings.
	 * Takes an integer, which is the number of options to return.
	 */
	private ArrayList<String> getRandomOptions(int numberRequested) {
		if (numberRequested < 1) {
			return new ArrayList<String>();
		}
		
		ArrayList<String> options = new ArrayList<String>();
		options.addAll(getOptions(numberRequested));
		options.addAll(getGCOptions());
		options.add(getJITOptions());
		options.addAll(getJVMOptions());
		options.add(getCompressedReferencesOption());
		
		return options;
	}
	
	
	/*
	 * Returns some random mode data. Optionally takes a hash of arguments, which
     * may specify a seed and a number of options to be generated.
     */
	String getRandomModeData(int bitSize, long seed, int count) {
		ArrayList<String> options = getRandomOptions(count);

		// Convert into a space separated string
		StringBuilder args = new StringBuilder();
		for (String o : options) {
			if (args.length() != 0) {
				args.append(" ");
			}
			args.append(o);
		}
		
		return args.toString();
	}
	
	
	RandomModesGenerator(int bitSize, long seed, long count) {
		this.bitSize = bitSize;
		
		if (RandomModesGenerator.rnd == null) {
			// Initialise random number generator
			if (seed == -1) {
				// First time through with no explicit seed, so pick one.
				seed = System.currentTimeMillis();
				logger.info("Running with random seed. Reproduce with '-mode=random_s" + seed + "'");
			}
			RandomModesGenerator.rnd = new Random(seed);
		}

		// Pick a seed for this instance
	  	this.seed = RandomModesGenerator.rnd.nextLong();
	  	RandomModesGenerator.rnd.setSeed(this.seed);
	}

	
	private ArrayList<String> getOptions(int count) {
		String[] singleOptions = {
				"-Xcheck:jni",
				"-Xcheck:gc:all:all:suppresslocal,verbose",
				"-Xcheck:vm:debuginfo",
				"-Xthr:minimizeUserCPU",
				"-XtlhPrefetch",
				"-Xtune:virtualized",
				"-Xtrace:none",
		};
		
		ArrayList<String> options = randomlyPick(singleOptions, Math.min(count, singleOptions.length));
		return options;
	}


	// Functions for choosing a JIT option(s), eg randomGen,disableMergeStackMaps,optLevel=hot,count=0, etc
	// TODO when randomGen is selected, need to add randomSeed=x, where x is an integer I believe,
	// but need to select appropriate values.
	private String[] getJitOptions() {
		String[] jitOptions = {
				"randomGen", 
				"disableMergeStackMaps", 
				"noJitUntilMain", 
				"optLevel", 
				"count", 
				"gcOnResolve",
				"jitCodeCacheOptions",
				"jitDataCacheOptions",				  
				"jitRecompilationOptions",
				"rtResolve",
				"quickProfile", 
				"reserveAllLocks",
				"sampleInterval"
		};
		return jitOptions;
	}

	private String[] optLevels = { "noOpt", "cold", "warm", "hot", "veryHot", "scorching" };

	
	private String getJITOptions() {
		ArrayList<String> optionsCopy = new ArrayList<String>(Arrays.asList(getJitOptions()));
		int optionCount = rnd.nextInt(optionsCopy.size()) + 1;  // make sure there is always one
		StringBuilder selected = new StringBuilder();
		for (int listIndex=0; listIndex<optionCount; listIndex++) {
			int optionIndex = rnd.nextInt(optionsCopy.size());
			String newOption = optionsCopy.get(optionIndex);
			optionsCopy.remove(optionIndex);
			
			if (newOption.equals("count")) {
				newOption = newOption + "=" + rnd.nextInt(5);
			}
			// The JIT Code Cache options alter the default settings for the JIT code cache
			// The code cache is where code compiled by the JIT is stored for re-use
			// The default code cache options as of Dec 2012 are (in MB):
			//  code=2048 (1024 on 32bit Windows, 512 on 32bit Linux x86)
			//  numCodeCachesOnStartup=1
			//  codeTotal=128000 (64000 on 31/32 bit)
			if (newOption.equals("jitCodeCacheOptions")) {
				newOption = jitCodeCacheOptions();
			}
			// The JIT Data Cache options alter the default settings for the JIT data cache.
			// The data cache contains "meta data" for compiled methods. 
			// Compiled code goes in the code cache, which is separate from the data cache.
			if (newOption.equals("jitDataCacheOptions")) {
				newOption = "dataCacheMinQuanta=" + (rnd.nextInt(16) + 1);
				newOption += ",dataCacheQuantumSize=" + (rnd.nextInt(64) + 1);
				newOption += ",paintDataCacheOnFree,data=" + (rnd.nextInt(4096) + 1);
			}
			// The JIT Recompilation options alter the default settings for recompiling methods at given optimization levels
			// Generally speaking: the more recompilation the JIT is doing, the more likely it is that something goes wrong
			// The default sample thresholds as of Dec 2013 are:
			// 	veryHotSampleThreshold=480
			// 	scorchingSampleThreshold=240
			if (newOption.equals("jitRecompilationOptions")) {
				newOption = jitRecompilationOptions();
			}
			if (newOption.equals("optLevel")) {
				int optLevelIndex = rnd.nextInt(optLevels.length);
				newOption += "=" + optLevels[optLevelIndex];
			}
			if (newOption.equals("sampleInterval")) {
				newOption += "=" + rnd.nextInt(16);
			}
			if (newOption.equals("scorchingSampleThreshold")) {
				newOption += "=" + rnd.nextInt(25565);
			}
			if (newOption.equals("randomGen")) {
				newOption = "randomGen,randomSeed=" + this.seed;
			}
			
			if (selected.length() != 0) {
				selected.append(",");
			}
			selected.append(newOption);
		}

		return "-Xjit:" + selected.toString();
	}


	private String jitCodeCacheOptions() {
		// randomly picks one of three JIT code cache options
		String codeCacheOption = "";
		int option = rnd.nextInt(3) + 1;
		if (option == 1) {
			// specifies an individual code cache size of up to 10MB, and a total of 10x this for size of all code caches
			int code = rnd.nextInt(10000) + 1;
			codeCacheOption = "code=" + code + ",codeTotal=" + (code * (rnd.nextInt(10) + 1));
			
		} else if (option == 2) {
			// sets the number of code caches we start with to be between 1 and 10
			codeCacheOption = "numCodeCachesOnStartup=" + (rnd.nextInt(10) + 1);
			
		} else if (option == 3) {
			// specifies an individual code cache size of up to 500KB, and a total of 128x this for size of all code caches
			int code = rnd.nextInt(500) + 1;
			codeCacheOption = "code=" + code + ",codeTotal=" + (code * (rnd.nextInt(128) + 1));
		}
		
		return codeCacheOption;
	}


	private String jitRecompilationOptions() {
		// randomly picks one of four JIT recompilation options
		String recompilationOption = "";	
		int option = rnd.nextInt(4) + 1;
		if (option == 1) {
			recompilationOption = "enableFastHotRecompilation";
		} else if (option == 2) {
			recompilationOption = "enableFastScorchingRecompilation";
		} else if (option == 3) {
			recompilationOption = "veryHotSampleThreshold" + "=" + rnd.nextInt(25565);
		} else if (option == 4) {
			recompilationOption = "scorchingSampleThreshold" + "=" + rnd.nextInt(25565);
		}
		
		return recompilationOption;
	}


	private String[] gc32Dist = { 
			"optthruput", 
			"gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon",  // *10 
			"optavgpause"
	};
	private String[] gc64Dist = {
			"optthruput",
			"gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", "gencon", // *10
			"optavgpause",
			"balanced"
	};
	
    // Returns extra Options for GC
	private ArrayList<String> getGCOptions() {
		String[] gcOptions = {
				"-Xmaxf" + String.format("%3.2f", ((float) ((35.0 + rnd.nextInt(65))/100))),  // 0.35 -> 0.99 
				"-Xmaxt" + String.format("%3.2f", ((float) ((10.0 + rnd.nextInt(90))/100))),  // 0.10 -> 0.99
				"-Xmr" + rnd.nextInt(1024) + "K", 
				"-Xminf" + String.format("%3.2f", ((float) rnd.nextInt(30)/100.0)), // 0.00 -> 0.29
				"-Xminf" + String.format("%3.2f", ((float) rnd.nextInt(8)/100.0)),  // 0.00 -> 0.08
				"-Xmine" + rnd.nextInt(256) + "M",
				"-Xloainitial" + String.format("%3.2f", ((float) rnd.nextInt(45)/100.0)), 
				"-Xconcurrentlevel" + rnd.nextInt(16),
				"-Xconcurrentbackground" + rnd.nextInt(16),
				"-Xconcurrentslack" + rnd.nextInt(16),
				"-Xconmeter:" + randomlyPickFrom(new String[] { "soa", "loa", "dynamic" }),
				"-Xsoftrefthreshold" + rnd.nextInt(64), 
				"-Xmca" + rnd.nextInt(64) + "K", 
				"-Xmco" + rnd.nextInt(256) + "K",
				"-Xgcworkpackets" + (1 + rnd.nextInt(65535)),
				"-Xcompactgc"
		};
		ArrayList<String> optionsSelected = randomlyPickSubset(gcOptions);
		
		if (bitSize == 64) {
			optionsSelected.add("-Xgcpolicy:" + randomlyPickFrom(gc64Dist));	
		} else {
			optionsSelected.add("-Xgcpolicy:" + randomlyPickFrom(gc32Dist));	
		}
		
		return optionsSelected;
	}


	// Randomly build JVM options
	private ArrayList<String> getJVMOptions() {
		String[] jvmOptions = {
			"-Xfastresolve" + rnd.nextInt(256), 
			"-Xfuture",
			"-Xiss" + rnd.nextInt(16)+"K",
			"-Xssi" + rnd.nextInt(32)+"K",
			"-Xthr:minimizeUserCPU",
			"-Xjni:arrayCacheMax=" + rnd.nextInt(8096),
			"-Xargencoding" };
		
		return randomlyPickSubset(jvmOptions);
	}


	private String getCompressedReferencesOption() {
		// Default is for compressed references to be disabled
		String crOption = "-Xnocompressedrefs";
		
		// If we are running on a 64 bit system, we want to enable compressed references half of the time
		if (bitSize == 64) {
			int option = rnd.nextInt(2) + 1;
			if (option == 1) {
				crOption = "";
				//System.out.println("OPTIONS GENERATOR: This job is using compressed references, unless the heap size is bigger than 28gb (limit accurate for linux, and varies per platform)");
			}
		}
		
		return crOption;
	}
	
	
	// Randomly pick some elements from the supplied array
	private ArrayList<String> randomlyPickSubset(String[] options) {
		int numberOfOptions = rnd.nextInt(options.length) + 1 ;
		return randomlyPick(options, numberOfOptions);
	}

	// Pick the specified number of elements from the supplied array of Strings
	private ArrayList<String> randomlyPick(String[] options, int count) {
		ArrayList<String> localOptions = new ArrayList<String>(Arrays.asList(options));
		
		ArrayList<String> optionsSelected = new ArrayList<String>();
		// Loop for choosing which options to use
		for (int i=0; i<count; i++) {
			// Random number to select which array entry to take an option from
			int rand = rnd.nextInt(localOptions.size());
			// put the selection option into the optionsSelected array
			optionsSelected.add(localOptions.get(rand));
			// Remove the used entry
		    localOptions.remove(rand);
		}
		
		return optionsSelected;
	}


	// Pick a single value from the supplied array of Strings
	private String randomlyPickFrom(String[] strings) {
		int selectedIndex = rnd.nextInt(strings.length);
		return strings[selectedIndex];
	}


	/**
	 * @return long containing the actual seed which the random number generator has been set to.
	 */
	public long getSeed() {
		return seed;
	}
}