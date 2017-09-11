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

package net.adoptopenjdk.loadTest.reporting;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;


/**
 * Test case to verify logic for load test log rotation is deleting the correct files.
 * 
 * This is in a test case as it's pretty tricky to verify that the load test run 
 * is leaving behind the most significant log files.
 * 
 * Notation to represent each log file is:
 *   '-' is a retained log file
 *   ' ' is a deleted log file
 *   'X' is a retained log file which contains a failing test
 */
public class TestExecutionLogManager {
	private int nextLogFileNumber = 0; 
	private ExecutionLogManager logManager = new ExecutionLogManager(25);
	
	
	@Test
	public void testAllClean() {
		System.out.println("  All clean");
		addPassingLog("[-]");
		addPassingLog("[--]");
		addPassingLog("[---]");
		addPassingLog("[----]");
		addPassingLog("[-----]");
		addPassingLog("[------]");
		addPassingLog("[-------]");
		addPassingLog("[--------]");
		addPassingLog("[---------]");
		addPassingLog("[----------]");
		addPassingLog("[-----------]");
		addPassingLog("[------------]");
		addPassingLog("[-------------]");
		addPassingLog("[--------------]");
		addPassingLog("[---------------]");
		addPassingLog("[----------------]");
		addPassingLog("[-----------------]");
		addPassingLog("[------------------]");
		addPassingLog("[-------------------]");
		addPassingLog("[--------------------]");
		addPassingLog("[---------------------]");
		addPassingLog("[----------------------]");
		addPassingLog("[-----------------------]");
		addPassingLog("[------------------------]");
		addPassingLog("[-------------------------]");
		addPassingLog("[------------------------ -]");
		addPassingLog("[------------------------  -]");
		addPassingLog("[------------------------   -]");
		addPassingLog("[------------------------    -]");
		addPassingLog("[------------------------     -]");
		addPassingLog("[------------------------      -]");
		addPassingLog("[------------------------       -]");
		addPassingLog("[------------------------        -]");
		addPassingLog("[------------------------         -]");
		addPassingLog("[------------------------          -]");
		addPassingLog("[------------------------           -]");
		addPassingLog("[------------------------            -]");
		addPassingLog("[------------------------             -]");
		addPassingLog("[------------------------              -]");
		addPassingLog("[------------------------               -]");
		addPassingLog("[------------------------                -]");
		addPassingLog("[------------------------                 -]");
		addPassingLog("[------------------------                  -]");
		addPassingLog("[------------------------                   -]");
		addPassingLog("[------------------------                    -]");
		addPassingLog("[------------------------                     -]");
		addPassingLog("[------------------------                      -]");
		addPassingLog("[------------------------                       -]");
		addPassingLog("[------------------------                        -]");
		addPassingLog("[------------------------                         -]");
		addPassingLog("[------------------------                          -]");
	}

	@Test
	public void testHaveLogsBeforeFirstFailure() {
		addPassingLog("[-]");
		addPassingLog("[--]");
		addPassingLog("[---]");
		addPassingLog("[----]");
		addPassingLog("[-----]");
		addFailingLog("[-----X]");
		addPassingLog("[-----X-]");
		addPassingLog("[-----X--]");
		addFailingLog("[-----X--X]");
		addPassingLog("[-----X--X-]");
		addPassingLog("[-----X--X--]");
		addPassingLog("[-----X--X---]");
		addPassingLog("[-----X--X----]");
		addPassingLog("[-----X--X-----]");
		addPassingLog("[-----X--X------]");
		addPassingLog("[-----X--X-------]");
		addPassingLog("[-----X--X--------]");
		addPassingLog("[-----X--X---------]");
		addPassingLog("[-----X--X----------]");
		addPassingLog("[-----X--X-----------]");
		addPassingLog("[-----X--X------------]");
		addPassingLog("[-----X--X-------------]");
		addPassingLog("[-----X--X--------------]");
		addPassingLog("[-----X--X---------------]");
		addPassingLog("[-----X--X----------------]");
		addPassingLog("[-----X--X--------------- -]");
		addPassingLog("[-----X--X---------------  -]");
		addPassingLog("[-----X--X---------------   -]");
		addPassingLog("[-----X--X---------------    -]");
		addPassingLog("[-----X--X---------------     -]");
		addFailingLog("[-----X--X--------------      -X]");
		addPassingLog("[-----X--X-------------       -X-]");
		addPassingLog("[-----X--X-------------       -X -]");
		addFailingLog("[-----X--X------------        -X -X]");
		addFailingLog("[-----X--X-----------         -X -XX]");
		addPassingLog("[-----X--X----------          -X -XX-]");
		addPassingLog("[-----X--X----------          -X -XX -]");
		addFailingLog("[-----X--X---------           -X -XX -X]");
		addPassingLog("[-----X--X--------            -X -XX -X-]");
		addPassingLog("[-----X--X--------            -X -XX -X -]");
		addPassingLog("[-----X--X--------            -X -XX -X  -]");
		addPassingLog("[-----X--X--------            -X -XX -X   -]");
		addPassingLog("[-----X--X--------            -X -XX -X    -]");
		addPassingLog("[-----X--X--------            -X -XX -X     -]");
		addPassingLog("[-----X--X--------            -X -XX -X      -]");
		addPassingLog("[-----X--X--------            -X -XX -X       -]");
		addPassingLog("[-----X--X--------            -X -XX -X        -]");
		addPassingLog("[-----X--X--------            -X -XX -X         -]");
		addPassingLog("[-----X--X--------            -X -XX -X          -]");
		addPassingLog("[-----X--X--------            -X -XX -X           -]");
		addPassingLog("[-----X--X--------            -X -XX -X            -]");
		addPassingLog("[-----X--X--------            -X -XX -X             -]");
		addPassingLog("[-----X--X--------            -X -XX -X              -]");
		addPassingLog("[-----X--X--------            -X -XX -X               -]");
		addPassingLog("[-----X--X--------            -X -XX -X                -]");
		addPassingLog("[-----X--X--------            -X -XX -X                 -]");
		addPassingLog("[-----X--X--------            -X -XX -X                  -]");
		addPassingLog("[-----X--X--------            -X -XX -X                   -]");
		addPassingLog("[-----X--X--------            -X -XX -X                    -]");
		addPassingLog("[-----X--X--------            -X -XX -X                     -]");
		addPassingLog("[-----X--X--------            -X -XX -X                      -]");
		addPassingLog("[-----X--X--------            -X -XX -X                       -]");
		addPassingLog("[-----X--X--------            -X -XX -X                        -]");
		addPassingLog("[-----X--X--------            -X -XX -X                         -]");
		addPassingLog("[-----X--X--------            -X -XX -X                          -]");
		addPassingLog("[-----X--X--------            -X -XX -X                           -]");
		addPassingLog("[-----X--X--------            -X -XX -X                            -]");
	}

