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

package net.adoptopenjdk.stf.processManagement.apps;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This mini program is used by the STF samples. 
 * This program simulates a client application.
 */
public class MiniClient {
	private static SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");
	
	public static void main(String[] args) {
		log("Client started");
		if (args.length > 0) {
			// Exit the client using the supplied exit value
			int exitValue = Integer.parseInt(args[0]);
			log("Client exiting with value of " + exitValue);
			System.exit(exitValue);
		}
		
		try {
			Thread.sleep(2500);
		} catch (InterruptedException e) {
			log("Client interrupted");
			e.printStackTrace();
		}

		log("Client completed normally");
	}

	private static void log(String message) {
		System.out.println(dateFormatter.format(new Date()) + " " + message);
	}
}
