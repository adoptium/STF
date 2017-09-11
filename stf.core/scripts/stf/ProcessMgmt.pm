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

package stf::ProcessMgmt;

use strict;
#use warnings;
use Cwd;
use Data::Dumper; # For debug
use File::Spec::Functions qw{catfile}; # For cat
use Time::Local; # For time functions

use stf::Constants qw(:all); # For $TRUE and $FALSE
use stf::stfUtility; # For log
use stf::Commands; # For start_process


#Define some constants for state
use constant STATE_UNSTARTED => 1;
use constant STATE_STARTED => 2;
use constant STATE_COMPLETED => 3;
use constant STATE_ERROR => 4;

our %STATE_DESCRIPTION = (STATE_UNSTARTED, 'unstarted',
						  STATE_STARTED, 'started',
						  STATE_COMPLETED, 'completed',
						  STATE_ERROR, 'error'
);

#Time of next heartbeat
my $nextbeat = time;
my $heartbeat_period = 300;

#------------------------------------------------------------#
# Constructor
#------------------------------------------------------------#
sub new {
	my $class = shift;
	my %args = @_;
	
	#This constructor is inherited by the subclasses. It performs two different functions
	#depending on the context it is called in.
	#
	#It either acts as a switch board, locating and loading the correct subclass to
	#be used on this platform or it is the constructor used for the subclasses
	if($class eq 'stf::ProcessMgmt') {
		#This is the base class constructor
		#Need to work out what platform I'm on and return an instance of the appropriate
		#sub class
		
		if($^O eq 'MSWin32') {
			require stf::ProcessMgmt::Windows;
			
			return new stf::ProcessMgmt::Windows(%args);
		}
		else {
			require stf::ProcessMgmt::UNIX;
			
			return new stf::ProcessMgmt::UNIX(%args);			
		}
	}
	else {
		#This is the sub class constructor
		
		#Do some basic arguments parsing
		if(! defined($args{command})) {
			croak("**Error** you didn't specify the command argument to the stf::ProcessMgmt constructor.");
		}
		
		my $self = \%args;
		
		bless $self => $class;
		
		$self->_qualify_command();
		
		$self->{state} = STATE_UNSTARTED;
		
		return $self;
	}
}

#------------------------------------------------------------#
# _get_description_for_state
# 
# Internal subroutine to get the description for a given state
#------------------------------------------------------------#
sub _get_description_for_state {
	my $state = shift;
	
	return $STATE_DESCRIPTION{$state};
}

#------------------------------------------------------------#
# _touch_file
# 
# Internal subroutine to touch a file
#------------------------------------------------------------#
sub _touch_file {
	my ($self, $filename) = @_;
	
	return if -f $filename;
	
	open(my $fh,'>',$filename) or die "Could not touch file $filename: $!";
	
	close($fh);
}

