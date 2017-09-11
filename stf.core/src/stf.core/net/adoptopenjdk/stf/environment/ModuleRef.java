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

import net.adoptopenjdk.stf.StfException;


/**
 * This class represents a Module.
 */
public class ModuleRef {
	private String moduleName;
	private FileRef jarFileRef;
	
	public ModuleRef(String moduleName, FileRef jarFileRef) throws StfException {
		this.moduleName = moduleName;
		this.jarFileRef = jarFileRef;
	}


	/**
	 * Return the name of this module.
	 */
	public String getName() {
		return moduleName; 
	}
	

	/**
	 * Return the base name of this module.
	 * This is the module name but without any '.jmod' file extension
	 * eg, if a 'com.hello.jmod' has been created this method would return 'com.hello'
	 */
	public String getBaseName() {
		if (moduleName.endsWith(".jmod")) {
			int extensionStart = moduleName.lastIndexOf(".jmod");
			return moduleName.substring(0, extensionStart);
		}
		
		return moduleName; 
	}
	

	/** 
	 * @return a FileRef for the jar file used by this module.
	 */
	public FileRef getJarFileRef() {
		return jarFileRef;
	}
	
	
	public String toString() { 
		return moduleName + "->" + jarFileRef.getName();
	}
}