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

package net.adoptopenjdk.stf.environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.extensions.Stf;


/** 
 * Provides information about the version of java which is used in the execution stage.
 * 
 * It captures the output of 'java -version' and extracts the required information by 
 * parsing this output.
 * To prevent repeated executions of an external process the 'java -version' 
 * output is cached.
 */
public class JavaVersion {
    private static final Logger logger = LogManager.getLogger(JavaVersion.class.getName());

    private static HashMap<String, JavaVersion> instances = new HashMap<String, JavaVersion>();

    private String javaHome;
    private boolean isPrimaryJvm;
    
	// Cache output from running 'java -version'
	private String javaVersionOutput;
	

	private JavaVersion(boolean isPrimaryJvm, String javaHome) throws StfException {
		this.javaHome = javaHome;
		this.isPrimaryJvm = isPrimaryJvm;
		
		// Run 'java -version' to find out about the JVM used for the execution stage. 
		String javaCmd = javaHome + File.separator + "bin" + File.separator + "java"; 
        ProcessBuilder pb = new ProcessBuilder(javaCmd, "-version");

        // Run 'java -version'
        Process process;
		try {
			process = pb.start();
		} catch (IOException e) {
			throw new StfException(e);
		}

		// Wait for the process to finish
		InputStream err = process.getErrorStream();
        while (true) {
            try {
                process.waitFor();
                break;
            } catch (InterruptedException e) {
                // Ignored
            }
        }

		// Read the 'java -version' information from stderr
        byte[] buf = new byte[10000];
        try {
			int lengthOfErrorBuffer = err.read(buf);
			javaVersionOutput = new String(buf, 0, lengthOfErrorBuffer);
		} catch (IOException e) {
			throw new StfException("Failed to read result of command: " + javaCmd);
		}

 		// Eat superfluous first lines (e.g. JIT: env var TR_OPTIONS etc, or JVMJ9VM082E Unable to switch to IFA processor)
        while (javaVersionOutput.startsWith("JVMJ9VM082E") || javaVersionOutput.startsWith("JIT:")) {
 			// Line starts with error message. Remove up to and including the newline.
 			int endOfFirstLine = javaVersionOutput.indexOf('\n');
 			javaVersionOutput = javaVersionOutput.substring(endOfFirstLine+1);
 		}
        
        // Allow parsing of output from some Oracle Java 9 versions.
        // Converts 'openjdk version "9-internal"' to 'java version "9-internal"'
        if (javaVersionOutput.startsWith("openjdk version")) {
        	javaVersionOutput = javaVersionOutput.replace("openjdk", "java");
        }

        // Sanity check. Verify that the output appears correct
		if (!javaVersionOutput.startsWith("java version")) {
			throw new StfException("'Java -version' output does not start with 'java version'. Actual output was: " + javaVersionOutput);
		}
	}

	public static JavaVersion getInstance(StfEnvironmentCore environmentCore) throws StfException {
		return getInstance(true, environmentCore.getProperty(Stf.ARG_JAVAHOME_EXECUTE));
	}

	public static JavaVersion getInstance(boolean isPrimaryJvm, String javaHome) throws StfException {
		if (javaHome.isEmpty()) {
			return null;
		}

		if (!instances.containsKey(javaHome)) {
			JavaVersion newJavaVersion = new JavaVersion(isPrimaryJvm, javaHome);
			instances.put(javaHome, newJavaVersion);
		}
		
		return instances.get(javaHome);
	}
	
	public String getJavaHome() {
		return javaHome;
	}
	
	public boolean isPrimaryJvm() {
		return isPrimaryJvm;
	}


	/**
	 * @return true if the JVM used for test execution is Java version 9. Otherwise false.
	 */
	public boolean isJava9() throws StfException {
		// Sample IBM output:
		//
		//java version "1.9.0"
		//Java(TM) SE Runtime Environment (build pwa6490ea-20160704_01)
		//IBM J9 VM build 2.9, JRE 1.9.0 Windows 7 amd64-64 Compressed References 20160701
		//_309978 (JIT enabled, AOT enabled)
		//J9VM - R29_20160701_0101_B309978
		//JIT  - tr.open_20160621_119843_74d1a142.green
		//OMR   - 62dba360
		//JCL - 20160628_01 based on Oracle jdk-9+95
		
		// Sample Oracle output:
		//
		//java version "9-ea"
		//Java(TM) SE Runtime Environment (build 9-ea+122)
		//Java HotSpot(TM) 64-Bit Server VM (build 9-ea+122, mixed mode)
		
		if ( javaVersionOutput.startsWith("java version \"9" ) ) {
			return true;
		};
		if ( javaVersionOutput.startsWith("java version \"1.9" ) ) {
			return true;
		};
		return false;
	}