#------------------------------------------------------------#
# monitorProcess
#
# External subroutine thats monitors a process for dumps and checks if it overruns
#
# Usage:
#	($elapsed, $results) = monitorProcess($process, $results)
#
# Arguments:
#	$process    = the process
#	%results    = (status => $status, javacore => @javacore, drwatson =>
#                   @drwatson, core => @core, heap => @heap, snap => @snap)
#
# Returns:
#	$elapsed    = the test elapsed time
#	%results    = (status => $status, 		= flag indicating if the monitor found dumps
#					javacore => @javacore,	= array of javacore files (if found) 
#					drwatson => @drwatson,	= array of the drwatson files (if found
#					core => @core,			= array of the core/user.dmp files (if found)
#					heap => @heap,			= array of the heap files (if found)
#					snap => @snap)			= array of the snap files (if found)
#
# Notes:
#	The process object is expected to have the following attributes set:
#
#	$command    = The command the process is running
#	$runtime    = Optional, the expected test runtime in minutes, if set, the process "timeout"
#                 attribute will be set to 1 after the runtime elapsed
#	$starttime  = The time the process began
#	$rename     = Optional, rename dumps if set to 1, default to 0
#	$coredir    = Optional, the directory where the core files are written, default logdir
#	$logdir     = The directory where the results are written
#	$uid        = Optional, process unique id, default pid
#	$heartbeat  = Optional, default to 0
#	$stderr     = The process stderr
#	$moveTDUMP  = Optional, default to 0 but set to 1 if on ZOs
#------------------------------------------------------------#
sub monitorProcess {
	# print Dumper(\@_);
		
	my ($self, $p, %results) = @_;
	
	# Interpret the process object attributes
	
	my $stderr = $p->{stderr};
	
	my $command = $p->{command};
	
	my $runtime = $p->{runtime};
	
	my $starttime = $p->{starttime};
	
	my $monitorDumps;
	if (defined $p->{monitorDumps}) {
		$monitorDumps = $p->{monitorDumps};
	} else {
		$monitorDumps = $TRUE;
	}
	
	my $rename;
	if (defined $p->{rename}) {
		$rename = $p->{rename};
	} else {
		$rename = $FALSE;
	}
	
	my $coredir;
	if (defined $p->{coredir}) {
		$coredir = $p->{coredir};
	} else {
		$coredir = $p->{logdir};
	}
	
	my $logdir;
	if (defined $p->{logdir}) {
		$logdir = $p->{logdir};
	} else {
		die "The process must define a logdir directory.";
	}

	my $uid;
	if (defined $p->{uid}) {
		$uid = $p->{uid};
	} else {
		# Use process pid as a unique identifier
		$uid = $p->{pid};
	}
	
	my $heartbeat;
	if (defined $p->{heartbeat}) {
		$heartbeat = $p->{heartbeat};
	} else {
		$heartbeat = $FALSE;
	}
	
	my $elapsed = timelocal( localtime() ) - $starttime;
	
	unless($p->poll()) {
		# Leave earlier if the process has completed and been triaged
		return ($elapsed, %results) if ((_get_description_for_state($p->{state}) eq "completed") && $p->{triaged});
	}

	if ($monitorDumps) {
		my $any_dumps_found = $FALSE; 
		
		# Look for cross platform dumps
		foreach my $dump_type ("javacore", "core", "heap", "snap") {
			my $dump_file = $dump_type;
			
			$dump_file = "heapdump" if ($dump_type eq "heap");
			$dump_file = "Snap" 	if ($dump_type eq "snap");
			
			trace("Checking for undetected " . $dump_file ." files");
			($any_dumps_found, $results{$dump_type}) = stf::ProcessMgmt->detectDumpFile(
				$results{$dump_type},		
				$logdir,
				$dump_file,	
				$stderr,            
				$any_dumps_found, 
				$rename
			);
		}
		
		# Look for platform specific dumps
		if ($^O eq 'os390') {
			trace("Checking for undetected CEEDUMP files");
			($any_dumps_found, $results{ceedump}) = stf::ProcessMgmt->detectDumpFile(
				$results{ceedump},		
				$logdir,
				"CEEDUMP",	
				$stderr,            
				$any_dumps_found, 
				$rename
			);
			trace("Checking for undetected TDUMP files");
			($any_dumps_found, $results{tdump}) = stf::ProcessMgmt->detectDumpFile(
				$results{tdump},		
				$logdir,
				"TDUMP",	
				$stderr,            
				$any_dumps_found, 
				$rename
			);
		} elsif ($^O eq 'MSWin32') {
			trace("checking for undetected dr watson files");
			($any_dumps_found, $results{drwatson}) = stf::ProcessMgmt->detectDumpFile(
				$results{drwatson},		
				$logdir,
				"drwtsn32",	
				$stderr,            
				$any_dumps_found, 
				$rename
			);
		}
	
		if ($any_dumps_found) {
			$p->{dump_found} = $TRUE;
		}
	}
	
	# Check if the process has timed out
	if ($runtime) {
		if ($elapsed > $runtime) {
			$p->{timeout} = $TRUE;
			
			err("**FAILED** Process $p->{uid} has timed out");
			
			$results{status} = $FAILURE;
		}
	}
	
	# Check if the process has hung
	if (defined $p->{hung} && $p->{hung} == $TRUE) {
		err("**FAILED** Process $p->{uid} has hung");
	
		$results{status} = $FAILURE;
	}

	# if heartbeat is $TRUE then beat ever 5 minutes and print time stamp
  	if ($heartbeat) {
		my $timedif = $nextbeat - time;
		if ($timedif <= 0) {
			# autoflush the heart beat to prevent axxon timeouts, as windows + perl
			# sometimes buffers output
			local $| = 1;
			
			warn("Heart-beat. Java process is still running.");
			$nextbeat = time + $heartbeat_period; #HEARTBEAT_PERIOD;
		}
	}
	
	# Triage a completed process
	if (_get_description_for_state($p->{state}) eq "completed") {
		debug("Triaging process $p->{uid}...");
	
		if (defined $p->{expectedOutcome} && $p->{expectedOutcome} eq "never") {
			err("**FAILED** Process $p->{uid} has ended unexpectedly");
			
			$results{status} = $FAILURE;
		}
		
		# Ensure that a process that is expected to crash has indeed crashed.
		if (defined $p->{expectedOutcome} && $p->{expectedOutcome} eq "crashes" && $p->{dump_found} == $FALSE) {
			err("**FAILED** Process $p->{uid} was expected to crash but no dumps were found!");

			$results{status} = $FAILURE;
		}
		
		# Check if the process exited with the expected exit code.
		if (defined $p->{expectedOutcome} && $p->{expectedOutcome} =~ "exitValue:") {
			(my $expected_exit_codes = $p->{expectedOutcome}) =~ s/exitValue:/$1/g;
			
			# Get the real exit code	
			my $exit_code =  $p->get_result()->exit_code();
	
			# We may be given an expected exit code or a list of expected exit codes
			my @codes = split(',', $expected_exit_codes);
			my $expected_exit_code_met;
			foreach my $expected_exit_code (@codes) {
				$expected_exit_code_met = 1 if ($exit_code == $expected_exit_code);
			}

			unless ($expected_exit_code_met) {
				err("**FAILED** Process $p->{uid} ended with exit code ($exit_code) and not the expected exit code/s ($expected_exit_codes)");

				$results{status} = $FAILURE;
			}
		}
		
		# We only want to triage a process after completion once so that we dont 
		# waste resources monitoring processes that have already completed
		$p->{triaged} = $TRUE;
	}

	return ($elapsed, %results);
}

