#!/usr/bin/perl

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Script: stf.pl
# Automation script for the System Test Framework.
#
#

# import modules
use strict;
use FindBin qw($Bin);
use lib "$Bin";

# standard perl modules
use File::Path qw(mkpath rmtree);
use File::Basename;

use stf::stfUtility;
use stf::Constants qw(:all);
use stf::Commands qw(:all);
use stfArguments;

use Cwd 'abs_path';

# This constant controls the number of old results directories that are retained
my $results_retention_number = 10;
# STF fails if the number of results directories exceeds this value
my $results_retention_limit = 20;


output_banner("STF");

$ENV{'loggingLevel'} = "WARN";

# Set a platform specific default value for a temp directory.
# This value is used for results-root, unless an alternative directory is specified on the command line. 
$ENV{'STF_TEMP'} = '/tmp/stf';
if ( $^O eq 'MSWin32' ) {
	$ENV{'STF_TEMP'} = 'C:\stf_temp';
}

# Locate the users optional STF property file
my $home_dir = stfArguments::get_home_dir();
my $stf_personal_properties = $home_dir . "/.stf.properties";
if (!-e $stf_personal_properties) {
    $stf_personal_properties = "";
}

# Locate the properties for this customisation of STF
# Note: directory name is passed into abs_path to workaround old perl bug.
my $stf_defaults = abs_path($Bin . "/../config") . "/stf.properties";

# Tell STF argument handling about the property files
stfArguments::set_argument_data($stf_personal_properties, $stf_defaults);

# Get the value for -systemtest-prereqs which may have been overridden on the command line, and validate it.
my $prereqs_root = stfArguments::get_argument("systemtest-prereqs");
# The existence of this directory inside one (or more) of the prereq roots proves their validity.
my $prereqs_root_validator = "/junit";

# Here we ensure the systemtest-prereqs environment variable is equal to the command line option.
# This way any test code can use whichever is the most convenient.
# If systemtest-prereqs is not specified, we find the systemtest_prereqs folder by stepping up stf.pl's path.
#
# E.g. If stf.pl is in /tmp/git/stf/stf.core/scripts, then we evaluate these (in order) as potential prereqs directories:
# /tmp/git/stf/stf.core/scripts/<dir>
# /tmp/git/stf/stf.core/<dir>
# /tmp/git/stf/<dir>
# /tmp/git/<dir>
# /tmp/<dir>
# or 
# /<dir>
#
# Where <dir> is either systemtest_prereqs or prereq/(ascii|ebcdic)/systemtest_prereqs. The latter allows us to store both the 
# ascii and ebcdic versions of our systemtest_prereqs under a single parent folder, selecting one or the other at runtime.
#
# If that fails, we default to [home directory]/systemtest_prereqs.

