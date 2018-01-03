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

package net.adoptopenjdk.stf.extensions;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.Argument.Required;
import net.adoptopenjdk.stf.extensions.interfaces.StfExtension;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This class runs as an extension class which is used in all STF runs, regardless of 
 * the extensions which are actually used by the plugin class.
 */
public class Stf implements StfExtension {
	//                        Java name                                                     Argument name                     Boolean          Type
	// Exclusively for perl layer
	public static Argument ARG_DRY_RUN                             = new Argument("stf", "dry-run",                             true,  Required.OPTIONAL);
	public static Argument ARG_DEBUG_GENERATION                    = new Argument("stf", "debug-generation",                    true,  Required.OPTIONAL);
	public static Argument ARG_JAVA_DEBUG_ARGS                     = new Argument("stf", "java-debug-args",                     false, Required.MANDATORY);
	public static Argument ARG_CREATE_RESULTS_SYM_LINKS            = new Argument("stf", "create-results-sym-links",            true,  Required.MANDATORY);
	public static Argument ARG_RM_PASS                             = new Argument("stf", "rm-pass",                             true,  Required.OPTIONAL);
	// For perl and java
	public static Argument ARG_HELP                                = new Argument("stf", "help",                                true,  Required.OPTIONAL);
	public static Argument ARG_LIST_TESTS                          = new Argument("stf", "list",                                true,  Required.OPTIONAL);	
	public static Argument ARG_TEST                                = new Argument("stf", "test",                                false, Required.MANDATORY);
	// Exclusively for Java usage
	public static Argument ARG_TEST_ROOT                           = new Argument("stf", "test-root",                           false, Required.OPTIONAL);
	public static Argument ARG_TEST_ARGS                           = new Argument("stf", "test-args",                           false, Required.OPTIONAL);
	public static Argument ARG_STF_BIN_DIR                         = new Argument("stf", "stf-bin-dir",                         false, Required.OPTIONAL);
	public static Argument ARG_SYSTEMTEST_PREREQS                  = new Argument("stf", "systemtest-prereqs",                     false, Required.MANDATORY);
	public static Argument ARG_REPEAT_COUNT                        = new Argument("stf", "repeat",                              false, Required.OPTIONAL);
	public static Argument ARG_VERBOSE                             = new Argument("stf", "v",                                   true,  Required.OPTIONAL);	
	public static Argument ARG_VERBOSE_VERBOSE                     = new Argument("stf", "vv",                                  true,  Required.OPTIONAL);	
	public static Argument ARG_JAVAHOME_GENERATION                 = new Argument("stf", "javahome-generation",                 false, Required.MANDATORY);
	public static Argument ARG_JAVAHOME_SETUP                      = new Argument("stf", "javahome-setup",                      false, Required.MANDATORY);
	public static Argument ARG_JAVAHOME_EXECUTE                    = new Argument("stf", "javahome-execute",                    false, Required.MANDATORY);
	public static Argument ARG_JAVAHOME_EXECUTE_SECONDARY          = new Argument("stf", "javahome-execute-secondary",          false, Required.OPTIONAL);
	public static Argument ARG_JAVAHOME_TEARDOWN                   = new Argument("stf", "javahome-teardown",                   false, Required.MANDATORY);
	public static Argument ARG_RESULTS_ROOT                        = new Argument("stf", "results-root",                        false, Required.MANDATORY);
	public static Argument ARG_PLATFORM                            = new Argument("stf", "platform",                            false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS                           = new Argument("stf", "java-args",                           false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_SETUP                     = new Argument("stf", "java-args-setup",                     false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE_INITIAL           = new Argument("stf", "java-args-execute-initial",           false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE                   = new Argument("stf", "java-args-execute",                   false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE_COMMENT           = new Argument("stf", "java-args-execute-comment",           false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE_SECONDARY_INITIAL = new Argument("stf", "java-args-execute-secondary-initial", false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE_SECONDARY         = new Argument("stf", "java-args-execute-secondary",         false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_EXECUTE_SECONDARY_COMMENT = new Argument("stf", "java-args-execute-secondary-comment", false, Required.OPTIONAL);
	public static Argument ARG_JAVA_ARGS_TEARDOWN                  = new Argument("stf", "java-args-teardown",                  false, Required.OPTIONAL);
	// Stf doesn't use these but since the perl layer requires defaults
	// in the properties file they get set, so need to tolerate them
	// To avoid confusion these aren't listed in the help output
	public static Argument ARG_RETAIN                              = new Argument("stf", "retain",                              false, Required.OPTIONAL);
	public static Argument ARG_RETAIN_LIMIT                        = new Argument("stf", "retain-limit",                        false, Required.OPTIONAL);
	
	@Override
	public Argument[] getSupportedArguments() {
		return new Argument[] {
			ARG_DRY_RUN,
			ARG_DEBUG_GENERATION,
			ARG_JAVA_DEBUG_ARGS,
			ARG_CREATE_RESULTS_SYM_LINKS,
			ARG_RM_PASS,
			ARG_TEST_ROOT,
			ARG_HELP,
			ARG_LIST_TESTS,
			ARG_TEST,                 
			ARG_TEST_ARGS,            
			ARG_STF_BIN_DIR,
			ARG_SYSTEMTEST_PREREQS,
			ARG_REPEAT_COUNT,
			ARG_VERBOSE,
			ARG_VERBOSE_VERBOSE,
			ARG_JAVAHOME_GENERATION,
			ARG_JAVAHOME_SETUP,
			ARG_JAVAHOME_EXECUTE,
			ARG_JAVAHOME_EXECUTE_SECONDARY,
			ARG_JAVAHOME_TEARDOWN,
			ARG_RESULTS_ROOT,
			ARG_PLATFORM,
			ARG_JAVA_ARGS,
			ARG_JAVA_ARGS_SETUP,
			ARG_JAVA_ARGS_EXECUTE_INITIAL,
			ARG_JAVA_ARGS_EXECUTE,
			ARG_JAVA_ARGS_EXECUTE_COMMENT,
			ARG_JAVA_ARGS_EXECUTE_SECONDARY_INITIAL,
			ARG_JAVA_ARGS_EXECUTE_SECONDARY,
			ARG_JAVA_ARGS_EXECUTE_SECONDARY_COMMENT,
			ARG_JAVA_ARGS_TEARDOWN,
			ARG_RETAIN,
			ARG_RETAIN_LIMIT
		};
	}

	@Override
	public void help(HelpTextGenerator help) {
		help.outputSection("Stf extension options");
		
		help.outputArgName("-" + ARG_TEST.getName(), "NAME");
		help.outputArgDesc("This is the name of the test plugin to run. Stf runs a Java class "
				    + "matching this name which also implements an interface which ultimately "
				    + "implements StfPluginRootInterface.");
		
		help.outputArgName("-" + ARG_TEST_ROOT.getName(), "DIRECTORY");
		help.outputArgDesc("This mandatory argument points at one or more directories containing test cases, including "
				    + "the primary STF workspace, and seperated by semicolons. It should be set to point to one or more "
				    + "Eclipse workspaces, or the equivilent directory in the build output (ie. the directory that "
				    + "contains the project directories, which in turn hold the test case class files).");
		
		help.outputArgName("-" + ARG_TEST_ARGS.getName(), "VALUES");
		help.outputArgDesc("This optional argument allows test specific information to be supplied "
					+ "to a test plugin. It is formatted as one or more comma separated name/value pairs. "
					+ "For example '-test-args=\"suite=suite1, timeout=30m\"'.");
		
		help.outputArgName("-" + ARG_SYSTEMTEST_PREREQS.getName(), "DIRECTORY");
		help.outputArgDesc("Points to the location of the mandatory jars, test material, etc.");
		
		help.outputArgName("-" + ARG_REPEAT_COUNT.getName());
		help.outputArgDesc("This is the number of times that the test plugins execute method is called. "
					+ "Defaults to a value of 1.");

		help.outputArgName("-" + ARG_DRY_RUN.getName());
		help.outputArgDesc("Generates the setup, execute and teardown scripts, but does not execute them. "
				         + "The commands needed for manual execution are listed.");

		help.outputArgName("-" + ARG_HELP.getName());
		help.outputArgDesc("Produces help text. It lists the options supported by each extension used by the test plugin. "
                         + "Note that because tests are free to use different extensions, so the actual "
                         + "options available may vary between tests.");
		
		help.outputArgName("-" + ARG_LIST_TESTS.getName());
		help.outputArgDesc("Searches the workspace for all test cases, which are then listed in project order.");
		
		help.outputArgName("-" + ARG_DEBUG_GENERATION.getName());
		help.outputArgDesc("Setting this argument allows for the debugging on a test plugin. If set then "
				+ "debug arguments are added to the command used to run the Stf Java process.");
	
		help.outputArgName("-" + ARG_JAVA_ARGS.getName(), "JAVA-ARGS");
		help.outputArgDesc("Set Java arguments to be used for Java processes run in the execute stage.");

		help.outputArgName("-" + ARG_JAVA_ARGS_SETUP.getName(), "JAVA-ARGS");
		help.outputArgDesc("Set Java arguments used by Java processes in the setup stage.");

		help.outputArgName("-" + ARG_JAVA_ARGS_EXECUTE_INITIAL.getName(), "JAVA-ARGS");
		help.outputArgDesc("This argument can supply JVM arguments for the unusual case in "
				+ "which the arguments need to appear before any JVM arguments that the testcase specifies. "
				+ "Only used in the exceute stage.");

		help.outputArgName("-" + ARG_JAVA_ARGS_EXECUTE.getName(), "JAVA-ARGS");
		help.outputArgDesc("Set Java arguments used by Java processes in the execute stage. "
				+ "The arguments appear after those specified by the testcase. "
				+ "Default behaviour is to use the value of '-" + ARG_JAVA_ARGS.getName() + "'.");

		help.outputArgName("-" + ARG_JAVA_ARGS_EXECUTE_SECONDARY_INITIAL.getName(), "JAVA-ARGS");
		help.outputArgDesc("As per the '-" + ARG_JAVA_ARGS_EXECUTE_INITIAL.getName() +"' argument, but for the secondary JVM.");

		help.outputArgName("-" + ARG_JAVA_ARGS_EXECUTE_SECONDARY.getName(), "JAVA-ARGS");
		help.outputArgDesc("As per the '-" + ARG_JAVA_ARGS_EXECUTE.getName() +"' argument, but for the secondary JVM.");

		help.outputArgName("-" + ARG_JAVA_ARGS_TEARDOWN.getName(), "JAVA-ARGS");
		help.outputArgDesc("Set Java arguments used by Java processes in the teardown stage.");

		help.outputArgName("-" + ARG_JAVA_DEBUG_ARGS.getName(), "JAVA-DEBUG-ARGS");
		help.outputArgDesc("This contains the debugging options to allow a java debugger to connect to "
				+ "the Stf generator program. The current default is set to '-Xdebug -Xrunjdwp:transport=dt_socket,address=8999,server=y,suspend=y'.");

		help.outputArgName("-" + ARG_JAVAHOME_GENERATION.getName(), "DIRECTORY");
		help.outputArgDesc("This JVM will be used during the perl generation stage.");
		
		help.outputArgName("-" + ARG_JAVAHOME_SETUP.getName(), "DIRECTORY");
		help.outputArgDesc("This JVM will be used by the generated setup script.");
		
		help.outputArgName("-" + ARG_JAVAHOME_EXECUTE.getName(), "DIRECTORY");
		help.outputArgDesc("This JVM will be used by the generated execute script.");
		
		help.outputArgName("-" + ARG_JAVAHOME_EXECUTE_SECONDARY.getName(), "DIRECTORY");
		help.outputArgDesc("Points at a second JVM, for tests which want to run with 2 different versions of Java.");
		
		help.outputArgName("-" + ARG_JAVAHOME_TEARDOWN.getName(), "DIRECTORY");
		help.outputArgDesc("This JVM will be used by the generated teardown script.");
		
		help.outputArgName("-" + ARG_RESULTS_ROOT.getName(), "DIRECTORY");
		help.outputArgDesc("This is a directory points to the results directory and is set by the stf.pl script.");
		
		help.outputArgName("-" + ARG_PLATFORM.getName(), "NAME");
		help.outputArgDesc("The name of the platform is usually calculated by examining the Java "
				+ "properties, but for testing purposes another value can be supplied. The "
				+ "platform name should be in the form '<os-name>_<architecture>-<wordsize>', eg, linux_x86-64");

		help.outputArgName("-" + ARG_VERBOSE.getName());
		help.outputArgDesc("Enables verbose output. Produces a summary of STF internal activity.");

		help.outputArgName("-" + ARG_VERBOSE_VERBOSE.getName());
		help.outputArgDesc("Enables super-verbose output. Produces detailed information "
				+ "about what STF is doing. This is usually only used for STF debugging.");
		
		help.outputArgName("-" + ARG_CREATE_RESULTS_SYM_LINKS.getName());
		help.outputArgDesc("If set to true then stf.pl creates a symbolic link to the latest set of results, "
				+ "eg, /tmp/stf/UtilLoadTest -> /tmp/stf/20161107-134621-UtilLoadTest. " 
				+ "This is can be useful when repeatedly running the same test as the result/tmp files will "
				+ "always be at the same location.\n"
				+ "Only supported on systems which support symbolic links.\n"
				+ "Disabled by default to prevent.");
		
		help.outputArgName("-" + ARG_RM_PASS.getName());
		help.outputArgDesc("If set to true (or just passed as a flag) then stf.pl deletes the results directory if the test passes."
				+ "Disabled by default.");
	}

	@Override
	public void initialise(StfEnvironmentCore environmentCore, StfExtensionBase extensionBase, PerlCodeGenerator generator) throws StfException {
	}
}
