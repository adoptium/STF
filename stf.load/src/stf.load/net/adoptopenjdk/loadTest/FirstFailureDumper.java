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

package net.adoptopenjdk.loadTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.loadTest.adaptors.LoadTestBase;
import net.adoptopenjdk.stf.StfException;


/**
 * This class creates a set of java dumps when running on an OpenJ9 based JVM.
 * It has no effect when running on a hotspot based JVM.
 * 
 * The 'createDumps()' method should only be called when load test has had its first failure.
 */
public class FirstFailureDumper {
	private static final Logger logger = LogManager.getLogger(FirstFailureDumper.class.getName());

	private static FirstFailureDumper instance = null;
	
	private static final String HEAPDUMP_FILE_NAME   = "firstfailure.heapdump.%Y%m%d.%H%M%S.%pid.%seq.phd";
	private static final String JAVADUMP_FILE_NAME   = "firstfailure.javacore.%Y%m%d.%H%M%S.%pid.%seq.txt";
    private static final String SYSTEMDUMP_FILE_NAME = "firstfailure.core.%Y%m%d.%H%M%S.%pid.%seq.dmp";

    // Dump methods accessed via reflection, so that this code can be compiled and 
    // executed on an Oracle JVM.
    private final boolean isJ9;
    private final Method heapDumpMethod;
    private final Method javaDumpMethod;
    private final Method systemDumpMethod;
    
    private boolean haveCreatedFirstFailureDumps = false;
    
    
    /**
     * Constructor uses reflection to get hold of method references to the dump methods.
     * @throws StfException if there was a reflection related exception.
     */
    private FirstFailureDumper() throws StfException {
    	String jvmVendor = System.getProperty("java.vm.vendor");
    	if (jvmVendor != null && ( jvmVendor.contains("IBM") || jvmVendor.contains("OpenJ9") ) ) {
    		this.isJ9 = true;
			try {
				// Get a reference to the IBM only dump methods.
				Class<?> dumpClass = Class.forName("com.ibm.jvm.Dump");
				this.heapDumpMethod = dumpClass.getDeclaredMethod("heapDumpToFile", String.class);
				this.javaDumpMethod = dumpClass.getDeclaredMethod("javaDumpToFile", String.class);
				this.systemDumpMethod = dumpClass.getDeclaredMethod("systemDumpToFile", String.class);
				
			} catch (ClassNotFoundException e) {
				throw new StfException("Failed to access IBM Dump class", e);
			} catch (NoSuchMethodException e) {
				throw new StfException("Failed to access IBM Dump method", e);
			} catch (SecurityException e) {
				throw new StfException("Failed to access IBM Dump class", e);
			}

    	} else {
        	this.isJ9 = false;
    		this.heapDumpMethod = null;
    		this.javaDumpMethod = null;
    		this.systemDumpMethod = null;
    	}
    }
	
    
    /**
     * Creates a first failure dumper object. Must be called before usage
     */
    public synchronized static void createInstance() throws StfException {
    	if (FirstFailureDumper.instance == null) {
    		FirstFailureDumper.instance = new FirstFailureDumper();
    	}
    }

    
    /**
     * Get hold of an instance of the FirstFailureDumper.
     */
    public synchronized static FirstFailureDumper instance() {
    	if (FirstFailureDumper.instance == null) {
    		throw new IllegalStateException("FirstFailureDumper does not exist. Must be created before use");
    	}
    	
    	return FirstFailureDumper.instance;
    }

    
    /**
     * This methods creates a set of heap, java and system dumps.
     * It should be called when load test has its first failure.
     * @throws StfException if a dump method could not be called.
     */
    public synchronized void createDumpIfFirstFailure(LoadTestBase test) {
        if (haveCreatedFirstFailureDumps) { 
        	return;
        }
		
        if (isJ9) {
			logger.info("First failure detected by thread: " + Thread.currentThread().getName() + ". Running test: " + test.toString() + ". Creating java dumps.");
			createDump("heap", heapDumpMethod, HEAPDUMP_FILE_NAME);
			createDump("java", javaDumpMethod, JAVADUMP_FILE_NAME);
			createDump("system", systemDumpMethod, SYSTEMDUMP_FILE_NAME);
			
		} else {
			logger.info("First failure detected by thread: " + Thread.currentThread().getName() + ". Not creating dumps as not running on an IBM JVM");
		}
		
		haveCreatedFirstFailureDumps = true;
	}
	

    // Create a dump. If there is an exception during dump creation then 
    // it is logged but not propagated, as the test has already failed.
	private void createDump(String dumpType, Method dumpMethod, String dumpFileName) {
		try {
			dumpMethod.invoke(null, dumpFileName);
		} catch (IllegalAccessException e) {
			logger.error("Failed to create a " + dumpType + "Dump", e);
		} catch (IllegalArgumentException e) {
			logger.error("Failed to create a " + dumpType + "Dump", e);
		} catch (InvocationTargetException e) {
			logger.error("Failed to create a " + dumpType + "Dump", e);
		}
	}	
}