#------------------------------------------------------------#
# detectDumpFile
#
# Internal subroutine that moves and renames the dump files (of type specified) 
# to the specified directory
#
# Usage:
#	($status, $newdump) = detectDumpFile($dumps, $homedir, $dumptype, $stderr, $status, $rename)
#
# Arguments:
#
#	$dumps      = dump array
#	$homedir    = the directory where the dumps are created
#	$dumptype   = the type of dumpfile (core, javacore, drwtsn32, user.dmp)
#	$stderr     = the stderr log
#	$status     = the current status
#	$rename     = $TRUE/$FALSE
#
# Returns:
#	$status     = the new status
#	$newdump    = the name of the dump moved
#
# Notes:
#	This function is intended to be used only by other functions in this module.
#------------------------------------------------------------#
sub detectDumpFile {
	#print Dumper(\@_);
	
	my ($self, $dumps, $homedir, $dumptype, $stderr, $any_dumps_found, $rename) = @_;

	my $cwd      = getcwd();
	my $now      = stf::stfUtility->getNow();
	my $newdump  = "";
	my @dumplist = ();
		
	# change to the directory we expect the files in
	chdir $homedir;
	
	if ($dumptype eq "TDUMP") {	
			debug("log file name to scan for tdumps is: " . $stderr);
			
			# Use a hash to ensure that each tdump is only dealt with once
			my %parsedNames = ();
			
			my $file = stf::stfUtility->readFileIntoArray($stderr);
	    	my $tdump;	
	    	foreach my $line (@{$file}) {
			   	if ($line =~ /IEATDUMP success for DSN=/ or $line =~ /IEATDUMP failure for DSN=/) {
		    		($tdump) = $line =~ /DSN='(.*)'/;
		    		
		    		$parsedNames{$tdump} = 1; 
				}
			}
		
		push(@dumplist, keys(%parsedNames));
	}
	else {
		# The JVM creates its core using the system core and then renames it, 
		# so we don't want to detect these before they are renamed. To achieve
		# this look for a core with dmp at the end, as the raw core is just "core".
		my $globLine ;
		if ($dumptype eq "core") {
			$globLine = catfile($homedir,"${dumptype}*dmp");
		}
		else {
			$globLine = catfile($homedir,"${dumptype}*");
		}

		$globLine =~ s/\s/\?/g; #replace any spaces in the path.
		my @dumpNames = glob("${globLine}");

		# The dump names above are not fully qualified path names but those in @dumps are
		# Correct this here by adding on the full directory path	
		foreach my $dump (@dumpNames) {
			#$dump = catfile($homedir, $dump); #No need to correct, they now already have the homedir in place.
			push (@dumplist, $dump);
		}
	}

	# Create a hash of the dumps already detected and processed on previous runs using the passed $dumps array ref
	my %alreadyDetected;
	foreach my $dump (@{$dumps}) { 
		$alreadyDetected{$dump} = $TRUE;
	}

	# Ignore the dumps we already knew about and create a list of the new ones
	my @newDumps;
	foreach my $detectedDump (@dumplist) {
		if(!$alreadyDetected{$detectedDump}) {
			push(@newDumps, $detectedDump);
		}
	}
	
	my $newlyFoundDumps   = scalar(@newDumps);
	my $existingDumpCount = scalar(@{$dumps});
	
	# Now check if we have found any new dumps
	if ($newlyFoundDumps ne 0) {
		$any_dumps_found = $TRUE;
		debug("DetectDumps given [".$existingDumpCount."] $dumptype dumps and found [".$newlyFoundDumps."] additional undetected $dumptype dumps.");
	} else {
		chdir $cwd;
		return ($any_dumps_found, $dumps);
	}

	if ($dumptype eq "TDUMP") {
		# On ZOs TDUMPS are saved outside the Unix layer
		# moveTDUMPS will attempt to move them to the file system
  		$self->moveTDUMPS($stderr, $homedir, \@newDumps);
  		
  		foreach my $dump (@newDumps) {	
  			if (grep(/\Q${dump}\E/, @{$dumps}) == 0) {
				push( @{$dumps}, $dump );
			}
		}
	} else {
		foreach my $dump (@newDumps) {		
			if (-e $dump) {
				warn("Found dump at: $dump");
			} else {
				err("Found a dump that doesn't exist: $dump");
			}
		
			$newdump = $dump;
			
			if (($rename == $TRUE) and ($dump =~ /$dumptype\.\D/ or $dump =~ /$dumptype/)) {
				my ($n, $p, $t) = fileparse( $dump, '\.[\w\-\+]*' );
				
				$newdump = catfile( $homedir, "${n}_${now}${t}" );
	
				my $cnt = 0;
				while ((-e $dump) and ($cnt < 90)) {
					rename( $dump, $newdump );
					
					sleep 10;
					
					$cnt++;
				}
	
				if (-e $dump) {
					$newdump = $dump;
				}
			}
	
			stf::stfUtility->writeToFile (
							 file    => $stderr,
							 content => ["${dumptype} file generated - ${newdump}"],
							 replace => $FALSE
			);
			
			if (grep(/\Q${newdump}\E/, @{$dumps}) == 0) {
				push( @{$dumps}, $newdump );
			}
		}
	}
	 
	chdir $cwd;
	return ($any_dumps_found, $dumps);
}


