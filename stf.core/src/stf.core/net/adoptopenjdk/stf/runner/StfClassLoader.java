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

package net.adoptopenjdk.stf.runner;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfException;


/**
 * This class loads the classes used by a STF plugin.
 * STF needs its own classloader because STF is started with the name of the plugin 
 * to run, and not the full classpath needed for that plugin. 
 * 
 * It may seem more attractive to start STF with a suitable classpath and avoid the 
 * complexities of having a custom class loader, but the 2 obvious solutions both 
 * have significant problems: 
 *   1) Starting with test specific classpath. 
 *      For this to work STF would need to be started with the full classpath, which 
 *      would either have to be supplied by the tester or read from some table
 *      containing the classpath needed for every test.
 *      It would obviously work but looses the current simplicity of just adding a 
 *      new test and running with just a test name. Also introduces maintenance headaches. 
 *   2) Start with every-in-the-workspace classpath.
 *      This is how the STF prototype used to work but has 2 problems. Firstly this 
 *      classpath will become huge as projects are added, and secondly those projects 
 *      will use external jar files which also have to be in the classpath (a problem 
 *      which the prototype did not address.
 */
public class StfClassLoader extends ClassLoader {
    private static Logger logger = null;

    // This holds a list of the projects from which classes are to be loaded.
	private String[] projectRoots = null;
	
	// This class loader is used to read classes from jar files used by the plugin.
	private ClassLoader jarClassLoader = null;
	
	// This maps from a full class name to the project it was loaded from.
	// This is needed so that the default properties for an extension can be found. 
	private HashMap<String, String> loadedClassToProjectMap = new HashMap<String, String>();
	
	
	public StfClassLoader(ClassLoader parent) {
		super(parent);
		
		// In order to get this class loader to load StfRunner we need to find out where 
		// this class lives on the file system, so that classes which live in the same
		// project (ie. the STF project) can be loaded.
		// Once StfRunner is running it will identify the test plugin to run, and from this it can  
		// find the projects and jars which that plugin needs to use. Once this has been done the  
		// root directories will be updated.
		// NB. This step is important. Without it the system class loader will load StfRunner
		// and the same class loader, and not this one, will be used to load everything else.
    	String currentProjectLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
    	projectRoots = new String[] { currentProjectLocation };
	}

	
    public StfClassLoader() {
        super(StfClassLoader.class.getClassLoader());
    }  

    
    /**
     * This method is called to tell the class loader about project directories which classes 
     * can be loaded from.
     * @param projectRoots is an array containing the file names of project directories.
     */
    public void internalSetClassRoots(String... projectRoots) {
    	this.projectRoots = projectRoots;
    }

    
    public String internalGetProjectName(String className) {
    	return loadedClassToProjectMap.get(className);
    }

    
    /**
     * This method is called to tell the class loader about jar files which the this class 
     * loader also needs to load classes from.
     * @param jarList is a array containing the full path to jar files.
     * @throws StfException
     */
    public void internalSetJarPath(String... jarList) throws StfException {
    	ArrayList<URL> urls = new ArrayList<URL>();
    	
    	// Convert the jar file names to URL objects
    	for (String jarFileName : jarList) {
    		try {
    			File jarFile  = new File(jarFileName);
    			urls.add(jarFile.toURI().toURL());
    		} catch (MalformedURLException e) {
    			throw new StfException("Failed to get URL for jar file: " + jarFileName);
    		}  
    	}
    	
	    jarClassLoader = new URLClassLoader(urls.toArray(new URL[] {}));
    }
    

    /**
     * This is the main method for class loading.
     * It tries to load the class from several known sources
     */
    public Class<?> loadClass(String className) throws ClassNotFoundException {
    	// Shortcut for java classes. Always use the system class loader
    	if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.") || className.startsWith("com.sun.")) {
    		return super.loadClass(className);
    	}
    	
    	// Work out the real name of the class
    	className = correctClassName(className);

    	// Try to load from the projects used by the plugin
        Class<?> clazz = findClass(className);
	    if (clazz != null) { 
	    	return clazz;
	    }
        
	    // Try to get the class from a jar file
        if (jarClassLoader != null) {
            return jarClassLoader.loadClass(className);
        }
        
