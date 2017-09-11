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

import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_COMMAND_MNEMONIC;
import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_COMMAND_NUMBER;
import static net.adoptopenjdk.stf.StfConstants.PLACEHOLDER_STF_PROCESS_INSTANCE;
import static net.adoptopenjdk.stf.StfConstants.PERL_PROCESS_DATA;
import static net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo.ECHO_ON;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.adoptopenjdk.stf.StfConstants;
import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.JavaVersion;
import net.adoptopenjdk.stf.environment.PlatformFinder;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.extensions.Stf;
import net.adoptopenjdk.stf.extensions.core.StfCoreExtension.Echo;
import net.adoptopenjdk.stf.processes.ExpectedOutcome;
import net.adoptopenjdk.stf.processes.StfProcess;
import net.adoptopenjdk.stf.processes.definitions.ProcessDefinition;
import net.adoptopenjdk.stf.util.StringSplitter;


/**
 * This class controls the process of adding generated perl code into the setup,
 * execute and teardown test scripts.
 * 
 * It is assumed that only extension classes will be using this class, as they 
 * are providing the fixtures which can be used by the test cases.
 */
public class PerlCodeGenerator {
    private static final Logger logger = LogManager.getLogger(PerlCodeGenerator.class.getName());

    private StfEnvironmentCore environmentCore;
	
	private Stage stage;
	private String methodName;
	private FileRef outputFile;
	private BufferedWriter perlFile;
	private int indentationDepth;

	// Command serial number is static, so that it is not reset for each stage. 
	private static int commandSerialNum = 0;

	private static int longestStageName;

	// Tracks the perl variables used in the current script
	private LinkedHashSet<String> declaredVariables;
	
	// Keep a 1 line summary on each generated command. To be logged at the end of code generation.
	private ArrayList<String> commandSummaryLog = new ArrayList<String>();
	
	
	// Results class to hold details on a command
	public static class CommandDetails {
		private int commandSerialNum;
		String executableName;
		String[] args;
		String argumentComment;
		HashMap<String, StfProcess> relatedProcesses = new HashMap<String, StfProcess>();
		HashMap<String, Integer> relatedProcessesData = new HashMap<String, Integer>();
		
		public void setRelatedProcesses(HashMap<String, StfProcess> rp, HashMap<String, Integer> rpd) {
			relatedProcesses = rp;
			relatedProcessesData = rpd;
		}
		
		public String getExecutableName() { return executableName; }
		public String[] getArgs() { return args; }
		public String getArgumentComment() { return argumentComment; }
		public HashMap<String, StfProcess> getRelatedProcesses() { return relatedProcesses; }
		public HashMap<String, Integer> getRelatedProcessesData() { return relatedProcessesData; }
		
		public String getAsSingleLineCommand() {
			StringBuilder singleLineCommand = new StringBuilder(executableName);
			for (String arg : args) {
				singleLineCommand.append(" " + arg);
			}
			return singleLineCommand.toString(); 
		}
		
		public int getCommandSerialNum() {
			return commandSerialNum;
		}
	}