#------------------------------------------------------------#
# moveTDUMPS
#
# Internal subroutine that searches through the file looking for evidence of a TDUMP
# and then attempts to move the TDUMP and any multiple dumps associated, returning an 
# array of all the dumpes moved. Can also take a list of known dump names.
#
# Usage:
#  @dumplist = moveTDUMPS($filePath, $moveLocation, $dumpref, $joinDumps)
#
# Arguments:
#	$filePath = either a log file name or an array ref of log file names
#	            These will be scanned for IEATDUMP messages (can be null)
#	$moveLocation = the directory to move the dumps to
#	$dumpref = an array reference containing names of dumps to be moved (can be null)
#	$joinDumps = if true, then multi-part tdumps will be joined into a single part.
#
# Returns:
#	@dumplist = an array of dumps names found
#
# Notes:
#	This function is intended to be used only by other functions in this module.
#------------------------------------------------------------#
sub moveTDUMPS {
	my ($self, $stderr, $moveLocation, $dumpref, $joinDumps) = @_;
	
	trace("moveTDUMPS subroutine - \@_ : " . Dumper(\@_));
	debug("Moving the TDUMPS to" . $moveLocation);
	
	if ($^O ne 'os390') {
		return;
	}
	
	my @dumplist = @$dumpref;
	if (!@dumplist) {
		debug("No dumps names found in logs/supplied");
		return; # Nothing to do;
	}
	
	debug("Attempting to retrieve dumps with names: '" . join("', '", @dumplist), "'");

	my %movedDumps 	   = (); 
	my $multipartIndex = 0;		
	my $file 		   = stf::stfUtility->readFileIntoArray($stderr);
	
	foreach my $dump (@dumplist) {
		# Check if there's been any failures during the the TDUMP generation
		foreach my $line (@{$file}) {
			if ($line =~ /IEATDUMP failure for DSN='$dump' RC=0x00000004 RSN=0x00000000/) {} # A partial dump has been created, even though it is a failure, dump still occurred and needs moving
			
			if ($line =~ /IEATDUMP failure for DSN='$dump' RC=0x00000008 RSN=0x00000026/) { # Dump failed due to no space left on the machine, so print out warning message and return
				err("**ERROR** TDUMP generation failed due to no space left on machine, manual cleanup required...");
				return;
			}
		}
		
		if($dump =~ /X&DS/) {
			warn("Naming of dump consistent with multiple dumps");
		}
			
		if($dump !~ /X&DS/) {
			my $cmd = "mv \"//'${dump}'\" ". catfile($moveLocation, "$dump");
			
			my $moveLog = $moveLocation . "/move.log";
			
			my ($rc, $move_process) = stf::Commands->run_process(
				mnemonic     => "MV",
				command      => $cmd,
				runtime      => 300,
				logName      => $moveLog,
				monitorDumps => $FALSE, 
				singleLog    => $TRUE,
				echo         => $FALSE
			);

			$movedDumps{$dump} = 1;		

			#  If there is anything in the move log assume something gone wrong
			if (! -z $moveLog) {
				err("**ERROR** Unable to move dump, attempting to move to dev null instead..");
						
				my $cmd = "mv \"//'${dump}'\" /dev/null";
						
				my $deleteLog = $moveLocation . "/delete.log";
						
				my ($rc, $delete_process) = stf::Commands->run_process(
					mnemonic     => "DEL",
					command      => $cmd,
					runtime      => 300,
					logName      => $deleteLog,
					monitorDumps => $FALSE,
					singleLog    => $TRUE,
					echo         => $FALSE
				);
			}
		}
		else {
			my @parts;
			
			my $i = 1;
			
			my $multiFound = $TRUE;
			
			$dump =~ s/X&DS//;
			
			warn("Changed dump name to $dump");

			warn("Will scan for dumps ${dump}.X00n , we expect a not-found failure at the end of this");

			while($multiFound == $TRUE && $i < 10) {	
				my $dump01 = $dump."X00".$i;
				
				warn("Looking for multiple TDUMPs - $dump01");
				
				my $cmd = "mv \"//'${dump01}'\" ". catfile($moveLocation, "$dump01");
				
				my $moveLog = $moveLocation . "/move_$i.log";
				
				my ($rc, $move_process) = stf::Commands->run_process(
					mnemonic     => "MV$i",
					command      => $cmd,
					runtime      => 300,
					logName      => $moveLog,
					monitorDumps => $FALSE,
					singleLog    => $TRUE,
					echo         => $FALSE
				);
				
				#  If there is anything in the move log assume something gone wrong
				if (! -z $moveLog) {
					warn("**ERROR** Unable to move dump, attempting to move to dev null instead..") if ($i == 1);
							
					my $cmd = "mv \"//'${dump}'\" /dev/null";
							
					my $deleteLog = $moveLocation . "/delete_$i.log";
							
					my ($rc, $delete_process) = stf::Commands->run_process(
						mnemonic     => "DEL$i",
						command      => $cmd,
						runtime      => 300,
						logName      => $deleteLog,
						monitorDumps => $FALSE,
						singleLog    => $TRUE,
						echo         => $FALSE
					);
					
					$multiFound = $FALSE;
				}

				# We will set multiFound to false if there is an error.					
				if ($multiFound == $TRUE) {
					warn("Found multiple TDUMP named $dump01, moved to $moveLocation");
					
					push(@parts, $dump01);
					
					if($i < 2) {
						warn("Added $dump01 to dump array");
						
						$movedDumps{$dump01} = 1;
					}
					else {
						warn("MULTIPLE DUMP found, this needs to be merged before continuing!");
					}

				}
				
				$i++;	
			}

			# Join multipart dumps
			if ($joinDumps && scalar(@parts) > 1) {
				$multipartIndex++;
				
				my $joinedDumpName = "joined.".$dump.".".$multipartIndex.".dmp"; 
				
				my $rv = join_dumps($moveLocation, \@parts, $joinedDumpName);
				
				if ($rv) {
					my $oldKey = $parts[0];
					
					delete $movedDumps{$oldKey};
					
					$movedDumps{$joinedDumpName} = 1;
				}	
			}
		}									                  				
	}
		
	my @returnList = keys(%movedDumps);
		
	warn("TDUMP Summary:");
	
	if (scalar(@returnList) eq 0) {
		warn("**ERROR** TDUMP/S not moved to file system.");
	} else {
		foreach my $line (@returnList) {
			warn("$line");
		}
	}

	warn("End of TDUMP Summary");
	
	return (@returnList);	 		
}