        // As the last resort, use the system class loader
        return super.loadClass(className);
    }  


    // The search for classes recursively looks at all classes below the projects bin file.
    // This works fine for Java 8 and earlier but breaks down with the structure currently used 
    // for Java 9 modularity projects, which effectively have multiple project roots within
    // the bin hierarchy.
    // This method takes the presumed class name for a class found below the bin directory and
    // works out what its class name really is.
    //
    // Here is a simplified example. A project could have 2 bin roots defined:
    //    /tmp/runtimestest_build/ascii/test.modularity/bin/common/displayServiceImpl4
    //    /tmp/runtimestest_build/ascii/test.modularity/bin
    // The search in the projects bin directory finds the file:
    //    common/displayServiceImpl4/net/adoptopenjdk/displayImpl4/DisplayServiceImpl4.class
    // Which is converted to the class name:
    //    common.displayServiceImpl4.net.adoptopenjdk.displayImpl4.DisplayServiceImpl4
    // A classfile is *not* found at:
    //    .../test.modularity/bin/common/displayServiceImpl4/common/displayServiceImpl4/net/adoptopenjdk/displayImpl4/DisplayServiceImpl4.class
    // But notice that the end of the project root and the start of the proposed class 
    // name are the same, so removing this part of the class name does find an actual 
    // class file at:
    //    .../test.modularity/bin/common/displayServiceImpl4/net/adoptopenjdk/displayImpl4/DisplayServiceImpl4.class
    // So in this case the 'common/displayServiceImpl4/' is removed and the class name is corrected to:
    //    net/adoptopenjdk/displayImpl4/DisplayServiceImpl4
    //
    // This complex logic is needed because the trees created in the bin directory by 
    // a projects modules and normal source code unfortunately overlap. 
    private String correctClassName(String className) {
        String slashedClassName = className.replace('.', File.separatorChar);
        
        // Examine every root to see if the class belongs to it
    	for (String currentRoot : projectRoots) {
    		// Remove any trailing slash from the project root
    		if (currentRoot.endsWith(File.separator)) {
    			currentRoot = currentRoot.substring(0, currentRoot.length()-1);
    		}
    		
    		// Abandon search if file exists within the current project root
			String fileName = currentRoot + File.separatorChar + slashedClassName + ".class";
	        File file = new File(fileName);
	        if (file.exists()) {
	        	break;
	        }

	        // Examine the end of the currentRoot string to see if it overlaps the start of the class name.
	        // Works back through every '/' from the end to the start of current root.
	        int lastSlashIndex = currentRoot.lastIndexOf(File.separatorChar);
        	boolean foundOverlap = false;
        	// Keep going whilst the last part of current root can be found in the slashedClassName
        	while (lastSlashIndex >=0 && slashedClassName.contains(currentRoot.substring(lastSlashIndex+1))) {
        		if (slashedClassName.startsWith(currentRoot.substring(lastSlashIndex+1))) {
        			// Full overlap found. 
        			foundOverlap = true;
        			break;
        		}
        		lastSlashIndex = currentRoot.lastIndexOf(File.separatorChar, lastSlashIndex-1);
        	}
        	if (!foundOverlap) {
        		// The class doesn't live within the current project root
        		continue;
        	}

        	// Found an overlap between end of current root and the start of the class name.
        	// We know how long the overlap is, so remove that many characters from the start of the class name.
        	int overlapLen = currentRoot.length() - lastSlashIndex;
        	String actualClassName = className.substring(overlapLen);
        	fileName = currentRoot + File.separatorChar + actualClassName.replace('.', File.separatorChar) + ".class";
        	file = new File(fileName);
        	if (file.exists()) {
        		// Class exists! Return corrected name
        		return actualClassName;
        	}        	
        }
        
    	// No change
        return className;         
    }
    
    
    public Class<?> findClass(String name) throws ClassNotFoundException{
		// If the class has already been loaded then return the existing class object.
    	// Very rare code path, but can happen when running on an Oracle JVM.
    	Class<?> cachedClass = findLoadedClass(name);
    	if (cachedClass != null) {
    		logger.trace("Class already loaded: " + name);
    		return cachedClass; 
    	}

    	byte[] classBytes = loadClassBytes(name);
        if (classBytes != null) {
        	return defineClass(name, classBytes, 0, classBytes.length);
        } else {
        	return null;
        }
    }
    
    
    /**
     * Attempt to load a class from the root bin directory of all know projects.
     * It is this method which allows STF to load the classes from plugins project.
     * 
     * @param className is the full name of the class to load.
     * @return the bytes of the class, or null if it cannot be found in the known projects.
     * @throws ClassNotFoundException if there was an IO error.
     */
    public byte[] loadClassBytes(String className) throws ClassNotFoundException {
    	// Attempt to load the class from all projects.
    	// This is not optimal but appears adequate as it's not loading many classes.
    	for (String currentRoot : projectRoots) {
    		BufferedInputStream inFile = null;
	        try{
	            String fileName = currentRoot + File.separatorChar + className.replace('.', File.separatorChar) + ".class";
	            File file = new File(fileName);
	            if (!file.exists()) continue;
	            
	            // Record which project the class has been loaded from
	            loadedClassToProjectMap.put(className, currentRoot);

	            // Read in the contents of the class file
				inFile = new BufferedInputStream(new FileInputStream(file));
				ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
				int b;
				while ((b = inFile.read()) != -1) {
					out.write(b);
				}
				
				// Optional logging
				if (logger == null) {
					// find the logger at run time. If done during class initialisation then you get a recursive class loading error.
					logger = LogManager.getLogger(StfClassLoader.class.getName());
				}
				logger.trace("Loaded class: " + currentRoot + " " + className);
				
				return out.toByteArray();
	        }
	        catch (java.io.IOException e) {
	        	throw new ClassNotFoundException("Failed to load class '" + className + "' below '" + currentRoot + "'", e);
	        } finally {
	        	try {
					if (inFile != null) inFile.close();
				} catch (IOException e) {
					throw new ClassNotFoundException("Failed to close file for class '" + className + "' below '" + currentRoot + "'", e);
				}
	        }
    	}
    	
    	return null;
    }
    
    
    @Override
    protected URL findResource(String resName) {
    	URL resource = super.findResource(resName);
    	if (resource != null) {
    		return resource;
    	}

    	// Attempt to find the resource in one of the projects
    	for (String currentRoot : projectRoots) {
            String candidateFileName = currentRoot + File.separatorChar + resName;
            if (new File(candidateFileName).exists()) {
				try {
					return new URL("file://" + candidateFileName);
				} catch (MalformedURLException e) {
					throw new IllegalStateException("Failed to create URL for file at: " + candidateFileName);
				}
            }
    	}

    	// Didn't find. Let parent class loader decide what to do
		return null;
    }
    

    //=================================================================================================
    // The following methods act as an interface to the STF class loader.
    //
    // NB. The class loader has to be called using reflection as calling it directly by
    // getting its reference and casting it doesn't work (as the calling class and the 
    // StfClassLoader have been loaded by different class loaders).
    // 
    // So the following static methods call the STF class loader by using reflection.
    //
    
    /**
     * This method is used to find out which project a loaded class belongs to.
     *  
     * @param className is the class name in the query.
     * @return a String containing the project name which contains the class. 
     * @throws StfException if the reflection based call failed.
     */
    public static String getProjectName(String className) throws StfException {
		String methodName = "internalGetProjectName";
		try {
			ClassLoader cl = StfClassLoader.class.getClassLoader();
			Method method = cl.getClass().getMethod(methodName, new Class[] { String.class });
			Object result = method.invoke(cl, new Object[] { className } );
			if (result == null) { 
				throw new StfException("Failed to find project owning class: " + className);
			}
			return (String) result;
		} catch (Exception e) {
			throw new StfException("Failed to invoke method: " + methodName, e); 
		}
    }
    
	// Tell the class loader which project directories we can load classes from.
    public static void setClassRoots(ArrayList<String> projectRoots) throws StfException {
		String methodName = "internalSetClassRoots";
		try {
			ClassLoader stfClassLoader = StfClassLoader.class.getClassLoader();
			Method method = stfClassLoader.getClass().getMethod(methodName, new Class[] { String[].class });
			String[] projectRootsAsArray = projectRoots.toArray(new String[] {});
			method.invoke(stfClassLoader, new Object[]{ projectRootsAsArray });
		} catch (Exception e) {
			throw new StfException("Failed to invoke method: " + methodName, e); 
		}
    }
    
	// Tell the stf class loader which jar files the plugin needs to use.
    public static void setJarPath(HashSet<String> jarsUsed) throws StfException {
    	String methodName = "internalSetJarPath";
		try {
			ClassLoader stfClassLoader = StfClassLoader.class.getClassLoader();
			Method method = stfClassLoader.getClass().getMethod(methodName, new Class[] { String[].class });
			String[] jarsUsedAsArray = jarsUsed.toArray(new String[] {});
			method.invoke(stfClassLoader, new Object[]{ jarsUsedAsArray });
		} catch (Exception e) {
			throw new StfException("Failed to invoke method: " + methodName, e); 
		}
    }
}