if($prereqs_root eq "null") {
	if (defined $ENV{'SYSTEMTEST_PREREQS_ROOT'}) {
		#If we have an environment variable, use that.
		$prereqs_root = $ENV{'SYSTEMTEST_PREREQS_ROOT'};
	} else {
		# If systemtest-prereqs was not supplied at all, we search for it up the path.
		my $prereqs_search = Cwd::abs_path($0);
		my $search_finished = "no";
		my $asciiOrEbcdic = "ascii";
		if (stf::stfUtility::getPlatform() eq "zos") {
			$asciiOrEbcdic = "ebcdic";
		}
		print "systemtest-prereqs not specified. Attempting to locate along the current working directory path.\n";
		while (!($search_finished eq "yes")) {
			if (-d "$prereqs_search/systemtest_prereqs$prereqs_root_validator") {
				$prereqs_root = $prereqs_search . "/systemtest_prereqs";
				$search_finished = "yes";
			} elsif(-d ($prereqs_search . "/prereqs/$asciiOrEbcdic/systemtest_prereqs$prereqs_root_validator")) {
				$prereqs_root = $prereqs_search . "/prereqs/$asciiOrEbcdic/systemtest_prereqs";
				$search_finished = "yes";
			} elsif(($prereqs_search =~ /^[A-Za-z]\:\\*$/) or ($prereqs_search =~ /^\/*$/)) {
				print "Unable to find systemtest-prereqs along the current working directory path.\n";
				$prereqs_root = stfArguments::get_home_dir() . "/systemtest_prereqs";
				print "Defaulting to [home_directory]/systemtest_prereqs: " . $prereqs_root . "\n";
				$search_finished = "yes";
			} else {
				# Go up a step and try again.
				my @prereqs_search_array = File::Spec->splitdir($prereqs_search);
				pop @prereqs_search_array;
				$prereqs_search = File::Spec->catdir(@prereqs_search_array);
			}
		}
		if (!($prereqs_root eq (stfArguments::get_home_dir() . "/systemtest_prereqs"))) {
			print "systemtest-prereqs was found here: " . $prereqs_root . "\n";
		}
		$ENV{'SYSTEMTEST_PREREQS_ROOT'} = $prereqs_root;
	}
}

#Now we validate each of the systemtest-prereqs we have, and make their directories absolute in case we change directory.
my $updated_systemtest_prereqs = make_paths_absolute("systemtest-prereqs",$prereqs_root,$prereqs_root_validator);

print "systemtest-prereqs has been processed, and set to: " . $updated_systemtest_prereqs;

my $test_name = stfArguments::get_argument("test");
my $help_arg = stfArguments::get_argument("help");
my $test_list_arg = stfArguments::get_argument("list");

# If help has been requested, but there is not enough information to run up STF java
# code, then explain that some minimal arguments need to be set.
if (stfArguments::get_argument("help") ne 'false') {
    if ($test_name eq 'null') {
        print "To get STF help please run again with a '-test' argument.\n";
        print "This will allow STF to produce help on the extensions used by that test case.\n";
        print "Extra options specific to this perl script.\n";
        print "   -retain=nnn        Set the number of output directories to keep.\n";
        print "   -retain-limit=nnn  Set the maximum number of failed output\n";
        print "                      directories before a failure condition is declared\n";
        exit 0;
    }
}

# Make sure that a test name has been specified
if ($test_name eq 'null' && $test_list_arg eq 'false') {
    print "** ERROR **  No test name specified. \(Use '-test' argument. eg, '-test=xyz', or '-list' to find all tests\)\n";
    exit 0;
}   
if ($test_list_arg ne 'false') {
    # STF has been run with something like 'stf -list', so force the name of the test to 'list'
    $test_name = "list";
}


# Create a timestamp for the results directories for this run
(my $sec,my $min,my $hour,my $mday,my $mon,my $year,my $wday, my $yday, my $isdst) = localtime();
my $timestamp = sprintf '%04d%02d%02d-%02d%02d%02d', $year+1900, $mon+1, $mday, $hour, $min, $sec;

# Work out where the working, temp and results directories are going
my $results_root  = stfArguments::get_and_check_argument("results-root");
my $retain_number = stfArguments::get_and_check_argument("retain");
my $retain_limit  = stfArguments::get_and_check_argument("retain-limit");
my $test_dir = $results_root . "/$timestamp-$test_name";
my $debug_dir   = $test_dir . "/debug";
my $results_dir = $test_dir . "/results";
my $generation_dir = $test_dir . "/generation";
my $setup_dir      = $test_dir . "/setUp";
my $execute_dir    = $test_dir . "/execute";
my $teardown_dir   = $test_dir . "/tearDown";
my $delete_results_root = $FALSE;

my @results_dirs;

if ( length $retain_limit ) {
   $results_retention_limit = $retain_limit;
}

if ( length $retain_number ) {
   $results_retention_number = $retain_number;
   # Increase results_retention_limit in line, otherwise failures are likely
   # User can override this with the retain-limit option if the wish to
   if ( $results_retention_number > $results_retention_limit ) {
      print "WARNING: Overriding retain-limit to retain + 1\n";
      $results_retention_limit = $results_retention_number+1;
   }
}

# We are only going to remove results_root if it passes some basic checks.
# So check its structure to verify it contains STF artifacts.
if (-e $results_root) {
	my @files = <'$results_root/*'>;
	my $count = @files;
    if ($count == 0) { 
        # No child directories yet, so structure is good
        $delete_results_root = $TRUE;
    } else {
        # Found one or more child files/directories.
        # Check that they are all STF directories.
        foreach my $suspect_file (@files) {
	        if (-d $suspect_file) {
		    	# Check contents of old results directory, if it is not empty
		    	my @subfiles = <'$suspect_file/*'>;
				my $subfiles_count = @subfiles; 
				if ($subfiles_count == 0) {
				    print "Warning: Found empty results directory: $suspect_file\n";
					$delete_results_root = $TRUE;
				} else {
			        # Check the structure of the old test directory, to see if it contains expected STF files 
			        my $setup_dir    = $suspect_file . "/setUp";
			        my $execute_dir  = $suspect_file . "/execute";
			        my $teardown_dir = $suspect_file . "/tearDown";
			        if (-d $setup_dir && -d $execute_dir && -d $teardown_dir) {
			            $delete_results_root = $TRUE;
			        } else {
						print "**FAILED** results directory '$suspect_file' does not contain expected setUp, execute and tearDown directories\n";
						$delete_results_root = $FALSE;
						last;
			        }
				}
				
				# Keep track of all result directories (allows deletion of oldest)
				if (! -l $suspect_file) {
					push @results_dirs, $suspect_file;
				}
			} else {
				print "**FAILED** unexpected file found at '$suspect_file'. Results-root at '$results_root' is not a STF results directory\n";
				$delete_results_root = $FALSE;
				last;
			}
		}
    }
} else { # results-root does not exist
    $delete_results_root = $TRUE;
}


# Abort the run if it is not safe to delete the results-root directory 
if ($delete_results_root == $FALSE) {
    # List files in the alleged STF directory to allow debugging
    print "Files/Directories at results root:\n";
    print "  " . describe_file($results_root) . " " . "$results_root\n";
	my @files = <'$results_root/*'>;
    foreach my $file (@files) {
        print "    " . describe_file($file) . " " . "$file\n";
        if (-d $file) {
            my @child_files = <'$file/*'>;
            foreach my $child_file (@child_files) {
                print "      " . describe_file($child_file) . " " . "$child_file\n";
            }
        }
    }
    
	# We don't want to trash a box just because of an incorrect argument, so abort the run
    print "Abort: Can't proceed as the results-root at '$results_root' does not appear to be a valid STF results directory\n"; 
    exit 1;
}


## Remove old STF results directories.
## First move to the same directory as this script, just in case the current working 
## directory is inside the results tree (which would cause the rmtree to fail)
my $num_to_delete = $#results_dirs -$results_retention_number;
if ($num_to_delete >= 0) {
    my $current_script_path = Cwd::abs_path($0);
    chdir dirname($current_script_path) or die "Failed to chdir to the stf.pl script directory $current_script_path\n";
    my @results_to_delete = @results_dirs[0..$num_to_delete];
    foreach my $results_root (@results_to_delete) {
        _log("Deleting old STF results: $results_root");
        # Some files may be longer than the Windows MAX_PATH value - e.g. the JCK run creates files
        # longer than that in the results directory. rmtree cannot delete those files, so use rmtree
        # instead.
        deleteDirectory($results_root);
    }
}

# Find out if the current machine supports symbolic links
my $symlink_supported = eval { symlink("",""); 1 };

# Now remove any orphaned symbolic links
if (defined $symlink_supported && $symlink_supported eq 1) {
	my @results_dir_content = <'$results_root/*'>;
    foreach my $file (@results_dir_content) {
        # Check to see if the current file is a link which has no directory
    	if (-l $file && ! -d $file) {
    	    _log("Deleting orphan results link: $file");
    	    unlink $file;
    	}
    }
}

# To allow for easier running on a Windows machine we may have ignored the failure to delete
# a results directory. 
# We can tolerate a having a few directories directories which are not deleteable, but once a 
# predefined limit is set we abort the test run.
# Hopefully having this buffer zone, and giving Windows more time to free its handles, will
# allow STF to keep running.  
my $num_result_dirs = 0;
my @files = <'$results_root/*'>;
foreach my $file (@files) {
    if (-d $file && ! -l $file) {
        # Found a directory which is not a symbolic link.
        # See if it contains a timestamp, eg /stf/20160706-092421-list
        if ($file =~ /20[0-9]{6}-[0-9]{6}/) {
        	$num_result_dirs++;
        }  
    }
}
if ($num_result_dirs > $results_retention_limit) {
    print "**FAILED** Too many result directories. Limit is $num_result_dirs. Currently have $num_result_dirs directories at $results_root\n";
    exit 2;
}


# Create directory structure for this run
mkpath($debug_dir);
mkpath($generation_dir);
mkpath($setup_dir);
mkpath($execute_dir);
mkpath($teardown_dir);
mkpath($results_dir);

# On Unix type systems create a symbolic link to the results directory.
# This allows terminals/editors to retain the same file name over multiple executions.
# Firstly find out if enabled (not enabled by default)
my $createResultsSymLinks = stfArguments::get_argument("create-results-sym-links");

# If symbolic links are supported and required then create the link
# eg, /tmp/stf/UtilLoadTest -> /tmp/stf/20160704-115233-UtilLoadTest
if (defined $symlink_supported && $symlink_supported eq 1 && $createResultsSymLinks ne 'false') {
    my $new_link_name = $results_root . "/$test_name";
    unlink $new_link_name; 
	symlink($test_dir, $new_link_name);
}

# On Windows, assign T: to the test directory.
# Some tests (e.g. the JCK) have been known to create very long paths which can exceed the Win32 limit of 260 chars.
# Using a substituted drive letter instead avoids the limit.
#if ($^O eq 'MSWin32') {
#    # subst might not work with forward slashes or escaped backslashes, so remove any that are there.
#    $test_dir =~ s,/,\\,g;
#    $test_dir =~ s,\\\\,\\,g;
#    print "Substituting test_dir $test_dir with T: to avoid long path lengths\n";

#    my $cmd = "subst T: /d";
#    print "Running $cmd\n";
#    my @subst_output = `$cmd 2>&1`;
#    my $rc = $?;
#    print "$cmd returned: $rc\n";
#    if ( $rc != 0 ) {
#        foreach my $line ( @subst_output ) {
#           print "$line";
#        }
#    }

#    $cmd = "subst T: $test_dir";
#    print "Running $cmd\n";
#    @subst_output = `$cmd 2>&1`;
#    $rc = $?;
#    print "$cmd returned: $rc\n";
#    if ( $rc != 0 ) {
#        foreach my $line ( @subst_output ) {
#           print "$line";
#        }
#    }
#    $test_dir = "T:/";
#}


# Check whether we have enough space available. If not inform the user and fail the test, 
# unless we are on z/OS, where may be on a dynamic, growable file system, like ZFS.
my $mb_free = check_free_space ($results_root);
if ( $mb_free < 3072 && stf::stfUtility::getPlatform() ne "zos") {
	print "
Test machine has only $mb_free Mb free on drive containing $results_root.\n
There must be at least 3Gb (3072Mb) free to be sure of capturing diagnostics
files in the event of a test failure.\n
Exiting.\n";
	exit 1;
}


# $now used as a unique id
my ($now, $date, $time) = stf::stfUtility->getNow(date => $TRUE, time => $TRUE);

	output_banner("GENERATION");

    # if any test root starts with '..' then resolve it now, before we change directory
    my $test_root = stfArguments::get_argument("test-root");
	my $updated_test_root = make_paths_absolute("test-root",$test_root,"");
    
    
    # Abort run if any test root contains a space character.
    # (This causes lots of class loading problems on windows)
    if ($test_root =~ / /) {
	    _log("**FAILED** STF cannot use any test root with a space character in its path: $test_root\n");
	    exit 1;
	}
    
    
    # Write the stf arguments to a properties file
    my $stf_parameters = $test_dir . "/stf_parameters.properties";
    stfArguments::write_arguments_to_file $stf_parameters, $Bin, $updated_test_root, $updated_systemtest_prereqs;

	# Move to the output directory
	chdir($generation_dir);

    # Decide if java debug has been enabled
	my $java_debug_settings = "";
    if (stfArguments::get_boolean_argument("debug-generation")) {
        $java_debug_settings = stfArguments::get_argument("java-debug-args");
	}

	# Find the location of java to be used in the generation step
	my $javahome_generation = stfArguments::get_and_check_argument("javahome-generation");
	validate_jvm($javahome_generation, "javahome-generation");
	
    # Build the command to run RunTestRunner - to generate the setup, execute and teardown scripts.
    # The generation step needs a custom class loader so that classes used by the plugin can be loaded.
    my $sep = stf::stfUtility->getPathSeparator;
    my $log4j_core_dir = findElement($prereqs_root, "/log4j/log4j-core.jar");
    my $log4j_api_dir = findElement($prereqs_root, "/log4j/log4j-api.jar");
    my $cmd = "$javahome_generation/bin/java " .
              "$java_debug_settings" .
              " -Dlog4j.skipJansi=true" .  # Suppress warning on Windows
              " -Djava.system.class.loader=net.adoptopenjdk.stf.runner.StfClassLoader" .
              " -classpath $log4j_api_dir" . $sep . "$log4j_core_dir" . $sep . "$Bin/../bin" .
              " net.adoptopenjdk.stf.runner.StfRunner" .
              " -properties \"$stf_parameters, $stf_personal_properties, $stf_defaults\"" .
              " -testDir \"$test_dir\"";

    _log("Starting process to generate scripts: $cmd");
    my ($rc, $process) = stf::Commands->run_process(
          	mnemonic  => "GEN",
            command   => $cmd,
            logName   => "$generation_dir" . "/generation",
            uid       => $now,
            echo      => $TRUE,
            prefix_on => $TRUE,
            runtime   => 900);
    if ($rc != 0) {
        print "Generation failed\n";
        exit 1;
    }

    # If we are just running to provide help or a list of all tests then finish.
    if ($help_arg ne 'false') {
	    exit 0;
	}   
    if ($test_list_arg ne 'false') {
	    exit 0;
	}   
	
	
	_log("");
    _log("Script generation completed");
    _log("");
   
    # Read names of execute scripts to run from text file
    my @executeStages = ();
    my $filename = "$test_dir/executeStages.txt";
    open(my $fh, '<', $filename) or die "Could not open file '$filename' $!";
    while (my $row = <$fh>) {
        chomp $row;
        push(@executeStages, "$row");
    }
    close($fh);
   
    # Build the perl commands needed to run the test
    my $setupCmd    = "perl $test_dir/setUp.pl";
    my $teardownCmd = "perl $test_dir/tearDown.pl";
   
	my $dry_run = stfArguments::get_boolean_argument("dry-run");
	if ($dry_run) {
        _log("*Not* executing scripts, as dryRun mode set");
        _log("Commands to execute scripts are:");
        _log("  $setupCmd");
        foreach (@executeStages) {
            _log("  perl $test_dir/$_.pl");
        }
        _log("  $teardownCmd");
        exit 0;
       
    } else {
        my $rc_setup    = 0;
        my $rc_execute  = 0;
        my @rc_executes = ();
        my $rc_teardown = 0;
          
        $rc_setup = runScript($setupCmd, $setup_dir, "setup");
        # Only run the test itself if the setup was successful
        my $rc_execute_total = 0;
        if ($rc_setup == 0) {
            for my $i (0 .. $#executeStages) {
                my $stageName = $executeStages[$i];
                my $cmd = "perl $test_dir/$stageName.pl";
                my $rc = runScript($cmd, $execute_dir, $stageName);
                push(@rc_executes, $rc);
                $rc_execute_total = $rc_execute_total + $rc;
            }
        }
       
        $rc_teardown = runScript($teardownCmd, $teardown_dir, "teardown");
   
        _log("");
        output_banner("results");
        
        # Find the longest stage name (so that the results can be neatly formatted)
        my $longestStageName = length "teardown";
        for my $i (0 .. $#executeStages) {
            my $currStageLen = length $executeStages[$i];
            if ($currStageLen > $longestStageName) {
                $longestStageName = $currStageLen;
            }
        }
        
        # Report pass/fail for each stage
        _log("Stage results:");
        reportStageResult("setUp", $rc_setup, $longestStageName);
        for my $i (0 .. $#rc_executes) {
            reportStageResult($executeStages[$i], $rc_executes[$i], $longestStageName);
        }
        reportStageResult("teardown", $rc_setup, $longestStageName);

        # Output an overall pass/fail status message
   		my $rc_overall = $rc_setup + $rc_execute_total + $rc_teardown;
   		_log("");
   		if ($rc_overall == 0) {
	        _log("Overall result: PASSED");
	        # If the --rm-pass option was specified, delete the results directory because the test passed.
	        if (stfArguments::get_boolean_argument("rm-pass")) {
	            _log("Deleting the results directory because no failures were detected.");
	            chdir $results_root;
	            deleteDirectory($test_dir);
	        }
	    } else {
	    	$rc_overall = 1;
	        _log("Overall result: **FAILED**");
	    }
	    
	    exit $rc_overall; 
   }


sub describe_file {
    my $file = shift;
    
    if (-f $file) {
        return "f";
    } elsif (-d $file) {
        return "d";
    }
    
    return "?";
}


sub runScript {

    # Runtime for the setup.pl, execute.pl and teardown.pl execution
    # is set to a week (7 days).
    # Tests should be setting timeouts for the processes within each script
    # according to their expectations, but setting a very long timeout here
    # at least stops automation machines being tied up for ever.

    my $script = shift;
    my $results_dir = shift;
    my $script_name = shift;

    _log("");
	output_banner($script_name);
	
    _log("Running $script_name: $script");
    
    my $return_code = 0;
    my ($rc, $process) = stf::Commands->run_process(
          mnemonic	=> "STF",
          command   => $script,
          logName    => "$results_dir" . "/$script_name",
          uid       => $now,
          echo      => $TRUE,
          prefix_on => $FALSE,
          runtime   => 604800);
          
    if ($rc != 0) {
        _log("**FAILED** $script_name script failed. Expected return value=0 Actual=$rc");
        $return_code = $rc;
    }
    
    return $return_code;
}


# This subroutine produces banner text which divide the output into sections.
# It aims to make it easier to quickly visually parse the output.
# We have found that capitalising the stage string and adding extra spaces makes
# the most effective divider.
# For example, given the text of 'executeJunit' it outputs:
#   ==============   E X E C U T E - J U N I T   ==============
#
sub output_banner {
    my $bannerText = shift;
    
    # Make the text more readable for execute methods
    $bannerText =~ s/^execute/execute-/;
    
    # Work out how many padding characters to add on either side of the banner text    
    my $targetWidth = 80;
    my $timestampWidth = 20;
    my $titlePadding = 3;
    my $expandedTextLength = ((length $bannerText) *2) -1;  # Allow for space insertion
    my $numHighlight = ($targetWidth -$timestampWidth -$titlePadding - $expandedTextLength -$titlePadding) / 2;
    
    # Make sure that we always have some highlighting characters
    if ($numHighlight < 5) {
        $numHighlight = 5;
    }
    
    # Create highlight string, with repeating '=' characters
    my $highlight="";
    my $i;
    for ($i=0; $i<$numHighlight; $i++) {
        $highlight = $highlight . "=";
    }
       
    # Format the banner text. Capitalise and add spacing    
    my $formattedBannerText="";
    for ($i=0; $i<length $bannerText; $i++) {
        $formattedBannerText = $formattedBannerText . uc substr($bannerText, $i, 1);
        if ($i != (length $bannerText) -1) {
            $formattedBannerText = $formattedBannerText . " ";  # Add space between characters of banner text
        }
    }

	# Build full banner text
    my $bannerLine = "$highlight   $formattedBannerText   $highlight";
    
    _log($bannerLine);
}


# Output overall pass/fail from running a stage 
sub reportStageResult {
    my $stageName = shift() . ":";
    my $rc = shift;
    my $stageNameLen = shift;
    $stageNameLen += 1;  # to allow for the ':'
    
    # Work out result status text
    my $resultText;
    if ($rc == 0) {
        $resultText = " pass";
    } else {
        $resultText = "*fail*";
    }

    my $resultLine = sprintf("  %-${stageNameLen}s %s", $stageName, $resultText);
    _log($resultLine); 
}
        

# This subroutine validates the JVM to be used for running STF for perl code generation.
# It checks that javahome points at a directory containing a 'java' file.
sub validate_jvm {
    my $javahome = shift;
    my $javahome_name = shift;
 
    _log("Checking JVM: $javahome" );

	# Process execution goes wrong if java installed into a directory with spaces. Prevent execution.
    if ($javahome =~ / /) {
	    _log("**FAILED** Cannot run due to space character in \$JAVA_HOME: $javahome\n");
	    exit 1;
	}
    
    # Firstly check that there is 'java' file in the expected place below javahome
    if (!-e "$javahome/bin/java" && !-e "$javahome/bin/java.exe" ) {
        _log("**FAILED** JVM for '$javahome_name' is not pointing at a valid JVM build: $javahome\n" );
        exit 1;
    }
}

# This subroutine retrieves the available space on results-root.
#
# On unix, run df -k.
# df -k <file|dir> returns the amount of free space on the file system which <file|dir> is on.
# The output is different for unix variants.
# The code below caters for:
# Linux:
# Filesystem     1K-blocks    Used Available Use% Mounted on
# /dev/sda1       59464844 9812524  46608636  18% /
# AIX: df shipped with AIX:
# Filesystem    1024-blocks      Free %Used    Iused %Iused Mounted on
# /dev/hd3          5242880   4410064   16%    11291     1% /tmp 
# AIX: freeware df which may be installed:
# Filesystem           1K-blocks      Used Available Use% Mounted on
# /dev/hd3               5242880    832816   4410064  16% /tmp
# zOS:
# Mounted on     Filesystem                Avail/Total    Files      Status
# /xxxxxx       (xxxxxxx)             579397092/1290153920 31238576   Available
#
# On Windows use dir <results_root>:
# C:\git\openjdk-systemtest>dir
# Volume in drive C has no label.
# Volume Serial Number is xxxx-xxxx
#
# Directory of C:\git\openjdk-systemtest
#
#02/02/2017  15:23    <DIR>          .
#02/02/2017  15:23    <DIR>          ..
#02/02/2017  15:23               766 .gitattributes
#02/02/2017  15:23                30 .gitignore
#02/02/2017  15:23    <DIR>          openjdk-systemtest
#               2 File(s)            796 bytes
#               3 Dir(s)  214,264,049,664 bytes free

sub check_free_space {

    my $results_root = shift;

	my $bytes_free = 0;
	my $kb_free = 0;
	my $mb_free = 0;
	my $cmd = "";
	my @df_output = ();
	print "Retrieving amount of free space on drive containing " .  $results_root . "\n";
	if ($^O eq 'MSWin32') {
		# dir doesn't work with forward slashes or escaped backslashes, so remove any that are there.
		$results_root =~ s,/,\\,g;
		$results_root =~ s,\\\\,\\,g;
		$cmd = "cmd /c dir $results_root";
		@df_output = `$cmd 2>&1`;
		foreach my $line ( @df_output ) {
			if ( $line =~ m/.*bytes\s+free.*/ ) {
				#               3 Dir(s)  214,264,049,664 bytes free
				( $bytes_free ) = $line =~ /.*Dir\(s\)\s+(.*)\s+bytes\s+free.*$/;
				$bytes_free =~ s/\,//g;
				$kb_free = int ($bytes_free / 1024);
			}
		}
	}
	else {
		$cmd = "df -k $results_root";
		my @df_output = `$cmd 2>&1`;
		my $df_header = $df_output[0];
		my $df_body;

		for (my $i=1; $i<=$#df_output; $i++){
     		$df_body .= $df_output[$i];
     	}
		
		if ( $df_header =~ m/.*Filesystem\s+1K-blocks.*/ ) {
			( $kb_free ) = $df_body =~ /[^\s]*\s+\d+\s+\d+\s+(\d+).*/;
		}
		elsif ( $df_header =~ m/.*Filesystem\s+1024-blocks.*/ ) {
			( $kb_free ) = $df_body =~ /[^\s]*\s+\d+\s+(\d+).*/;
		}
		elsif ( $df_header =~ m/.*Mounted on\s+Filesystem.*/ ) {
			( $kb_free ) = $df_body =~ /[^\s]*\s+[^\s]+\s+(\d+)\/.*/;
		}
		else {
			print "Unable to parse $cmd output:\n";
			foreach my $line ( @df_output ) {
				print $line;
			}
			die "Unable to determine amount of free space for test results, terminating\n";
		}
	}
	$mb_free = int ($kb_free / 1024);

	die "Unable to determine amount of free space for test results, terminating\n" unless (defined $kb_free);

	print "There is $mb_free Mb free\n";
	return $mb_free;
}

# Parameters: parameter name, list of paths, inner folder

# The parameter name is just for error messages.
# Takes a semicolon-separated list of paths, and returns those same paths 
# after making them absolute (non-relative), and verifying their existence.
# We can also check that the directories are valid by checking for the 
# presence of an inner directory in at least one of the paths. 
# Note: inner directory must either begin with a slash, or be left blank.

# E.g. make_paths_absolute("systemtest-prereqs","/tmp/p1;/tmp/p2","/junit");
# or make_paths_absolute("test-roots","/tmp/p1","");

sub make_paths_absolute { 
	my $parameter_name = shift;
	my $stringOfPaths = shift;
	my $innerFolder = shift;
	
	my $absolutePaths = $stringOfPaths;
	
	if (!($stringOfPaths eq "null")) {
		my @paths_array = split(/;/,$stringOfPaths);
		my $one_path;
		my $found_inner_dir = "false";
		foreach $one_path (@paths_array)
		{
			$one_path = Cwd::abs_path($one_path);
			die "The " . $parameter_name . " directory " . $one_path . " could not be found. \n" unless (-d "$one_path"); 
			if (-d "$one_path$innerFolder") {
				$found_inner_dir = "true";
			}
		}
		$absolutePaths = join(';',@paths_array);
		die "The " . $parameter_name . " paths (\"" . $stringOfPaths . "\") are not valid because none of their absolute equivalents (\"" . $absolutePaths . "\") contain the inner directory \"$innerFolder\"\n" unless ($found_inner_dir eq "true"); 
	}
    
	return $absolutePaths;
}

# Takes a string of one or more paths, separated by semicolons, 
# finds the first path containing the supplied file/dir/symlink (etc) 
# name (which *must* begin with a slash), and returns the path that 
# contains it plus its name.
#
# E.g. findElement("/tmp/a;/tmp/b","/potato") might return "/tmp/b/potato"

sub findElement {
	my $stringOfPaths = shift;
	my $elementName = shift;
	
	my $elementPath = "null";
	my @paths_array = split(/;/,$stringOfPaths);
	my $one_path;
	
	foreach $one_path (@paths_array)
	{
		if ((-e "$one_path$elementName") and ($elementPath eq "null")) {
			$elementPath = "$one_path$elementName";
		}
	}
	die "Could not find " . $elementName . " in any of these supplied paths: " . $stringOfPaths . "\n" unless (!($elementPath eq "null")); 
	    
	return $elementPath;
}

# Takes a single directory and attempts to delete it, along with any contents.

sub deleteDirectory {
	my $doomed_directory = shift;
	if ( $^O eq 'MSWin32' ) {
        	my $cmd = "cmd /c rmdir /s /q \"$doomed_directory\"";
            `$cmd`;
            if ( $? ) {
                die "Error running $cmd: $!";
            } 
        }
        else {
            rmtree($doomed_directory, {keep_root => 1}, {error => \my $err} );
            if ((defined $err) and (@$err)) {
                for my $diag (@$err) {
                    my ($file, $message) = %$diag;
                    if ($file eq '') {
                        _log("  general error: $message");
                    } else {
                        _log("  problem unlinking $file: $message");
                    }
                }
            }
        }
}

# Simple internal method for logging.
# For use when code only wants to log something.
# Expects a single argument which contains the message to log.
sub _log {
    my $messageText = shift;
    
    stf::stfUtility->logMsg(message => $messageText);
}

 
#===============================================================================
# Usage
#===============================================================================
sub usage
{
   exit 1;
}
