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

import java.io.File;


/**
 * Simple object which holds information about some java-args and their originating file.
 */
public class JvmArgDetails {
	private File originatingFile;
	private String javaArgs;
	
	public JvmArgDetails(File originatingFile, String javaArgs) {
		this.originatingFile = originatingFile;
		this.javaArgs = javaArgs.trim();
	}

	public File getOriginatingFile() {
		return originatingFile;
	}

	public String getJavaArgs() {
		return javaArgs;
	}
}