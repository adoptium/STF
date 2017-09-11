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

package net.adoptopenjdk.stf;

import java.util.ArrayList;

import org.junit.Test;

import junit.framework.TestCase;
import net.adoptopenjdk.stf.StfError;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.LayeredProperties;
import net.adoptopenjdk.stf.environment.properties.OrderedProperties;
import net.adoptopenjdk.stf.environment.properties.Argument.Required;


/**
 * This test case verifies that property substitution of '${x}' values is working.
 */
public class TestArgumentProcessing extends TestCase {
	private static Argument ARG_STF_BIN_DIR = new Argument("stf", "stf-bin-dir", false, Required.OPTIONAL);

	
	@Test 
	public void testOverridenProperty() throws StfException {
		LayeredProperties props = createProperties(
				new String[] { 
						"dry-run=false", 
						"stf-bin-dir=/usr/home/bin",  // new value
						"x=y" 
						},
				new String[] {
						"p1=x",
						"p2=y",
						"stf-bin-dir=!!!!!"   // old value
						}
				);
		
		assertEquals("/usr/home/bin", props.getProperty(ARG_STF_BIN_DIR.getName()));
	}
	
	@Test 
	public void testEnvironmentVariableReplacement() throws StfException {
		LayeredProperties props = createProperties(
				new String[] { 
						"dry-run=false", 
						"stf-bin-dir=/home/${JAVA_HOME}/bin",  // JAVA_HOME is not a sensible value for this. But it is available on all platforms
						"x=y" 
						}
				);
		
		assertEquals("/home/" + System.getenv("JAVA_HOME") + "/bin", props.getProperty(ARG_STF_BIN_DIR.getName()));
	}
	
	@Test 
	public void testPropertyReplacement() throws StfException {
		LayeredProperties props = createProperties(
				new String[] { 
						"stf-bin-dir=/home/${user-name}/bin/${process-name}",
						"user-name=fred",
						"x=y"
						},
				new String[] {
						"process-name=search.sh",
						"p1=x",
						"p2=y",
						"stf-bin-dir=!!!!!"
						}
				);
		
		assertEquals("/home/fred/bin/search.sh", props.getProperty(ARG_STF_BIN_DIR.getName()));
	}
	
	
	@Test 
	public void testPropertyReplacementIndirect() throws StfException {
		String userNameVariable;
		if (System.getProperty("os.name").equals("Linux")) {
			userNameVariable = "USER";
		} else {
			userNameVariable = "USERNAME";
		}
		
		LayeredProperties props = createProperties(
				new String[] { 
						"user-name=${first-name}.${last-name}",
						"process-name=search.sh",
						"stf-bin-dir=/home/${user-name}/bin/${process-name}",
						"x=y"
						},
				new String[] {
						"p1=x",
						"p2=y",
						"first-name=fred",
						"last-name=${" + userNameVariable + "}",  // Pulls value from env.variable
						"stf-bin-dir=!!!!!"
						}
				);
		
		String expected = "/home/fred." + System.getenv(userNameVariable) + "/bin/search.sh";
		String actual = props.getProperty(ARG_STF_BIN_DIR.getName());
		assertEquals(expected, actual);
	}
	
	
	@Test 
	public void testPropertyReplacementIndirectFails() throws StfException {
		LayeredProperties props = createProperties(
				new String[] { 
						"user-name=${first-name}${last-name}",
						"process-name=search.sh",
						"stf-bin-dir=/home/${user-name}/bin/${process-name}",
						"x=y"
						},
				new String[] {
						"p1=x",
						"p2=y",
						"first-name=fred",
						"last-name=${login-name}",  // No value specified for login-name !!
						"stf-bin-dir=!!!!!"
						}
				);

		boolean gotException = false;
		try {
		    props.getProperty(ARG_STF_BIN_DIR.getName());
		    fail();
		} catch (StfError e) {
			gotException = true;
			String expectedError = "Unable to succesfully build value for property 'stf-bin-dir'. There is no enviroment variable or property defined for 'login-name'. Chain followed is: stf-bin-dir -> user-name -> last-name -> login-name";
			assertEquals(expectedError, e.getMessage());
		}
		
		assertTrue(gotException);
	}
	

	private LayeredProperties createProperties(String[]... layers) {
		ArrayList<OrderedProperties> properties = new ArrayList<OrderedProperties>();
		
		for (String[] props : layers) {
			//System.out.println("Layer is: " + Arrays.toString(props));
			OrderedProperties orderedProperties = new OrderedProperties();
			properties.add(orderedProperties);
			
			for (String nameValuePair : props) {
				nameValuePair = nameValuePair.trim();
				int equalsAt = nameValuePair.indexOf("=");
				String name = nameValuePair.substring(0, equalsAt);
				String value = nameValuePair.substring(equalsAt+1);
				//System.out.println("  '" + name + "' = '" + value + "'");
				orderedProperties.put(name, value);
			}
		}
		
		return new LayeredProperties(properties, new ArrayList<Argument>());
	}
}