	@Test
	public void testNoLogsBeforeFirstFailure() {
		addPassingLog("[-]");
		addPassingLog("[--]");
		addPassingLog("[---]");
		addPassingLog("[----]");
		addPassingLog("[-----]");
		addPassingLog("[------]");
		addPassingLog("[-------]");
		addPassingLog("[--------]");
		addPassingLog("[---------]");
		addPassingLog("[----------]");
		addPassingLog("[-----------]");
		addPassingLog("[------------]");
		addPassingLog("[-------------]");
		addPassingLog("[--------------]");
		addPassingLog("[---------------]");
		addPassingLog("[----------------]");
		addPassingLog("[-----------------]");
		addPassingLog("[------------------]");
		addPassingLog("[-------------------]");
		addPassingLog("[--------------------]");
		addPassingLog("[---------------------]");
		addPassingLog("[----------------------]");
		addPassingLog("[-----------------------]");
		addPassingLog("[------------------------]");
		addPassingLog("[-------------------------]");
		addPassingLog("[------------------------ -]");
		addPassingLog("[------------------------  -]");
		addPassingLog("[------------------------   -]");
		addPassingLog("[------------------------    -]");
		addPassingLog("[------------------------     -]");
		addFailingLog("[-----------------------      -X]");
		addPassingLog("[----------------------       -X-]");
		addPassingLog("[----------------------       -X -]");
		addFailingLog("[---------------------        -X -X]");
		addPassingLog("[--------------------         -X -X-]");
		addPassingLog("[--------------------         -X -X -]");
		addPassingLog("[--------------------         -X -X  -]");
		addPassingLog("[--------------------         -X -X   -]");
		addFailingLog("[-------------------          -X -X   -X]");
		addPassingLog("[------------------           -X -X   -X-]");
		addPassingLog("[------------------           -X -X   -X -]");
		addPassingLog("[------------------           -X -X   -X  -]");
		addFailingLog("[-----------------            -X -X   -X  -X]");
		addPassingLog("[----------------             -X -X   -X  -X-]");
		addFailingLog("[---------------              -X -X   -X  -X-X]");
		addFailingLog("[--------------               -X -X   -X  -X-XX]");
		addPassingLog("[-------------                -X -X   -X  -X-XX-]");
		addPassingLog("[-------------                -X -X   -X  -X-XX -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX  -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX   -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX    -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX     -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX      -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX       -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX        -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX         -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX          -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX           -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX            -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX             -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX              -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX               -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                 -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                  -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                   -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                    -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                     -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                      -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                       -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                        -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                         -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                          -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                           -]");
		addPassingLog("[-------------                -X -X   -X  -X-XX                            -]");	
	}

	
	private void addPassingLog(String expectedStatus) {
		addLog(0, expectedStatus);
	}

	private void addFailingLog(String expectedStatus) {
		addLog(1, expectedStatus);
	}

	private void addLog(int numErrors, String expectedStatus) {
		File logFile = new File("/tmp/logEvictionTest." + nextLogFileNumber + ".ltd");
		nextLogFileNumber++;
		
		logManager.fileCompleted(logFile, numErrors);
		
		System.out.println(logManager.toString());
		assertEquals(expectedStatus, logManager.toString());
	}
}
