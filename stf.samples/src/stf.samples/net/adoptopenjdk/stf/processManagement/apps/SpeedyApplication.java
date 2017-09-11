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

import java.util.Random;

/**
 * This is a short running 'application', which is used by
 * the Sample programs to demonstrate multi-process control.
 */
public class SpeedyApplication {
	public static void main(String[] args) {
		System.out.println("SpeedyApplication started");
		
		try {
			int sleepTime = new Random().nextInt(100);
			System.out.println("SpeedyApplication sleeping for " + sleepTime + "ms");
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			System.out.println("SpeedyApplication interrupted");
			e.printStackTrace();
		}

		System.out.println("SpeedyApplication completed normally");
	}
}
