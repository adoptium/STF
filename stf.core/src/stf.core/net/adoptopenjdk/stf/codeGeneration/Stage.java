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

package net.adoptopenjdk.stf.codeGeneration;

import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.extensions.Stf;

/** 
 * This class represents the different stages of code generation.
 * 
 * Extensions and test plugins sometimes need to know which stage they are 
 * working on so that the can vary the code being produced. 
 * For example, only the execution stage should be running with full JVM arguments.
 */
public enum Stage { 
	INITIALISATION("pluginInit", null),
	SETUP         ("setUp",      Stf.ARG_JAVA_ARGS_SETUP),
	EXECUTE       ("execute",    Stf.ARG_JAVA_ARGS_EXECUTE, Stf.ARG_JAVA_ARGS_EXECUTE_SECONDARY),
	TEARDOWN      ("tearDown",   Stf.ARG_JAVA_ARGS_TEARDOWN);
	
	private String methodName;
	private Argument primaryJavaArg; 
	private Argument secondaryJavaArg; 
	
	private Stage(String methodName, Argument javaArg) {
		this.methodName = methodName;
		this.primaryJavaArg   = javaArg;
		this.secondaryJavaArg = javaArg;
	}

	private Stage(String methodName, Argument primaryJavaArg, Argument secondaryJavaArg) {
		this.methodName = methodName;
		this.primaryJavaArg   = primaryJavaArg;
		this.secondaryJavaArg = secondaryJavaArg;
	}
		
	public String getMethodName() {
		return methodName;
	}
	
	/**
	 * @return The argument which will contain Java arguments for the stage.
	 */
	public Argument getJavaArg(JavaVersion javaVersion) {
		if (javaVersion.isPrimaryJvm()) {
			return primaryJavaArg;
		} else {
			return secondaryJavaArg;
		}
	}
};