#------------------------------------------------------------#
# join_dumps
#
# Internal subroutine that re-joins the parts of a multipart tdump
# On failure, the joined dump will be deleted.
# 
# Usage:
#	my $rv = join_dumps($sourceDir, $dumpNames, $newName);
#
# Arguments: 
#	$sourceDir - where the dump parts are
#	$dumpNames - a reference to an ordered array of dump parts
#	$newName - the name for the joined dump
#------------------------------------------------------------#
sub join_dumps {

	my ($sourceDir, $dumpNames, $newName) = @_;
  
	warn("Attempting to join dump parts: " . join(',', @$dumpNames) . " to file $newName");

	my $outputName = "$sourceDir/$newName";

	my $seek = 0;
  
	foreach my $dumpName (@$dumpNames) {
		warn("Joining dump part $dumpName");
	    
		my $command = "dd if=$sourceDir/$dumpName of=$outputName bs=4160";
	    
		if ($seek) {
			$command = $command . " seek=$seek";
		}
	    
		warn("Running command '$command'");
	    
		my $output = `$command 2>&1`;
	    
		if ($output =~ m/(\d+)\+(\d)+ records out/) {
			my $blocksWritten = $1;
			
			my $extraBytesWritten = $2;
			
			warn("Seems to have worked, $blocksWritten blocks, $extraBytesWritten bytes");
	
			if ($extraBytesWritten ne "0") {
				err("Number of blocks written wasn't a whole number. Can't cope !! Argh. Deleting output file");
				`rm $outputName`;
				return 0;
			}
	
			$seek = $seek + $blocksWritten;
	    }
		else {
			err("The dd command appears to have failed. Output was\n$output\nRemoving output file $outputName");
			`rm $outputName`;
			return 0;
	    }
 	}
  return 1;
}

1;
__END__