	/**
	 * @return true if the JVM used for test execution is Java version 8. Otherwise false.
	 */
	public boolean isJava8() throws StfException {
		// Sample IBM output:
		//
		//java version "1.8.0"
		//Java(TM) SE Runtime Environment (build pwa6480sr3-20160428_01(SR3))
		//IBM J9 VM (build 2.8, JRE 1.8.0 Windows 7 amd64-64 Compressed References 2016042
		//7_301573 (JIT enabled, AOT enabled)
		//J9VM - R28_Java8_SR3_20160427_1620_B301573
		//JIT  - tr.r14.java.green_20160329_114288
		//GC   - R28_Java8_SR3_20160427_1620_B301573_CMPRSS
		//J9CL - 20160427_301573)
		//JCL - 20160421_01 based on Oracle jdk8u91-b14

		// Sample Oracle output:
		//
		//java version "1.8.0_92"
		//Java(TM) SE Runtime Environment (build 1.8.0_92-b14)
		//Java HotSpot(TM) 64-Bit Server VM (build 25.92-b14, mixed mode)

		return javaVersionOutput.startsWith("java version \"1.8.");
	}
	
	/**
	 * @return true if the JVM used for test execution is Java version 7. Otherwise false.
	 */
	public boolean isJava7() throws StfException {
		return javaVersionOutput.startsWith("java version \"1.7.");
	}
	
	/**
	 * @return true if the JVM used for test execution is Java version 6. Otherwise false.
	 */
	public boolean isJava6() throws StfException {
		return javaVersionOutput.startsWith("java version \"1.6.");
	}
	
	/**
	 * @return true if the JVM used for test execution is Java version 10. Otherwise false.
	 */
	public boolean isJava10() throws StfException {
		return javaVersionOutput.startsWith("java version \"10");
	}
	
	/**
	 * @return true if the JVM used for test execution is Java version 11. Otherwise false.
	 */
	public boolean isJava11() throws StfException {
		return javaVersionOutput.trim().startsWith("java version \"11");
	}

	/**
	 * @param version int value of version to check if matches
	 * @return true if the JVM used for test execution matches input param version for 10+. Otherwise false.
	 */
	public boolean isJavaVersion(int version) {
		return javaVersionOutput.trim().startsWith("java version \"" + version);
	}

	/**
	 * @return the java version with the format as a single int.
	 * eg, 6, 7, 8 or 9, 10, 11 etc
	 * @return int containing the java version number.
	 * @throws StfException if an unknown JVM release has been found.
	 */
	public int getJavaVersion() throws StfException {
		if (isJava6()) {
			return 6;
		}
		if (isJava7()) {
			return 7;
		} 
		 if (isJava8()) {
			return 8;
		} 
		if (isJava9()) {
			return 9;
		} 
		// from 10 and up, format can be checked with version number directly
		// format will be "java version XX"
		int lowver = 10;
		int highver = 99;
		for (int version=lowver; version < highver; version++) {
			if (isJavaVersion(version)) return version;
		}	
		throw new StfException("Unknown JVM release: " + PlatformFinder.getPlatformAsString());
	}
	
	// Return jvm version as 60, 70, 80 or 90, 100 etc
	public String getJavaVersionCode() throws StfException {
		return getJavaVersion() + "0";
	}
	
	public boolean isIBMJvm() throws StfException {
		return (javaVersionOutput.contains("IBM") | javaVersionOutput.contains("OpenJ9"));
	}

	public boolean isOracleJvm() throws StfException {
		return javaVersionOutput.contains("HotSpot");
	}

	public String getRawOutput() {
		return javaVersionOutput;
	}
	
	/**
	 * Allows a test to only run if the execution stage is using an IBM JVM.
	 * If an IBM JVM is not used then the test run will fail with a StfError.
	 * This method should not be used for tests which will run on an Oracle JVM, as
	 * it's sometimes useful to retain the flexibility of be able to run on either.
	 * @throws StfException for non-IBM JVM or if execution of 'java -version' failed. 
	 */
	public void verifyUsingIBMJava() throws StfException {
		if (!isIBMJvm()) {
			String narrative = isPrimaryJvm ? "" : " for the secondary JVM";
			logger.info("'java -version' output:\n" + getRawOutput());
			throw new StfError("Aborting run. This test requires an IBM JVM" + narrative + ".");
		}
	}
	
	/**
	 * Allows a test to only run if the execution stage is using an Oracle JVM.
	 * If an IBM is not then the test run will fail with a StfError.
	 * @throws StfException for non-Oracle JVM or if execution of 'java -version' failed. 
	 */
	public void verifyUsingOracleJava() throws StfException {
		if (!isOracleJvm()) {
			String narrative = isPrimaryJvm ? "" : " for the secondary JVM";
			logger.info("'java -version' output:\n" + getRawOutput());
			throw new StfError("Aborting run. This test requires an Oracle JVM" + narrative + ".");
		}
	}
}