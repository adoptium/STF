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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;


/**
 * This class contains code which sets the StfClassLoaders search path.
 * 
 * This is required because STF is started with a classpath that is only 
 * sufficient for running the STF classes, but it then has to be able to
 * run any testcase within the workspace. Hence the need to dynamically 
 * extend the class path to cover a specific project.
 */
public class ClassPathConfigurator {
    private static final Logger logger = LogManager.getLogger(ClassPathConfigurator.class.getName());

	/**
	 * This method tells the STF class loader to search for classes and jar files
	 * based on the configuration for the specified project.
	 * 
	 * This code parses the .classpath file to work out which directories and 
	 * jar files are on the classpath.
	 * 
	 * @param environmentCore gives access to stf properties, workspace-root and test-root.
	 * @param projectName is the name of the project containing the test plugin.
	 * @return boolean set to true if the project uses the STF project or jar file.
	 * @throws StfException
	 */
	public static boolean configureClassLoader(StfEnvironmentCore environmentCore, String projectName) throws StfException {
		// Find the projects dependencies of the project by parsing its .classpath file
		ArrayList<String> projectRoots = new ArrayList<String>();
		HashSet<String> jarsUsed = new LinkedHashSet<String>();
		HashSet<String> processedProjects = new LinkedHashSet<String>();  // To prevent indirect recursion
		findProjectDependencies(environmentCore, projectName, projectRoots, jarsUsed, processedProjects);


		System.out.println("Classpath directories used by project '" + projectName + "': ");
		for (String root : projectRoots) {
			System.out.println("  " + root);
		}
		for (String jarName : jarsUsed) {
			System.out.println("  " + jarName);
		}	

		// Log the discovered dependencies
		if (logger.isDebugEnabled()) {
			logger.debug("Classpath directories used by project '" + projectName + "': ");
			for (String root : projectRoots) {
				logger.debug("  " + root);
			}
			logger.debug("Jar files used by project '" + projectName + "': ");
			for (String jarName : jarsUsed) {
				logger.debug("  " + jarName);
			}	
		}

		// Configure the class loader for the plugins project dependencies
		StfClassLoader.setClassRoots(projectRoots);
		StfClassLoader.setJarPath(jarsUsed);

		// Work out if the current project is using STF
		boolean usesStf = false;
		for (String root : projectRoots) {
			if (new File(root).getParentFile().getName().equals(StfConstants.STF_PROJECT_NAME)) {
				usesStf = true;
			}
		}
		for (String jarName : jarsUsed) {
			String jarFileName = new File(jarName).getName();
			if (jarFileName.startsWith(StfConstants.STF_JAR_PREFIX) && jarFileName.endsWith(".jar")) {
				usesStf = true;
			}
		}	
		
		return usesStf;
	}
	
	
	/** 
	 * This method parses the .classpath file for a project to extract:
	 *   1) directories to include on the classpath and
	 *   2) jar files used by the project.
	 *   
	 * It recursively traverses dependent projects to make sure that a full 
	 * classpath can be built.
	 * 
	 * @param environmentCore gives access to stf properties.
	 * @param testRoot points to the directory containing test classes.
	 * @param projectName is the name of the project to examine.
	 * @param projectRoots contains all discovered classpath directories.
	 * @param jarsUsed contains all discovered jar files.
	 * @param processedProjects prevents infinite recursion by tracking which projects have
	 * already been processed.
	 * @throws StfException
	 */
	private static void findProjectDependencies(StfEnvironmentCore environmentCore, String projectName, 
						ArrayList<String> projectRoots, HashSet<String> jarsUsed, 
						HashSet<String> processedProjects) throws StfException {
		// Abandon this project if already done. Need to prevent infinite recursion.
		if (processedProjects.contains(projectName)) {
			return;
		}
		logger.trace("Examining dependencies for '" + projectName + "'");
		processedProjects.add(projectName);
		
		// Set 'projectRef' to the location of the project on disk.
		// Firstly see if the project lives at '$test-root/<project>'
		DirectoryRef projectRef = environmentCore.findTestDirectory(projectName);
	
		// Work out where to read classpath configuration from.
		// First choice is the stfclasspath.xml file, otherwise eclipse .classpath file.
		FileRef stfClasspathFile = projectRef.childFile(StfConstants.STF_CLASSPATH_XML_FILE);
		FileRef classpathFile = projectRef.childFile(".classpath");
		FileRef projectConfig = null;
		if (stfClasspathFile.asJavaFile().exists()) {
			logger.debug(projectName + "/" + StfConstants.STF_CLASSPATH_XML_FILE + " file takes precedence over the projects .classpath");
			projectConfig = stfClasspathFile;  
		} else {
			// Normal case. Find dependencies from '<project>/.classpath' file.
			projectConfig = classpathFile;
		}
		
		try {
			// Open the projects .classpath xml file
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(projectConfig.asJavaFile());
			

			// Find any optional j9output entries.  for projects. ie. classpathentry of kind 'src' and whose path starts with a '/'.
			// Only used in stfclasspath.xml to add extra roots for class files. Added for Java 9 modularity support.
			XPath xpathSrc = XPathFactory.newInstance().newXPath();
			XPathExpression exprSrc = xpathSrc.compile("/classpath/classpathentry[@kind=\"j9output\"]/@path");
			
			// Run the xpath query to extract the src entries
			NodeList nl = (NodeList) exprSrc.evaluate(doc, XPathConstants.NODESET);
			for (int i=0; i<nl.getLength(); i++) {
				Node node = nl.item(i);
				String srcValue = node.getNodeValue();
				DirectoryRef srcBin = projectRef.childDirectory(srcValue);
				projectRoots.add(srcBin.asJavaFile().getAbsolutePath());
			}

			DirectoryRef binDirectory = projectRef.childDirectory("bin");
			projectRoots.add(binDirectory.getSpec());
			
			// Find entries for projects. ie. classpathentry of kind 'src' and whose path starts with a '/'.
			XPath xpath = XPathFactory.newInstance().newXPath();
			XPathExpression expr = xpath.compile("/classpath/classpathentry[@kind=\"src\" and starts-with(@path, '/')]/@path");
			
			// Run the xpath query to extract the referenced projects
			nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			for (int i=0; i<nl.getLength(); i++) {
				Node node = nl.item(i);
				String otherProjectName = node.getNodeValue().substring(1);
				findProjectDependencies(environmentCore, otherProjectName, projectRoots, jarsUsed, processedProjects);
			}
		

			// Build a query to find the jars used by the project
			XPath xpathJars = XPathFactory.newInstance().newXPath();
			XPathExpression exprJars = xpathJars.compile("/classpath/classpathentry[@kind=\"lib\"]/@path");
			
			// Run the xpath query to extract the jar locations
			nl = (NodeList) exprJars.evaluate(doc, XPathConstants.NODESET);
			for (int i=0; i<nl.getLength(); i++) {
				Node node = nl.item(i);
				String jarName = node.getNodeValue();
				logger.trace("  " + projectName + ": jarName: " + jarName);
				
				// Some classpath entries will refer to the systemtest-prereqs directory. This
				// is fine for eclipse time compiles, as it knows where the prereqs jars
				// really are.
				// But it's not fine for STF. It decides that the workspace root is the
				// parent directory of 'stf.core/scripts/stf.pl'. This takes it to the root of
				// the project that contains the stf code but there is then no sign of 
				// jar references such as say '/systemtest_prereqs/log4j-2.3/log4j-api-2.3.jar'.
				// In such cases the full path to the jar is created found by replacing the first
				// part with the relevant systemtest-prereqs root.  
				if (jarName.startsWith("/systemtest_prereqs")) {
					// Jar needs to be found below a systemtest-prereqs root
					String tempJarPath = jarName.replace("/systemtest_prereqs", "");
					String fullJarPath = environmentCore.findPrereqFile(tempJarPath).getSpec();
					
					jarsUsed.add(fullJarPath);
					if (!new File(fullJarPath).exists()) {
						throw new StfException("Failed to parse '.classpath' file for project '" + projectName + "'. "
								+ "The jar file for entry '" + jarName + "' does not exist in any of the systemtest-prereqs roots at its expected location: '<systemtest_prereqs>" + tempJarPath + "'");
					}
					logger.trace("  " + projectName + ": Found prereq jar from 'lib' entry: '" + fullJarPath);
				} else {
					// Usual case. Find the jar within the eclipse workspace
					FileRef fullJarPath = environmentCore.findTestFile(jarName);
					jarsUsed.add(fullJarPath.getSpec());
					if (!fullJarPath.asJavaFile().exists()) {
						throw new StfException("Failed to parse '.classpath' file for project '" + projectName + "'. "
								+ "The jar file for entry '" + jarName + "' does not exist in the eclipse workspace at '" + fullJarPath + "'");
					}
					logger.trace("  " + projectName + ": Found jar from 'lib' entry: '" + fullJarPath);
				}
			}
		} catch (XPathExpressionException e) {
			throw new StfException("Failed to read from classpath file: " + classpathFile.asJavaFile().getAbsolutePath(), e);
		} catch (ParserConfigurationException e) {
			throw new StfException("Failed to read from classpath file: " + classpathFile.asJavaFile().getAbsolutePath(), e);
		} catch (IOException e) {
			throw new StfException("Failed to read from classpath file: " + classpathFile.asJavaFile().getAbsolutePath(), e);
		} catch (SAXException e) {
			throw new StfException("Failed to read from classpath file: " + classpathFile.asJavaFile().getAbsolutePath(), e);
		}
	}
}
