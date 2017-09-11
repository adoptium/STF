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

package net.adoptopenjdk.stf.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.adoptopenjdk.stf.StfException;


public class FileOperations {
	/**
	 * Copy a file. 
	 */
	public static void copyFile(File sourceFile, File destFile) throws StfException {
		try {
			// Create destination directory if it doesn't exist
			File parentDir = destFile.getParentFile();
			if (!parentDir.exists()) {
				parentDir.mkdirs();
			}
			
		    InputStream in = null;
		    OutputStream out = null;
		    try {
		        in = new FileInputStream(sourceFile);
		        out = new FileOutputStream(destFile);
		        byte[] buffer = new byte[1024];
		        int length;
		        while ((length = in.read(buffer)) > 0) {
		            out.write(buffer, 0, length);
		        }
		    } finally {
		        if (in != null) in.close();
		        if (out != null) out.close();
		    }
		} catch (IOException e) {
			throw new StfException("Failed to copy file. Source:" + sourceFile.getAbsolutePath() + " Dest:" + destFile.getAbsolutePath(), e);
		}
	}
}