	public static void setStageNameLength(int longestStageName) {
		PerlCodeGenerator.longestStageName = longestStageName;
	}

	
	public PerlCodeGenerator(StfEnvironmentCore environmentCore, String methodName, Stage stage, FileRef outputFile) throws StfException {
		this.environmentCore = environmentCore;

		this.methodName = methodName; 
		this.stage = stage;
		this.outputFile = outputFile;
		
		this.declaredVariables = new LinkedHashSet<String>();

		// Create perl output file
		if (stage != Stage.INITIALISATION) {
			boolean isExistingPerlFile = outputFile.asJavaFile().exists();
			try {
				FileWriter fw = new FileWriter(outputFile.asJavaFile(), true);
				perlFile = new BufferedWriter(fw);
			} catch (IOException e) {
				throw new StfException("Failed to create perl file: " + outputFile, e);
			}
			
			if (!isExistingPerlFile) {
				// Outputting a new file
				outputPerlHeaders();
			}
		}
	}
	
	
	private void outputPerlHeaders() throws StfException {
		DirectoryRef scriptDir = environmentCore.findTestDirectory("stf.core/scripts");

		// Write perl header lines
		outputLine("#!/usr/bin/perl");
		outputLine("# ");
		outputLine("# This script has been automatically generated by STF.");
		outputLine("# Do not check-in or attempt to make permanent changes.");
		outputLine("#");
		outputLine("# Test:         " + environmentCore.getProperty(Stf.ARG_TEST));
		outputLine("# Test-args:    " + environmentCore.getProperty(Stf.ARG_TEST_ARGS));
		outputLine("# Platform:     " + PlatformFinder.getPlatformAsString());
		outputLine("# Generated at: " + new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS").format(new Date()));
		outputLine("#");
		outputEmptyLine();
		outputLine("# Turn on autoflush");
		outputLine("$| = 1;");
		outputEmptyLine();
		outputLine("use warnings;");
		outputLine("use strict;");
		outputEmptyLine();
		outputLine("use lib \"" + scriptDir.getSpec() + "\";");
		outputEmptyLine();
		outputLine("use FindBin qw($Bin);");
		outputLine("use File::Path qw(mkpath rmtree);");
		outputLine("use File::Copy qw(copy);");
		outputEmptyLine();
		outputLine("use stf::stfUtility;");
		outputLine("use stf::Commands;");

		// Set the windows_sysinternals directory as an environment variable.
		// This is used in stfCommands.pm to locate windows_sysinternals.
		if (PlatformFinder.isWindows()) {
			DirectoryRef sysInternalsDir = environmentCore.findPrereqDirectory("windows_sysinternals");
			outputLine("$ENV{'WINDOWS_SYSINTERNALS_ROOT'} = \"" + sysInternalsDir.getSpec() + "\";");
		}
		outputEmptyLine();
		outputLine("my $rc;");
		outputLine("my $err;");
		
		// Tell perl modules how much logging is needed
		outputEmptyLine();
		String loggingLevel = "INFO";
		if (environmentCore.isVerboseSet()) {
			loggingLevel = "DEBUG";  // run with '-v'
		}
		if (environmentCore.isSuperVerboseSet()) {
			loggingLevel = "TRACE";  // run with '-vv'
		}
		outputLine("$ENV{'loggingLevel'} = \"" + loggingLevel + "\";");
		
		// If running on OMVS (the ZOS unix environment) then SYSLOG messages
		// from child processes need to be echo'ed to stderr.
		// An example would be 'dump taken for Java job xxx' messages.
		if (PlatformFinder.isZOS()) {
			outputEmptyLine();
			outputLine("# Make sure zos SYSLOG messages are echo'ed to standard error");
			outputLine("$ENV{'_BPXK_JOBLOG'} = 2;");
		}
		
		// Execution stage needs to run 'java -version'
		if (stage == Stage.EXECUTE) {
			String javaCommand = environmentCore.getJavaHome().childFile("bin/java").getSpec();
			String javaVersionCommand = javaCommand + " -version";
			outputEmptyLine();
			outputEmptyLine();
			outputLine("# ");
			outputLine("# To help with debugging show the version of Java being used");
			outputLine("# ");
			outputLine("info('');");
			outputLine("info('Java version');");
			outputLine("info('Running: " + javaVersionCommand + "');");
			outputLine("system(\"" + javaVersionCommand + "\");");
		}
		
		// Make sure that any core dumps are created in the results directory
		outputEmptyLine();
		outputLine("# Move to the results directory, so that any created core files go there");
		outputLine("chdir '" + environmentCore.getResultsDir() + "';");
	}
	
	
	// Outputs perl to initialise a variable.
	// If the variable has not been used before then it is created. eg 
	//   my $x = 0;
	//   $x = 5;
	private void outputVariableInitialisation(String variableName, String initalValue) throws StfException {
		String prefix = "";
		if (!declaredVariables.contains(variableName)) {
			// New variable, so create and add to list of known variables.
			prefix = "my ";
			declaredVariables.add(variableName);
		}
		
		outputLine(prefix + variableName + " = " + initalValue + ";");
	}


	// Outputs perl code to declare a variable.
	// If the variable has not been used before then it is created. eg 
	//   my outfile;
	public void outputCreateVariable(String variableName) throws StfException {
		if (!declaredVariables.contains(variableName)) {
			// New variable, so create and add to list of known variables.
			declaredVariables.add(variableName);

			outputLine("my " + variableName + ";");
		}
	}


	/** 
	 * Generate code for the monitor_processes perl method.
	 */
	public void monitorProcesses(ArrayList<StfProcess> processesToMonitor) throws StfException {
		StringBuilder processList = createProcessReferenceString(processesToMonitor);
		
		outputLine("$rc = stf::Commands->monitor_processes(" + processList.toString() + ");");
	}


	/**
	 * Generate code to kill one or more processes.
	 * @param resultAssignment is the code to generate to capture the result of the kill call.
	 * Set to empty string if you don't want to know the result code, otherwise something like "$rc = ".
	 */
	public void outputKillProcesses(ArrayList<StfProcess> processesToKill, String resultAssignment) throws StfException {
		if (!processesToKill.isEmpty()) {
			StringBuilder processList = createProcessReferenceString(processesToKill);
			outputLine(resultAssignment + "stf::Commands->kill_processes(" + processList.toString() + ");");
		}
	}

	
	// Create a comma separated list of perl variable names for the supplied process details
	// Eg, returns a string such as '$process_3SRV, %process_4CLS'
	private StringBuilder createProcessReferenceString(ArrayList<StfProcess> processes) {
		HashSet<String> processedVariables = new HashSet<String>();
		StringBuilder processList = new StringBuilder();

		for (StfProcess process : processes) { 
			// Don't allow the variable onto the list more than once
			String perlProcessVariable = process.getPerlProcessVariable();
			if (processedVariables.contains(perlProcessVariable)) {
				continue;
			}
			processedVariables.add(perlProcessVariable);
			
			if (processList.length() > 0) {
				processList.append(", ");
			}
			processList.append(perlProcessVariable);
		}
		return processList;
	}
		
	
	/**
	 * This method generates the perl code needed to run a process.
	 * It is used for both synchronous and asynchronous methods, as the arguments used 
	 * are virtually the same.
	 * @param comment is a high level summary of why this process is being run
	 * @param argumentComment is a further comment about the arguments. Mostly used to expose the seed for -mode=random
	 * @param mnemonic is a 3 letter abbreviation of the process. The mnemonic is prefixed 
	 * to every line of output when echoed by STF. 
	 * @param numInstances is the number of process instances to start with the same configuration.
	 * @param echoSetting turns on/off child process echoing.
	 * @param perlMethod is the name of the perl method to invoke.
	 * @param expectedOutcome describes the automations expectation of process completion.
	 * @param command represents the command contains the arguments.
	 * @return an array list of StfProcess objects to represent the running processes.
	 * @throws StfException
	 */
	public StfProcess[] generateRunProcess2(String comment, String argumentComment, String mnemonic, int numInstances, Echo echoSetting, String perlMethod, ExpectedOutcome expectedOutcome, CommandDetails command) throws StfException {
		// Make sure that the automation has set a maximum run time (it's easy to miss)
		if (expectedOutcome.getDurationLimit() == null) {
			throw new StfException("Maximum run time not set for process " + mnemonic + " ('" + comment + "')."
					+ " The ExpectedOutcome object needs a call to the within() method");
		}
		
		// Output the command and its arguments.
		// Depending on the length and content of the command 1 of 3 different formatting options is used.
		String asSingleLineCommand = command.getAsSingleLineCommand();
		String commandString;
		String argsVariableName = null;
		if (needArrayBasedArgs(command)) {
			// Arguments contain special characters. Output command in array based format.
			// Write out a comment with the single line version of this command. 
			// This allows somebody to cut+paste the whole line to manually run the same command.
			String pastableCommand = asSingleLineCommand.replace(PLACEHOLDER_STF_PROCESS_INSTANCE, "");
			outputLine("# Cut+paste version of the command: " + replaceKeysWithValues(pastableCommand,"",command));
			outputEmptyLine();
	
			// Build up an array to hold all arguments for the command
			argsVariableName = "args" + commandSerialNum;
			outputLine("my @" + argsVariableName + " = [");
			for (String arg : command.args) { 
				outputLine("    '" + replaceKeysWithValues(arg,"'",command) + "',");
			}
			outputLine("];");		
			commandString = "'" + replaceKeysWithValues(command.executableName,"'",command) + "'";
		} else if (command.getAsSingleLineCommand().length() < 120) {
			// Short commands can fit onto a single line
			commandString = "'" + replaceKeysWithValues(asSingleLineCommand,"'",command) + "'";			
		} else {
			// Longer multiline command.
			// Write out a comment with the single line version of this command. 
			// This allows somebody to cut+paste the whole line to manually run the same command.
			String pastableCommand = asSingleLineCommand.replace(PLACEHOLDER_STF_PROCESS_INSTANCE, "");
			outputLine("# Cut+paste version of the command: " + replaceKeysWithValues(pastableCommand,"",command));
			outputEmptyLine();
	
			// Build a neatly formatted command string from all of the arguments
			StringBuilder commandStr = new StringBuilder();
			commandStr.append("'" + command.executableName);
			
			// Write out the command and its arguments. 
			// Combine an argument and value onto the same line if possible. 
			// eg, generate '-timeLimit 8m20s ' on 1 line instead of taking 2 lines. 
			boolean lastArgStartsHypen = false;
			for (int i=0; i<command.args.length; i++) {
				String arg = command.args[i].trim();
				
				// Output white space, and possible open quote, in preparation for the argument value  
				boolean thisArgStartsHypen = arg.startsWith("-") && !arg.startsWith("-1");  // Don't put the common numeric '-1' on its own line
				if (lastArgStartsHypen && !thisArgStartsHypen) {
					// The current argument is going on the same line
					commandStr.append(" ");
				} else {
					// Force a newline
					commandStr.append(" ' .\n    '");
				}
				lastArgStartsHypen = thisArgStartsHypen;
				
				// Finally write out the argument value
				commandStr.append(arg);
			}
			commandStr.append(" '"); // close the final argument
			commandString = replaceKeysWithValues(commandStr.toString(),"'",command);
		}
		
		// Declare the command to be executed
		String commandVariable = "$command" + PerlCodeGenerator.commandSerialNum;
		String commandComment = "# The command to run";
		if (argumentComment != null && !argumentComment.isEmpty()) {
			commandComment = commandComment + argumentComment;
		}
		outputLine(commandComment);
		outputLine("my " + commandVariable + " = " + commandString + ";");
		outputEmptyLine();

		// Create stdout/sdterr File references, for later access through the StfProcess object
		DirectoryRef resultsDir = environmentCore.getResultsDir();
		String commandName = commandSerialNum + "." + mnemonic;
		FileRef stdout = resultsDir.childFile(commandName + ".stdout");
		FileRef stderr = resultsDir.childFile(commandName + ".stderr");
		
		String processVariable = null;  // holds the name of the perl process reference
		String resultsValue = null;
		String argStart = "{";
		String argEnd   = "}";
		if (perlMethod.equals("start_process") || perlMethod.equals("run_process")) {
			// Command will look like: ($rc, $process_2SER) = stf::Commands->start_process({ ...
			argStart = "";
			argEnd   = "";
			processVariable = "$" + "process_" + commandSerialNum + mnemonic;
			resultsValue = "($rc, " + processVariable + ")";
			outputLine("my " + processVariable + ";");
		} else if (perlMethod.equals("start_processes") || perlMethod.equals("run_processes")) {
			// Command will look like: ($rc, %process_3CLI) = stf::Commands->start_processes({ ...
			processVariable = "%" + "process_" + commandSerialNum + mnemonic;
			resultsValue = "($rc, " + processVariable + ")";
			outputLine("my " + processVariable + ";");
		} else {
			throw new StfException("Internal Error: method unknown: " + perlMethod);
		}
		
		// Run the command
		outputLine("# Execute the " + mnemonic + " command, and wait for it to finish");
		outputLine(resultsValue + " = stf::Commands->" + perlMethod + "(" + argStart);
	    outputLine("    mnemonic  => '" + mnemonic + "',");
	    outputLine("    command   => " + commandVariable + ",");
	    if (argsVariableName != null) {
	    	outputLine("    args      => @" + argsVariableName + ",");
	    }
	    if (numInstances > 1) {
	    	outputLine("    instances => '" + numInstances + "',");
	    }
	    outputLine("    logName   => '" + resultsDir.childFile(commandName) + "',");
	    outputLine("    echo      => '" + (echoSetting == ECHO_ON ? "1" : "0") + "',");
	    outputLine("    runtime   => '" + expectedOutcome.getDurationLimit().getSeconds() + "',");
	    outputLine("    expectedOutcome => '" + generateExpectedOutcomeValue(expectedOutcome) + "'" + argEnd + ");");
	    
	    // Create StfProcess objects to represent each process that will be started
	    StfProcess processes[] = new StfProcess[numInstances];
	    for (int i=0; i<numInstances; i++) {
	    	// Work out the mnemonic perl will use for the current instance, eg CL1, CL2, CL3, etc
	    	String currentMnemonic;
	    	if (numInstances == 1) {
	    		currentMnemonic = mnemonic;
	    	} else {
	    		currentMnemonic = mnemonic + (i+1); 
	    	}
	    	processes[i] = new StfProcess(currentMnemonic, processVariable, expectedOutcome, stderr, stdout);
	    }
	    
	    return processes;
	}
	/**
	 * This method takes a String, finds any references to Perl processes inside that string, and 
	 * substitutes those references with the relevant Perl code.
	 * 
	 * E.g. The CommandDetails.relatedProcesses object could tell us that "substitute_this_string" 
	 * is linked to process1, and CommandDetails.relatedProcessesData could tell us that 
	 * "substitute_this_string" is linked to PERL_PROCESS_DATA's index for the perl code "->{pid}".
	 * 
	 * So "'java TestClass substitute_this_string debug'" becomes "'java RunThis ' + $process1->{pid} + ' debug'"
	 * 
	 * This allows us to get data about another process at runtime, and use it (e.g.) as an argument to a second process.
	 * 
	 * @param inputString         The string which may contain perl variable references.
	 * @param delimiter           If the inputString is surrounded by (e.g.) single quotes, we need to know what symbol 
	 *                            we're escaping and resuming after replacing the substitute string with perl code.
	 *                            Leave blank to escape nothing.
	 * @param CommandDetails      The object that tells us what substitution strings to replace with what perl code.
	 * @return String             The processed String, where each process reference is replaced with Perl code
	 */
	private String replaceKeysWithValues(String inputString, String delimiter, CommandDetails data) {
		String outputString = inputString;
		//Now we replace instances of any substitution string with a data request from the associated process.
		for(Map.Entry<String, StfProcess> processPair : data.getRelatedProcesses().entrySet()) {
			if(outputString.contains(processPair.getKey())) {
				//The next three lines turn our StfProcess object into the Perl variable name.
				ArrayList<StfProcess> tempArray = new ArrayList<StfProcess>();
				tempArray.add(processPair.getValue());
				StringBuilder processVariable = createProcessReferenceString(tempArray);
				//This next line should result in a variable name in perl, followed by the suffix in the PERL_PROCESS_DATA HashMap 
				//that corresponds to the value associated with the key linked to this data request.
				String dataRetrievalPerl = processVariable.toString().substring(1) + PERL_PROCESS_DATA.get(data.getRelatedProcessesData().get(processPair.getKey()));
				if(delimiter.isEmpty()) {
					outputString = outputString.replaceAll(processPair.getKey(), Matcher.quoteReplacement("$") + dataRetrievalPerl);
				} else {
					outputString = outputString.replaceAll(processPair.getKey(), delimiter + " . " + Matcher.quoteReplacement("$") + dataRetrievalPerl + " . " + delimiter);
				}
			}
		}
		return outputString;
	}
	
	
	/*
	 * Generates the perl value for an expected outcome argument.
	 * Essential translates between the contents of the ExpectedOutcome object and 
	 * its equivalent representation in perl code.
	 */
	private String generateExpectedOutcomeValue(ExpectedOutcome expectedOutcome) {
		switch (expectedOutcome.getExpectedOutcome()) {
			case CLEAN_RUN:     return "exitValue:0";
			case NON_ZERO_EXIT: return "exitValue:" + expectedOutcome.getExpectedExitValue();
			case NEVER:         return "never";
			case CRASHES:       return "crashes";
		}
		
		throw new IllegalStateException("Internal Error. Missing outcome state");
	}

	
	/* Examines the arguments for a command to determine if the contents of the arguments
	 * mean that the command needs to be invoked with arguments that are passed in as 
	 * an array. 
	 * @return true if an array (instead of a single argument string) is required.
	 */
	private boolean needArrayBasedArgs(CommandDetails command) {
		// Look at the contents of all args to decide which variant of command generation 
		// will be used. 
		// We prefer to generate the command as a big string, as this is more readable.
		// However if any of the args contain a double-quote or a space we need to fall back to 
		// passing in the args as elements in an array. This is foolproof but not so readable.
		boolean foundFunnyChar = false;
		for (String arg : command.args) {
			foundFunnyChar |= arg.contains(" ") || arg.contains("\"");
		}

		return foundFunnyChar;
	}
	
	
	/**
	 * Builds an object representing a command formatted in different ways.
	 * 1. A string formatted for readability
	 * 2. An unformatted string
	 * 3. TODO.  The command as a string and its arguments as an array
	 * 
	 * Formatting the command makes a huge difference to its readability in the 
	 * generated perl script.
	 *
	 * @param mnemonic is the mnemonic for this command.
	 * @param numInstances is the number of process instances to start with the same configuration.
	 * @param argumentComment is an optional value for describing something about the command 
	 * arguments. Mostly used to say what the random mode seed is set to.
	 * @param executable is the name of the program to run.
	 * @param args is a list of strings for the arguments to the command.
	 * @return a result object containing the command.
	 * @throws StfException
	 */
	public CommandDetails buildCommand(String mnemonic, int numInstances, String argumentComment, String executable, String... argsValues) throws StfException {
		// Now that we are about to generate the perl code we can replace any values
		// with the known command number or mnemonic.
		// Values for ${{STF-PROCESS-INSTANCE}} need to be done in perl land.
		String[] updatedArgs = new String[argsValues.length];
		for (int i=0; i<argsValues.length; i++) {
			String arg = argsValues[i];
			arg = arg.replace(PLACEHOLDER_STF_COMMAND_NUMBER, Integer.toString(commandSerialNum));
			arg = arg.replace(PLACEHOLDER_STF_COMMAND_MNEMONIC, mnemonic);
			if (numInstances == 1) {
				// For single instances there is no point in letting perl replace the process
				// instance placeholder. May as well remove it now to keep the generated code clean.
				arg = arg.replace(StfConstants.PLACEHOLDER_STF_PROCESS_INSTANCE, "");
			}
			updatedArgs[i] = arg;
		}
		
		// Build object to return results in
		CommandDetails commandDetails = new CommandDetails();
		commandDetails.commandSerialNum = commandSerialNum;
		commandDetails.executableName = executable;
		commandDetails.args = updatedArgs;
		commandDetails.argumentComment = argumentComment;
		
		return commandDetails;
	}
	
	
	/**
	 * Builds a formatted JVM command.
	 * This method can generate perl code to run any of the programs in the JVM bin directory.
	 * 
	 * @param mnemonic is the mnemonic for this command.
	 * @param numInstances is the number of process instances to start with the same configuration.
	 * @param processDetails holds information about the process that is to be run.
	 * @return a result object containing the java command.
	 * @throws StfException
	 */
	public CommandDetails buildJvmCommand(String mnemonic, int numInstances, ProcessDefinition processDetails) throws StfException {
		JavaVersion jvm = processDetails.getJavaVersion();

		ArrayList<String> commandArgs = new ArrayList<String>();
		String argumentComment = null;
		
		// Sometimes the test needs JVM args which are the very first arguments. eg modes for shared classes test
		String command = processDetails.getCommand();
		if (command.endsWith("java") && environmentCore.getStage() == Stage.EXECUTE) {
			// Add any such values as the first arguments, so that they can be overriden
			Argument java_args_initial = getExecuteInitialArgument(jvm);
			String initialJvmOptions = environmentCore.getProperty(java_args_initial);
			ArrayList<String> jvmOptionArgs = StringSplitter.splitArguments(initialJvmOptions);
			commandArgs.addAll(jvmOptionArgs);
			
			if (initialJvmOptions != null && !initialJvmOptions.isEmpty()) {
				// If there are some initial java args then we need to pull in a comment about the arguments.
				// The comment probably says what random mode seed is being used, but need to grab 
				// at this point so that it can be output in execute.pl
				Argument java_args_comment = getExecuteCommentArgument(jvm);
				argumentComment = environmentCore.getProperty(java_args_comment);
			}
		}
		
		// Add in the arguments that the test plugin wants
		String[] argStrings = processDetails.asArgsArray().toArray(new String[0]);
		commandArgs.addAll(Arrays.asList(argStrings));
		
		// If we are about to run java then add in the current set of jvm options
		if (command.endsWith("java")) {
			// JVM options need to go in just before the classpath (so that they take precedence)
			// Firstly work out the index at which they need to be inserted.
			int insertPosition = -1;
			for (int i=0; i<commandArgs.size(); i++) {
				if (commandArgs.get(i).startsWith("-classpath") || commandArgs.get(i).startsWith("-jar")) {
					insertPosition = i;
					break;
				}
			}
			// Insert jvm options at the correct point
			String jvmOptions = getBaseJvmOptions(jvm);
			if (jvmOptions != null) {
				ArrayList<String> jvmOptionArgs = StringSplitter.splitArguments(jvmOptions);
				if (insertPosition == -1) {
					commandArgs.addAll(jvmOptionArgs);  // No classpath! Add at the end of the command
				} else {
					commandArgs.addAll(insertPosition, jvmOptionArgs);
				}
			}
		}
		
		return buildCommand(mnemonic, numInstances, argumentComment, command, commandArgs.toArray(new String[0]));
	}

	private Argument getExecuteInitialArgument(JavaVersion jvm) { 
		if (jvm.isPrimaryJvm()) {
			return Stf.ARG_JAVA_ARGS_EXECUTE_INITIAL;
		} else {
			return Stf.ARG_JAVA_ARGS_EXECUTE_SECONDARY_INITIAL;
		}
	}
	
	private Argument getExecuteCommentArgument(JavaVersion jvm) { 
		if (jvm.isPrimaryJvm()) {
			return Stf.ARG_JAVA_ARGS_EXECUTE_COMMENT;
		} else {
			return Stf.ARG_JAVA_ARGS_EXECUTE_SECONDARY_COMMENT;
		}
	}

	/**
	 * Returns the lowest level of JVM options needed when running a java process.
	 * The actual value return depends on the stage which is currently executing.
	 * 
	 * @return String containing the java options to be used.
	 */
	public String getBaseJvmOptions(JavaVersion jvm) throws StfException {
		if (environmentCore.getStage() == Stage.INITIALISATION) {
			throw new StfException("Can't run java process in the initialisation stage");
		}
		
		Argument baseJvmOptionsArgument = environmentCore.getStage().getJavaArg(jvm);
		String baseJvmOptions = environmentCore.getProperty(baseJvmOptionsArgument);
		return baseJvmOptions;
	}


	/**
	 * This method should be called before the generation of a new command.
	 * It outputs a comment block and increments the command number.
	 * 
	 * @param comment is a comment from the test case describing why the the command is being run.
	 * @param commandName is a short name describing the command that is going to be executed.
	 * @param commandSummary is a brief description about what the command does.
	 * @param commentary holds pair of name value strings describing the main arg/value pairs.
	 * @returns the serial number of the new command.
	 * @throws StfException
	 */
	public int startNewCommand(String comment, String commandName, String commandSummary, String... commentary) throws StfException {
		commandSerialNum++;
		
		// Add a summary of this command into the generated script
		outputEmptyLine();
		outputEmptyLine();
		outputLine("#");
		outputLine("# Step   : " + commandSerialNum);
		outputLine("# Command: " + commandName);
		outputLine("# Comment: " + comment);
		outputLine("#");
		
		// Find longest commentary name
		if (commentary.length % 2 != 0) {
			throw new IllegalStateException("Number commentary strings not even");
		}
		int longestName = 0;
		for (int i=0; i<commentary.length/2; i++) {
			String name = commentary[i*2];
			longestName = Math.max(longestName, name.length());
		}

		// Output some lines describing the command and its arguments.
		outputLine("info('');");
		outputLine("info('+------ Step " + commandSerialNum + " - " + comment + "');");
		outputLine("info('| " + commandSummary + "');");
		// Output all commentary name/value pairs
		for (int i=0; i<commentary.length/2; i++) {  // step through each pair
			String name = commentary[i*2];
			String value = commentary[(i*2)+1];
			String paddedName = String.format("%1$-" + longestName + "s", name);
			outputLine("info('|   " + paddedName + " " + value + "');");
		}
		outputLine("info('|');");
		outputLine("");

		// Remember details of this command for the command summary table.
		// This is written near the start of the STF output once generation has completed.
		String summary = String.format("   %2d  %-" + longestStageName + "s %-17s %s", commandSerialNum, methodName, commandName, comment);
		commandSummaryLog.add(summary);
		
		return commandSerialNum;
	}

	
	/**
	 * Verifies that a boolean condition is true.
	 * 
	 * @param condition is the value being checked.
	 * @param comment is used as the exception test if 'condition' is not true.
	 * @throws StfException if 'condition' is not equal to true.
	 */
	public void verify(boolean condition, String comment) throws StfException {
		if (condition != true) { 
			throw new StfException(comment);
		}
	}


	/**
	 * Adds a block of perl which fails the test if a perl condition is true.
	 * 
	 * @param command is a short description of the command for which code is being generated.
	 * @param comment is a description from the test plugin describing why the current command is being run.
	 * @param resultVariable is the perl variable whose result is being checked.
	 * @param operator is the comparison operator used to compare the resultVariable with the successValue. 
	 * @param resultValue contains the value to compare resultVariable to.
	 * @param runningProcesses is a list of processes which could still be running, and need to be killed before the script dies.
	 * @throws StfException if we fail to write to the output file.
	 */
	public void outputFailIfTrue(String command, String comment, String resultVariable, String operator, String resultValue, ArrayList<String> extraErrorLines, ArrayList<StfProcess> runningProcesses) throws StfException {
		// Build the if statement and error message, eg 'if ($rc != 0) { ...'
		outputLine("if (" + resultVariable + " " + operator + " " + resultValue +  ") {");
		increaseIndentation();

		// Output some optional failure lines from the caller 
		if (extraErrorLines != null) {
			for (String line : extraErrorLines) { 
				outputLine(line);
			}
		}
		
		// Generate perl to kill any running processes and exit the test 
		String errorMessage = StfConstants.FAILURE_PREFIX 
				+ "at " + describeCommand(command, comment) + ". "
				+ "Expected return value=" + resultValue + " "
				+ "Actual=" + resultVariable;
		outputDieCommand(runningProcesses, errorMessage);
		
		decreaseIndentation();
		outputLine("}");
	}


	/**
	 * Generates the code needed to terminate the running perl script.
	 * Generally only needed to handle error paths. 
	 * @param runningProcesses is an array list containing all processes which could 
	 * still be running, and will therefore be killed.
	 * @param dieMessage is the message to be used for the die call.
	 */
	public void outputDieCommand(ArrayList<StfProcess> runningProcesses, String dieMessage) throws StfException {
		// Terminate all child processes
		outputKillProcesses(runningProcesses, "");

		// Now the real reason for this method. Terminate the perl process.
		outputLine("die \"" + dieMessage + "\";");
	}
	
	
	/**
	 * Adds a block of perl code which scans a log file counting the number of times that a 
	 * specified string matches.
	 * It passes no judgement about whether or not the resulting count is good or bad, that is the
	 * job of subsequent call to outputFailIfTrue().
	 * 
	 * It does this by creating a while loop which opens a log and does a regex match on each line.
	 * Every match causes the resultVariable to be incremented.
	 * 
	 * @param resultVariable is the perl variable where the result of the scan is set. eg '$error_count'
	 * @param log is the log file whose output is scanned.
	 * @param runningProcesses is a list of processes which could still be running, and need to be killed before the script dies.
	 * @param expectedResultMessages contains the message/s that the log file is expected to contain.
	 * @throws StfException if anything goes wrong.
	 */
	public void outputCountFileMatches(String countVariable, FileRef log, ArrayList<StfProcess> runningProcesses, String... expectedResultMessages) throws StfException {
		// Build the if statement arguments
		StringBuilder ifArgs = new StringBuilder();
		
		// Ensure that supports the checking of multiple messages
		boolean multipleResultMessageValues = false;
		for (String expectedResultMessage : expectedResultMessages) {
			if (multipleResultMessageValues) {
				ifArgs.append(" || ");
			}
			ifArgs.append("$line =~ '" + expectedResultMessage + "'");
			multipleResultMessageValues = true;
		}
		
		// Build the perl code that loops through the log and does the regex matching
		outputLine("unless (open(LOG, " + "'" + log + "' )) {");
		increaseIndentation();
		outputDieCommand(runningProcesses, "Could not open log");
		decreaseIndentation();
		outputLine("}");	
		outputEmptyLine();
		
		outputVariableInitialisation(countVariable, "0");
		outputLine("while (my $line = <LOG>) {"); 
		outputLine("    if (" + ifArgs + ") {");
		outputLine("        " + countVariable + "++;");
		outputLine("    }");
		outputLine("}");
		outputLine("close(LOG);");
	}
	
	
	/**
	 * Generates perl code to echo the contents of a file to standard output.
	 * @param targetFile points to the file to be read
	 * @param runningProcesses are the processes that need to be killed if the file cannot be opened.
	 */
	public void outputEchoFile(FileRef targetFile, ArrayList<StfProcess> runningProcesses) throws StfException {
		// Open the target file
		outputLine("unless (open(LOG, " + "'" + targetFile + "' )) {");
		increaseIndentation();
		outputDieCommand(runningProcesses, "Failed to open file for contents echoing at: " + targetFile);
		decreaseIndentation();
		outputLine("}");	
		outputEmptyLine();

		// Read and echo every line of target file
		outputLine("info('Echoing contents of file " + targetFile + "');");
		outputLine("info('>>>>>>');");
		
		outputLine("while (my $line = <LOG>) {"); 
		outputLine("    print($line);");
		outputLine("}");
		outputLine("close(LOG);");
		
		outputLine("info('<<<<<<');");
		outputLine("info('Ending echo for file " + targetFile + "');");
	}
	
	
	
	/**
	 * This method increases the indentation depth 
	 */
	public void increaseIndentation() {
		indentationDepth++;
	}
	
	
	/**
	 * This method decreases the indentation depth
	 */
	public void decreaseIndentation() {
		indentationDepth--;
	}
	

	/**
	 * This method gets the indentation depth and returns the indentation string
	 * 
	 * @return the indentation string 
	 */
	private String getIndentation() {
		StringBuilder indentationArgs = new StringBuilder();
		
		for (int indentationLevel=0; indentationLevel<indentationDepth; indentationLevel++) {
			indentationArgs.append("    ");
		}
		
		return indentationArgs.toString();
	}
	
	
	/**
	 * Adds a block of perl which makes sure that a perl variable is set to '0'.
	 * If the perl variable has a different value then an error message is produced and the 
	 * process aborts.
	 * 
	 * @param command is a short description of the command for which code is being generated.
	 * @param comment is a description from the test plugin describing why the current command is being run.
	 * @param resultVariable is the perl variable whose result is being checked.
	 * @param runningProcesses is a list of processes which could still be running, and need to be killed before the script dies.
	 * @throws StfException if 'resultVariable' is not a valid perl variable name or if we fail to write to the output file.
	 */
	public void outputErrorCheck(String command, String comment, String resultVariable, ArrayList<StfProcess> runningProcesses) throws StfException {
		if (!resultVariable.startsWith("@")) {
			throw new StfException("Variable must be an array");
		}
		
		outputLine("if ( defined (" + resultVariable + "[0] ) ) {");
		increaseIndentation();
		outputLine("stf::stfUtility->listErrors( " + resultVariable + ");");
		outputDieCommand(runningProcesses, StfConstants.FAILURE_PREFIX + "at " + describeCommand(command, comment));
		decreaseIndentation();
		outputLine("}");
    }
	

	public String describeCommand(String command, String comment) { 
		return "step " + PerlCodeGenerator.commandSerialNum + " (" + comment + ")";
	}

	
	/**
	 * Adds a line of perl code to the output file. 
	 * The code is prefixed with indentation, which can be set by the caller 
	 * using the increaseIndentation() and decreaseIndentation() public methods.
	 * The caller is responsible for making sure it is valid perl code and the indentation level is correct.
	 * 
	 * @param perlCode is a String containing the perl code to write.
	 * @throws StfException if there is an IO exception when writing to the file.
	 */
	public void outputLine(String perlCode) throws StfException {
		if (stage == Stage.INITIALISATION) {
			throw new StfException("perl code generation 'do' methods cannot be used during the initialisation stage");
		}
		
		try {
			perlFile.write(getIndentation() + perlCode + "\n");
		} catch (IOException e) {
			throw new StfException("Failed to write to perl output: " + outputFile, e);
		}
	}

	
	public void outputEmptyLine() throws StfException {
		outputLine("");
	}


	/**
	 * When the generation of the perl script is complete then the STF framework will call this 
	 * method to close off the output file.
	 * 
	 * @doExitCommand set to true if a perl exit command needs to be generated.
	 * @throws StfException if there is an IO exception.
	 */
	public void closeOutput(boolean doExitCommand) throws StfException {
		if (stage == Stage.INITIALISATION) {
			return;
		}

		if (doExitCommand) {
			outputEmptyLine();
			outputEmptyLine();
			outputLine("info('" + environmentCore.getStage() + " stage completed');");
			outputLine("exit 0;");
		}
		
		try {
			perlFile.close();
		} catch (IOException e) {
			throw new StfException("Failed to close perl output: " + outputFile, e);
		}
	}
	
	
	public void summariseGeneratedCommands() {
		for (String commandSummary : commandSummaryLog) {
			logger.info(commandSummary);
		}
	}
}