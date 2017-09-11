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

package net.adoptopenjdk.stf.extension;

import java.util.ArrayList;

import net.adoptopenjdk.stf.StfException;
import net.adoptopenjdk.stf.codeGeneration.PerlCodeGenerator;
import net.adoptopenjdk.stf.environment.DirectoryRef;
import net.adoptopenjdk.stf.environment.FileRef;
import net.adoptopenjdk.stf.environment.StfEnvironmentCore;
import net.adoptopenjdk.stf.environment.properties.Argument;
import net.adoptopenjdk.stf.environment.properties.Argument.Required;
import net.adoptopenjdk.stf.extensions.StfExtensionBase;
import net.adoptopenjdk.stf.extensions.interfaces.StfExtension;
import net.adoptopenjdk.stf.runner.modes.HelpTextGenerator;


/**
 * This simple extension demonstrates how to add a custom extension.
 * See SampleCustomExtensionTest.java for more details.
 */
public class ExampleExtension implements StfExtension {
    private StfEnvironmentCore environmentCore;
    private StfExtensionBase extensionBase;
    private PerlCodeGenerator generator;
    
    private static boolean haveDeclaredVariables = false;
    
    
    // Arguments for this extension.
    // Default value set in config file, which can be overridden on the command line.
	//                        Java name                     extension Argument   name           Boolean            Type
	public static Argument ARG_SHOW_FILES    = new Argument("exampleExtension", "show-files",    true,   Required.OPTIONAL); 
    
	@Override
	public Argument[] getSupportedArguments() {
		return new Argument[] { 
			ARG_SHOW_FILES
		};
	}
	
	
	@Override
	public void help(HelpTextGenerator help) {
		help.outputSection("Example extension options");
		
		help.outputArgName("-" + ARG_SHOW_FILES.getName());
		help.outputArgDesc("If set then test data files are written to stdout.");
	}

	
	@Override
	public void initialise(StfEnvironmentCore environmentCore, StfExtensionBase extensionBase, PerlCodeGenerator generator) throws StfException {
		this.environmentCore = environmentCore;
		this.extensionBase = extensionBase;
		this.generator = generator;	
	}
	
	
	/**
	 * This action uses perl code to validate the contents of a GC log file. 
	 */
	public void doVerifyGcLog(String comment, FileRef gcResultsFile) throws StfException {
		invokeParsingSubroutine(comment, "GC", "checkGCData", gcResultsFile);
	}


	/**
	 * This action uses perl code to validate the contents of a GC log file.
	 */
	public void doVerifyHeapLog(String comment, FileRef heapResultsFile) throws StfException {
		invokeParsingSubroutine(comment, "Heap", "checkHeapData", heapResultsFile);
	}


	/**
	 * The parsing methods have basically the same logic, so this method generates perl 
	 * to invoke the named method.
	 * @param comment describes why the test is calling the action.
	 * @param dataType short name describing the data type whose file is being parsed.
	 * @param subroutineName is the method in ExampleModule.pm to run.
	 * @param dataFile is the file to be parsed.
	 * @throws StfException if code generation fails.
	 */
	private void invokeParsingSubroutine(String comment, String dataType, String subroutineName, FileRef dataFile) throws StfException {
		generator.startNewCommand(comment, "perl", "Run perl method to validate " + dataType + " log",
				dataType + "File:", dataFile.getSpec());
		
		boolean showFiles = environmentCore.getBooleanProperty(ARG_SHOW_FILES);
		if (showFiles) {
			// Output some perl to dump the contents of the test file
			generator.outputCreateVariable("$outFile");
			generator.outputCreateVariable("$line");
			generator.outputLine("print \"Contents of file " + dataFile.getSpec() + ":\\n\";");
			generator.outputLine("print \">>>\\n\";");
			generator.outputLine("open $outFile, '<', \"" + dataFile.getSpec() + "\" or die \"Unable to open file for reading : $!\";");
			generator.outputLine("while ( $line = <$outFile> ) {");
			generator.outputLine("    print \"$line\";");
			generator.outputLine("}");
			generator.outputLine("close $outFile;");
			generator.outputLine("print \"<<<\\n\";");
			generator.outputLine("");
		}    
	
		DirectoryRef scriptDir = environmentCore.findTestDirectory("stf.samples/scripts");
		
		// Generate code to call the subroutine in the perl module
		String variablePrefix = haveDeclaredVariables ? "" : "my ";  // Only create return variables on the first call
		haveDeclaredVariables = true;
		generator.outputLine("use lib \"" + scriptDir.getSpec() + "\";");
		generator.outputLine("use ExampleModule;");
		generator.outputLine(variablePrefix  + "($errors, $errorCount) = ExampleModule->" + subroutineName + "(\"" + dataFile + "\");");
		
		// Build some perl code to be used in the event of a failure.
		// It lists all of the errors returned by the parsing subroutine.
		ArrayList<String> onErrorPerl = new ArrayList<String>();
		onErrorPerl.add("# List parsing errors and end test");
		onErrorPerl.add("print \"$errorCount error(s) found when parsing file:\\n\";");
		onErrorPerl.add("for my $i (0 .. $errorCount-1) {");
		onErrorPerl.add("    print \"  \" . ($i+1) . \") @$errors[$i]\\n\";");
		onErrorPerl.add("}");

		// Output an if statement that fails the test if the number of parsing errors doesn't meet expectations.
		extensionBase.outputFailIfTrue("perl", comment, "$errorCount", "!=", "0", onErrorPerl);
	}
}