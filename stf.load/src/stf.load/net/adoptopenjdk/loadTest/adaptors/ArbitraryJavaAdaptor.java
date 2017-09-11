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

package net.adoptopenjdk.loadTest.adaptors;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;

import net.adoptopenjdk.loadTest.FirstFailureDumper;
import net.adoptopenjdk.stf.StfException;

/**
 * This class allows load test to run any piece of Java code.
 * The 'test' is only recorded as a failure if it throws an exception.
 */
public class ArbitraryJavaAdaptor extends LoadTestBase {
	private String testClassName;
	private String testMethodName;

	private Class<?> javaClass;
	private Constructor<?> constructor;
	private Object[] constructorArgs;
	private Method javaMethod;
	private Object[] methodArgs;
	
	
	public ArbitraryJavaAdaptor(int testNum, String testClassName, ArrayList<String> constructorArgValues, 
					String testMethodName, ArrayList<String> methodArgValues, BigDecimal weighting, boolean countingOnly) 
				throws ClassNotFoundException, NoSuchMethodException, SecurityException, StfException {
		super(testNum, testClassName, testMethodName, weighting);

		if (countingOnly) {
			// Don't need to do anything else as we are only counting how many tests there are.
			// This will be test generation time, so classpath not set to load test cases.
			return;
		}
		
		this.testClassName = testClassName;
		this.testMethodName = testMethodName;

		javaClass = Class.forName(testClassName);
		
		// Find a constructor which is compatible with the supplied constructor arguments
		for (Constructor<?> method : javaClass.getConstructors()) {
			Class<?>[] actualParameterTypes = method.getParameterTypes();
			if (constructorArgValues.size() == actualParameterTypes.length) {
				Object[] candidateConstructorArgs = convertArguments(constructorArgValues, actualParameterTypes);
				if (candidateConstructorArgs != null) {
					constructorArgs = candidateConstructorArgs;
					constructor = method;
					constructor.setAccessible(true);
					break;
				}
			}
		}
		
		// Find an instance method which is compatible with the supplied method arguments
		for (Method method : javaClass.getMethods()) {
			Class<?>[] actualParameterTypes = method.getParameterTypes();
			if (method.getName().equals(testMethodName)  &&  methodArgValues.size() == actualParameterTypes.length) {
				Object[] candidateMethodArgs = convertArguments(methodArgValues, actualParameterTypes);
				if (candidateMethodArgs != null) {
					methodArgs = candidateMethodArgs;
					javaMethod = method;
					break;
				}
			}
		}
		
		// Validate that suitable constructor and test methods were found
		if (constructor == null) {
			throw new StfException("Failed to find a matching constructor for test number '" + testNum + "'."
					+ " Expecting a constructor in class '" + testClassName + "' which is compatible with arguments '" + constructorArgValues + "'");
		}
		if (javaMethod == null) {
			throw new StfException("Failed to find a matching test method for test number '" + testNum + "'." 
					+ " Expecting a method in class '" + testClassName + "' called '" + testMethodName + "' which is compatible with arguments '" + methodArgValues.toString() + "'");
		}
	}
	
	
	@Override
	public ResultStatus executeTest() throws Throwable {
		// Create instance of test class and run the test method
		try {
			Object instance = constructor.newInstance(constructorArgs);
			javaMethod.invoke(instance, methodArgs);
		} catch (InvocationTargetException e) {
			// Test will be marked as a failure
			FirstFailureDumper.instance().createDumpIfFirstFailure(this);
			throw e.getCause();
		} catch (Throwable t) {
			// Test will be marked as a failure
			FirstFailureDumper.instance().createDumpIfFirstFailure(this);
			throw t;
		}
		
		return ResultStatus.UNKNOWN;
	}
	
	
	// Attempts to convert a bunch of String values into the types for a constructor/method.
	// Returns null if not compatible, otherwise returns an array with the string values 
	// converted to the appropriate Java object.
	private Object[] convertArguments(ArrayList<String> sourceValues, Class<?>[] destinationTypes) {
		Object[] converted = new Object[sourceValues.size()];
		
		for (int i=0; i<sourceValues.size(); i++) {
			String sourceValue = sourceValues.get(i);
			String destinationType = destinationTypes[i].getName();
			
			Object convertedValue = convertToType(sourceValue, destinationType);
			if (convertedValue == null) {
				// Not possible to convert the current value to the target type. Bail out.
				return null;
			}
			converted[i] = convertedValue;
		}

		return converted;
	}

	
	// Converts a single value from a String to the specified type.
	// Returns null if the conversion from string value to required type is not possible/supported. 
	// If a conversion from String to the destination type is supported then this method returns 
	// a Java object which holds the source value.
	private Object convertToType(String sourceValue, String destinationType) {
		try {
			if (destinationType.equals("java.lang.String")) {
				return sourceValue;
			} else if (destinationType.equals("int")) {
				return new Integer(sourceValue);
			} else if (destinationType.equals("long")) {
				return new Long(sourceValue);
			} else if (destinationType.equals("float")) {
				return new Float(sourceValue);
			} else if (destinationType.equals("double")) {
				return new Double(sourceValue);
			} else if (destinationType.equals("boolean")) {
				if (sourceValue.equalsIgnoreCase("true") || sourceValue.equalsIgnoreCase("false")) { 
					return new Boolean(sourceValue);
				}				
			}
		} catch (NumberFormatException e) {
			// Conversion failed, so obviously not compatible 
			return null;
		}

		return null;
	}


	@Override
	public ResultStatus checkTestOutput(byte[] output, int off, int len) {
		return ResultStatus.UNKNOWN;
	}
	
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!(o instanceof ArbitraryJavaAdaptor)) {
			return false;
		}
		ArbitraryJavaAdaptor junit = (ArbitraryJavaAdaptor) o;
		return testClassName.equals(junit.testClassName) && testMethodName.equals(junit.testMethodName);
	}
	
	public int hashCode() { 
		return testClassName.hashCode() + testMethodName.hashCode();
	}
	
	public String toString() { 
		return "ArbitraryJava[" + testClassName + " " + testMethodName + "]";
	}
}