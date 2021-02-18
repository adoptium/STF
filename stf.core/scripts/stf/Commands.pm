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
#----------------------------------------------------------------------------------#
# Commands.pm
#
# This library manages processes allowing the caller to run, kill and monitor processes in a
# platform independent way.
#
# The caller has the choice of calling the "run" subroutine to run processes synchronously where 
# processes run in parallel and are monitored to completion or start a process, monitor and kill 
# as separate calls using the other external subroutines. There's also dump monitoring, logging 
# the ability to specify an expected outcome from a process and run several instances of a given
# command.
#
# The library was written to aid the automation of multi process test scenarios.
#
# Some usage examples:
#
# Example 1 - Start a simple process (java -version)
#  my ($rc, $process) = stf::Commands->start_process(mnemonic => "Java", command => "java", args => "-version", logName => $logName);
#
# Example 2 - Run 10 java processes in parallel, monitored to completion with a timeout value of 5 mins
#  my $rc = stf::Commands->run({mnemonic => "JAVA", command => "java", args => @args, logName => $logName, runtime => 300, instances => 10});
#
# Example 3 - Run 2 separate (unrelated) processes in parallel, monitored to completion
#  my $rc = stf::Commands->run({mnemonic => "Java", command => "java", args => @java_args, logName => $logName},
#							   {mnemonic => "Perl", command => "perl", args => @perl_args, logName => $logName});
#
# Example 4 - Start a process that is expected to create a dump for a RAS test
#  my $rc = stf::Commands->run({mnemonic => "CRASH", command => "java", args => @args, logName => $logName, expectedOutcome=crashes});
#
# Example 5 - Start a ORB server that is expected to never complete
#  my ($rc, $process) = stf::Commands->start_process(mnemonic => "ORBS", command => "java", args => @args, logName => $logName, expectedOutcome=never);
#
# Example 6 - Run a Node command that is expected to end with return code 11
#  my $rc = stf::Commands->run({mnemonic => "Node", command => "node", args => @args, logName => $logName, expectedOutcome=exitValue:11});
#
# Example 7 - Start a couple of ORB servers and 3 clients
#  my ($rc, %process_list) = stf::Commands->start_processes({mnemonic => "ORBS", command => "java", args => @server_args, logName => $logName, instances => 2, expectedOutcome=never},
#															{mnemonic => "ORBC", command => "java", args => @client_args, logName => $logName, instances => 3});
#
# Example 8 - Monitor a list of processes/single process
#  my $rc = stf::Commands->monitor_processes(%process_list);
#  my $rc = stf::Commands->monitor_processes($process);
#
# Example 9 - Kill a list of processes/single process
#  my $rc = stf::Commands->kill_processes(%process_list);
#  my $rc = stf::Commands->kill_processes($process);
#----------------------------------------------------------------------------------#
package stf::Commands;

# Core Perl modules
#use strict;
use Time::Local;
use File::Spec::Functions qw{catfile};
use File::Basename;
use File::Temp qw{tempfile};
use File::Path qw(mkpath);
use Cwd;
use Cwd qw(abs_path);
use Fcntl qw(SEEK_SET SEEK_CUR SEEK_END); #SEEK_SET=0 SEEK_CUR=1 ...

# For dev
use FindBin qw($Bin);
use lib "$Bin/..";
use Data::Dumper;

# STF modules
use stf::ProcessMgmt;
use stf::Constants qw(:all);
use stf::stfUtility;


my $useHiRes = $TRUE;
eval "use Time::HiRes";
if ($@) {
     $useHiRes = $FALSE;
}

my $delimiter = stf::stfUtility->getDelimiter;

# Heartbeat parameters
my $heartbeat = $TRUE; # If TRUE print heartbeat every 5 minutes
my $heartbeat_period = 300;
my $nextbeat = time + $heartbeat_period; # Time of next heartbeat

#----------------------------------------------------------------------------------#
# validate_args
#
# This is an internal subroutine that interprets and validates the supplied process arguments 
# to ensure that the arguments provided are sane. It also defines stderr/stdout and sets 
# process attributes required internally. 
#
# Usage:
#  %process_args = validate_args(%process_args); 
#
# Arguments:
#  %process_args = A hash that contains the supplied process attributes
#
# Returns:
#  %process_args = A hash with the validated process attributes 
#----------------------------------------------------------------------------------#
sub validate_args {
	my %process_args = @_;
	
	trace("validate_args subroutine - \%process_args:" . Dumper(\%process_args));

	my $rc = 0;
	
	# Ensure process mnemonic, command and logdir parameters are supllied as these parameters 
	# are required for monitoring and logging.
	unless (defined $process_args{command}) {
		warn "Process command (\$command) must be defined";
		
		$rc = 1;
	}
	
	unless (defined $process_args{mnemonic}) {
		warn "Process name (\$mnemonic) must be defined";
		
		$rc = 1;		
	}

	unless (defined $process_args{logName}) {
		warn "Process logName (\$logName) must be defined";
		
		$rc = 1;				
	}

	# Extract the log directory from logName
	$process_args{logdir} = dirname($process_args{logName});

	# Ensure the process mnemonic and logName are unique if we running multiple instances of the same command
	if (defined $instance) {
		$process_args{mnemonic} = $process_args{mnemonic} . "$instance";
		$process_args{logName} = $process_args{logName} . "$instance";
	}

	# Pad the mnemonic with spaces for a consisted output
	while (length($process_args{mnemonic}) < 3) {
		$process_args{mnemonic} .= " ";
	}
	
	# Default prefix_on to $TRUE (1) for the echoing
	$process_args{prefix_on} = $TRUE unless (defined $process_args{prefix_on});
	
	# Default dump_found to $FALSE (0)
	$process_args{dump_found} = $FALSE;

	# Default echo to $FALSE (0)
	$process_args{echo} = $FALSE unless (defined $process_args{echo});
	
	# Define expectedOutcome by default to avoid warnings
	$process_args{expectedOutcome} = defined unless (defined $process_args{expectedOutcome});

	# Use the mnemonic parameter as the uid	
	$process_args{uid} = $process_args{mnemonic};
	
	# Set the logs
	if (defined $process_args{singleLog} && $process_args{singleLog} == $TRUE) {
		$process_args{stderr} = $process_args{logName};
		$process_args{stdout} = $process_args{logName};
	} else {
		$process_args{stderr} = $process_args{logName} . ".stderr";
		$process_args{stdout} = $process_args{logName} . ".stdout";
	}

	# Ensure we start from a clean state
	unlink $process_args{stderr} if (exists $process_args{stderr});
	unlink $process_args{stdout} if (exists $process_args{stdout});

	# If the parameter args is not defined, assume the command parameter includes the arguments 
	unless (defined $process_args{args}) {
		my @cmd_elements = split( " ", $process_args{command} );

		$process_args{command}  = shift @cmd_elements;

		@{$process_args{args}}  = @cmd_elements;
	}

	# The inwards of the Process object expect an array of arguments,
	# convert args to an array if we are given args as a string (scalar).
	if ((ref \$process_args{args}) =~ "SCALAR") {
		my @args_elements = split( " ", $process_args{args} );

		$process_args{args} = \@args_elements;
	}

    # Create value for the current instnance number, with empty string if only
    # running 1 instance of the process.	
	my $instanceId = '';
	if (defined $instance) {
	    $instanceId = $instance;
	}
	
	# If the process we are about to run needs to know its instance number then replace 
	# the placeholder now with the actual instance number. See StfConstants.java.
   	for $argIndex (0..@{$process_args{args}}) {
   	    if (defined @{$process_args{args}}[$argIndex]) {
    		s/\$\{\{STF-PROCESS-INSTANCE\}\}/$instanceId/ for @{$process_args{args}}[$argIndex]; 
    	}
    }

	info("Running command: $process_args{command} " . join(" ", @{$process_args{args}}));

	info("Redirecting stderr to $process_args{stderr}");

	info("Redirecting stdout to $process_args{stdout}");

	# Pre-define the logs last byte, these attributes are required for the process_logs function.
	$process_args{stderr_last_byte} = 0;
	$process_args{stdout_last_byte} = 0;
	
	return ($rc, %process_args);
}

#----------------------------------------------------------------------------------#
# start_process
#
# This is an external subroutine that starts a process, it expects a hash of arguments and 
# returns a process object and a return code.
#
# Usage:
#  my ($rc, $process) = start_process(%process_args); 
#  my ($rc, $process) = start_process(mnemonic => "java", command => "java -version", logName => $logName);
#
# Arguments:
#  mnemonic		= The process mnemonic, to be used as the process identifier
#  command 		= The process command, it supports command args too
#  args			= Optional, for more complex command args, it can be either a string or an array of args
#  logName 		= The name of the log where the results are written
#  echo			= Optional, set to 1 to echo the stderr/stdout to the console screen/Axxon
#  runtime     	= Optional, the expected test runtime in seconds, if set, the test will be marked failed if it overruns
#  kill			= Optional, if set to 1, processes will be killed when they timeout, defaults to 1
#  instances=<number>	= Optional, use to launch multiple instances of a given command. The process mnemonic 
#				will have the instance number appended to it
#  prefix_on	= Optional, set to use a prefix during the echoing of stderr/sdtout, defaults to 1
#  expectedOutcome=never	= Set when the expected outcome is for the process to run in the background and never
# 				terminate, the test will be marked failed if the process monitor library detects that the process is not running
#  expectedOutcome=crashes	= Set when the expected outcome is for the process to crash, the test will be marked 
# 				failed if the process monitor library detects that the process completes without a crash occuring
#  expectedOutcome=exitValue:<value>	= Use to explicitly set an expected return/exit code, the test will be marked 
# 				failed if the process monitor library detects that the process completes with a different return/exit code
#
# Returns:
#  $rc = The return code, 0 pass, 1 failure
#  $process = A process object
#----------------------------------------------------------------------------------#
sub start_process {
	my ($self, %process_args) = @_;
	
	trace("start_process subroutine - \%process_args: " . Dumper(\%process_args));

	$rc = 0;
	($rc, %process_args) = validate_args(%process_args);

	my $process = new stf::ProcessMgmt(%process_args);
	
	unless ($process->start()) {
		warn "Could not start \"$process->{command} " . join(" ", @{$process->{args}}) . "\". Error message: " . $process->err_msg() . "\n";
		
		$rc = 1;
	}

	$self->process_logs($process);
	
	return ($rc, $process);
}

#----------------------------------------------------------------------------------#
# start_processes
#
# This is an external subroutine to start multiple processes, it expects a list of hashes for process
# arguments, it returns a list of process objects and a return code.
#
# Usage:
#  my ($rc, %list) = stf::Commands->start_processes(\%command1, \%command2);
#  my ($rc, %list) = stf::Commands->start_processes({mnemonic => "Java1", command => "java", args => @args1, logName => $logName}, 
#											 		 {mnemonic => "Java2", command => "java", args => @args2, logName => $logName});
#
# Arguments:
#  A list of hashes where each hash contains the process args required to instanciate a process object.
#
# Returns:
#  $rc = The return code, 0 pass, 1 failure
#  %list = A list (hash) of process objects, where the process mnemonic is the key and the process object is the value
#
# Notes:
#  See the start_process description for the arguments required to start a process.
#----------------------------------------------------------------------------------#
sub start_processes {
	my %process_list;

	my ($self, @commands) = @_;
	
	trace("start_processes subroutine - \@commands: " . Dumper(\@commands));

	my $status = 0;
	
		#Run our commands
		foreach my $this_command (@commands) {
                my %process_args = %{$this_command};

                if (defined $this_command->{instances}) {
                	# Run multiple instances of the same command if required
            
                	for $instance (1..$this_command->{instances}) {
						my ($rc, $process) = $self->start_process(%process_args);

                        # Assign $process object to the process_list hash, use the process uid as it's key.
                        $process_list{$process->{uid}} = $process;
                        	
                        $status = $rc unless ($rc == 0);
					}
				} 
				else {
					my ($rc, $process) = $self->start_process(%process_args);

					# Assign $process object to the process_list hash, use the process uid as it's key.
					$process_list{$process->{uid}} = $process;
					
					$status = $rc unless ($rc == 0);
                }
        }
	return ($status, %process_list);
}

#----------------------------------------------------------------------------------#
# monitor_processes
#
# This is an external subroutine to monitor processes for dumps, it checks if they overrun and then 
# later checks if the processes complete with the expected outcome
#
# Usage:
#  my $rc = stf::Commands->monitor_processes($process);
#  my $rc = stf::Commands->monitor_processes(%list);
#
# Arguments:
#  $process/%list = A process object or a list of process objects
#
# Returns:
#  $rc = The return code, 0 pass, 1 failure
#----------------------------------------------------------------------------------#
sub monitor_processes {
	my $self = shift; 

	my %process_list;	

	# Iterate through the parsed arguments to ensure that we are given a valid object 
	# or a list of objects, this approach allows for the end-user to call the monitor_processes 
	# directly with either a single object, an array of objects or a hash of objects.
	foreach $obj (@_) {
		if (ref ($obj) =~ "stf::ProcessMgmt:") {
			$process_list{$obj->{uid}} = $obj;
		}
	}

	unless(keys %process_list) {
		warn "Internal error: No process ids supplied to monitor_processes function" ;
 
 		return 1;
	}
 
 	info("Monitoring processes: " . join(" ", sort keys %process_list));
 	
 	trace("monitor_processes - \%process_list: " . Dumper(\%process_list));
 
	my %results;

	my $not_all_finished = 1;

	# Monitor processes whilst they are still running.
	while($not_all_finished == 1) {
		$not_all_finished = 0;
		
		while ((my $uid, my $process) = each(%process_list)) {
			# process logs
			$self->process_logs($process);
			
			# Monitor the process for timeout and dumps
			($elapsed, %results) = stf::ProcessMgmt->monitorProcess($process, %results);
			
			if (defined $results{status} and $results{status} == $FAILURE) {
				debug("Failure detected, exiting early!");
				
				# Create dumps if the process has timed out or hung
				if ($process->{hung} or $process->{timeout}) {
					$self->create_dumps($process);
				}
												
				$not_all_finished = 0;
				last;
			}
			
			# Check if the process is still running
			if ($process->{state} == 2) { 	# State 2 means the process is still running
				trace("Process $process->{uid} is still running");
				_heartbeat($process);
				
				# Leave the loop if the expected outcome is to never complete.
				next if ($process->{expectedOutcome} eq "never");
				
				$not_all_finished = 1;
			}
		}

		if ($not_all_finished == 1) {
			# Use the HiRes perl module if available otherwise sleep for 1 sec
			if ($useHiRes) {
				# Work out how long to sleep for. This tries to strike a balance between avoiding 
				# wasting time waiting for short runs, vs checking too frequently on long runs.
				# Note that 'elapsed' is in seconds, so after running for 15s the sleep time would be .150s or 150ms.  
				my $sleeptime = $elapsed / 100;
				
				if ($sleeptime < 0.020) {
		    			$sleeptime = 0.005;
				} 
				elsif ($sleeptime > 0.500) {
		    			$sleeptime = 0.500;
				}
	    		Time::HiRes::sleep($sleeptime);
			}
			else {
				sleep 1;
			}
		}
		else {
			debug("Stopped monitoring");
		}
	}

	# Make sure we echo the output of all the processes before exiting the monitoring
	foreach my $uid (sort keys %process_list) {
		my $process = $process_list{$uid};
	
		$self->process_logs($process);
	}

	my $rc = report(\%results, %process_list);
	
	return $rc;
}

sub report {
	my $results = shift;

	my %process_list = @_;
	
	my $run_status = 0;
	
	info("Monitoring Report Summary:");
	
	# Evaluate the processes' outcome and report 
	foreach my $uid (sort keys %process_list) {
		my $process = $process_list{$uid};
			
			# Check if a process has crashed
			if ($process->{dump_found} == $TRUE) {
				
				if ($process->{expectedOutcome} eq "crashes") {
						info("  o Process $process->{uid} caused dumps as expected");
	
						next;
				}
				else {
						info("  o Process $process->{uid} has crashed unexpectedly");
	
						$run_status = 1;
						next;
				}
			} 		
	
			# Ensure that a process that is expected to crash has indeed crashed.
			if ($process->{expectedOutcome} eq "crashes" && $process->{dump_found} == $FALSE) {
				info("  o Process $process->{uid} was expected to crash but no dumps were found");
	
				$run_status = 1;
				next;
			} 
	
			# Ensure that a process that in not meant to complete is still running.
			if ($process->{expectedOutcome} eq "never" && $process->{state} == 3) {
				info("  o Process $process->{uid} has ended unexpectedly");
				
				$run_status = 1;
				next;
			}
	
			# Check if the process has timed out.
			if ($process->{timeout}) {
				info("  o Process $process->{uid} has timed out");
	
				$run_status = 1;
				next;
			}
			
			# Check if the process has hung.
			if ($process->{hung}) {
				info("  o Process $process->{uid} has hung");
	
				$run_status = 1;
				next;
			}
	
			# Check if the process has been killed
			if ($process->{killed}) {
				info("  o Process $process->{uid} has been killed");
	
				$run_status = 1;
				next;
			}
			
		if($process->poll()) {
			if ($process->{expectedOutcome} eq "never") {
				info("  o Process $process->{uid} is still running as expected");
				
				next;			
			}
			else {
				info("  o Process $process->{uid} is still running");
							
				$run_status = 1;
			}			
		} 
		else {
			# Get the process return code
			my $rc =  $process->{result}->{return_code}; 
	
			# In unix the return code is complicated and the exit code might not be the same as the return code.
			# For example, a kill -9 will create a 9 exit code but the return code will remain unchanged. 
			# So if we have an exit code we say the return code is the exit code to avoid false positives.
			$rc = $process->{result}->{code} if ((defined $process->{result}->{code}) && ($process->{result}->{code} != 0));
	
			# Check if the process exited with the expected exit code.
			if ($process->{expectedOutcome} =~ "exitValue:") {
				(my $expected_exit_codes = $process->{expectedOutcome}) =~ s/exitValue:/$1/g;
				
				# Get the real exit code	
				my $exit_code =  $process->get_result()->exit_code();
					
				# We may be given an expected exit code or a list of expected exit codes
				my @codes = split(',', $expected_exit_codes);
				my $expected_exit_code_met;
				foreach my $expected_exit_code (@codes) {
					if ($exit_code == $expected_exit_code) {
						info("  o Process $process->{uid} ended with the expected exit code ($expected_exit_code)");
						
						$expected_exit_code_met = 1;
					}
				}
				
				if ($expected_exit_code_met) {			
					$rc = 0;
					
					next;					
				}
				else {
					info("  o Process $process->{uid} ended with exit code ($exit_code) and not the expected exit code/s ($expected_exit_codes)");
	
					$run_status = 1;
					next;						
				}
			}
		
			# Check if there's a non 0 return code.
			if ($rc != 0 ) {
				info("  o Process $process->{uid} ended with return code $rc");
		
				$run_status = 1;
			}
			else {
				info("  o Process $process->{uid} ended sucessfully");
			}
			
			trace(Dumper(\$process));
		}
	}
	
	debug("Fail - At least one process returned an error code or ended with an unexpected outcome.") unless ($run_status == 0);
	
	trace("report subroutine - \$results: " . Dumper(\$results));
	trace("report subroutine - \%process_list) " . Dumper(\%process_list));
	
	return $run_status;
}

#----------------------------------------------------------------------------------#
# process_logs
#
# This is an internal subroutine that:
# - Echos the stderr/stdout to the screen/Axxon when the process "echo" attribute is set. 
# Note by default the stderr/stdout is redirected to the logs only. 
# - Scans the logs for hangs
#
# Usage:
#  $self->process_logs($process);
#
# Arguments:
#  $process = A process object
#----------------------------------------------------------------------------------#
sub process_logs {
	my ($self, $process) = @_;

	foreach my $output ("stdout", "stderr") {
		open LOG, "<$process->{$output}" or die "Could not open file '$process->{$output}' $!";
		
		# Find the last byte of the log
		my $target_byte=$process->{$output . "_last_byte"};

		# Add a prefix to each line to make it easy to identify where the stderr/stdout
		# is coming from. This is particulary useful when running several processes in parallel. 
		my $prefix;		
		if ($process->{prefix_on} == $TRUE) {
			if ($output eq "stderr") {
				$prefix = "$process->{uid} $output ";
			}
			else {
				$prefix = "$process->{uid} ";					
			}		
		}
		else {
			$prefix = "";	
		}

		seek(LOG,$target_byte,SEEK_CUR);

		while (<LOG>) {
		    my $finalChar = substr $_, -1;
		    if ($finalChar eq "\n") {
	    		if ($process->{echo} == $TRUE) {
			        # Found a complete line. Echo it.
					# Note, both stderr and stdout are printed to stdout, this is deliberate. 
					# Autoflush STDOUT to prevent buffering.
					$| = 1;
					select(STDOUT);
					print STDOUT "$prefix" . "$_";			
	    		}

				# Scan the logs for hangs but ignore logs from STF (the parent process) as otherwise STF
				# gets declared hung closely after the child process hangs and it gets killed prematurely.
				if ($process->{mnemonic} ne "STF" && "$_" =~ "POSSIBLE HANG DETECTED") {
					$process->{hung} = $TRUE;
				}

				# Store the last processed byte of the log
				$process->{$output . "_last_byte"}=tell(LOG);
				
		    } else {
		    	# Abandon the echoing for this file. 
		    	# It looks like all we can see is a partial line of output.
		    	# If we were to echo the partial line then we risk injecting the process mnemonic
		    	# part way through a line.
		    	last;
		    }
		}

		close LOG;
	}
}

#----------------------------------------------------------------------------------#
# kill_process
#
# This is an intermal subroutine that terminates a process, it first attempts to stop the process 
# and then kills the process if it is still running. If the process is terminated the process 
# object "kill" attribute is set to 1, otherwise 0.
#
# Usage:
#  $self->kill_process($process1);
#
# Arguments:
#  $process = A process object
#
# Returns:
#  0 for pass and 1 for failure
#----------------------------------------------------------------------------------#
sub kill_process {
    my ($self, $process) = @_;

	my $rc;

	my $ppid = $process->{pid};

	$process->{killed} = $FALSE;
        
	unless ($process->poll()) {
		info("  o Process $process->{uid} pid $ppid is not running");
		return;
	}

	info("  o Process $process->{uid} pid $ppid stop()");
	$process->stop();

	if($process->poll()) {
		info("  o Process $process->{uid} pid $ppid terminate()");
		$process->terminate();
	}

	if($process->poll()) {
		if ($^O eq 'os390') {
			# Z/OS has another more forcefull kill option to try.
			if ($process->poll()) {
				sleep(5); # The kill -K doesn't work unless you give it some time after a kill -9. See the Z/OS documentation.
				
				info("  o kill -9 didn't work. Trying kill -K");
				
				my $kill_command = "kill -K $ppid";

				$self->start_process(
					mnemonic => "KILL",
					logName	 => $process->{logdir} . $delimiter . "kill_k",
					command  => $kill_command,
					runtime  => 300
				);
			}
		}
	}

	if($process->poll()) {
		info("  o Process $process->{uid} terminate() pid $ppid didn't work, manual cleanup required");
	}
	else {
		info("  o Process $process->{uid} pid $ppid killed");
		$process->{killed} = $TRUE;
	}

	# Echo any remaining stdout/stderr
	$self->process_logs($process);
	
	if ($process->{killed}) {
		return 0;
	}
	else {
		return 1;
	}
}

#----------------------------------------------------------------------------------#
# kill_processes
#
# This is an external subroutine that terminates a process or a list of process, it calls the 
# kill_process internally, see it's description for further info.
#
# Usage:
#  my $rc = stf::Commands->kill_processes($process)
#  my $rc = stf::Commands->kill_processes(%list);
#
# Arguments:
#  $process/%list = A process object or a list of process objects
#
# Returns:
#  0 for pass and 1 for failure
#----------------------------------------------------------------------------------#
sub kill_processes {
	my $self = shift;

	my %process_list;

	# Iterate through the parsed arguments to ensure that we are given a valid object 
	# or a list of objects, this approach allows for the end-user call the kill_processes 
	# directly with either a single object, an array of objects or a hash of objects.
	foreach $obj (@_) {
		if (ref ($obj) =~ "stf::ProcessMgmt:") {
			$process_list{$obj->{uid}} = $obj;
		}
	}

	unless(keys %process_list) {
		warn "Internal error: No process ids supplied to kill_processes";
		
		return 1;
	}

 	warn("Killing processes: " . join(" ", sort keys %process_list));

	# Expect all processes to be killed, switch 
	# to false if it fails to kill a process
	my $all_processes_killed = $TRUE;
	while ((my $uid, my $process) = each(%process_list)) {

		$all_processes_killed = $FALSE if ($self->kill_process($process));
	}

	if ($all_processes_killed) {
		return 0;
	}
	else {
		return 1;
	}
}

#----------------------------------------------------------------------------------#
# run_process
#
# This is an external method to run a command or a series of commands, they will run in parallel and
# be monitored (for dumps and timeout) to completion.  It calls start_processes and monitor_processes 
# internally, see their description for further info.
# If the process does not complete before it's time limit it will be left running and we return 1. 
#
# Usage:
#  my ($rc, $process) = stf::Commands->run_process({mnemonic => "Java", command => "java", args => @args, logName => $logName});
# 
# Arguments:
#  A hash or a list of hashes where each hash contains the process args required to instanciate a process object
#
# Returns:
#  $rc = 0 for pass and 1 for failure
#
# Notes:
#  See the start_process description for the arguments required to start a process
#----------------------------------------------------------------------------------#
sub run_process {
	my ($self, @commands) = @_;

	my $status = 0;

	my ($rc, $process) = $self->start_process(@commands);

	$status = $rc unless ($rc == 0);
        
	$rc = $self->monitor_processes($process);

	$status = $rc unless ($rc == 0);

	return ($status, $process);
}


#----------------------------------------------------------------------------------#
# run_processes
#
# This is an external method to run multiple instances of the same command, which will run in parallel and
# be monitored (for dumps and timeout) to completion.  It calls start_processes and monitor_processes 
# internally, see their description for further info.
# If one or more of the process does not complete before it's time limit it will be left running and we return 1. 
#
# Usage:
#  my ($rc, %process_list) = stf::Commands->run_processes({mnemonic => "Java", command => "java", args => @args, logName => $logName});
# 
# Arguments:
#  A hash or a list of hashes where each hash contains the process args required to instanciate a process object
#
# Returns:
#  $rc = 0 for pass and 1 for failure
#
# Notes:
#  See the start_process description for the arguments required to start a process
#----------------------------------------------------------------------------------#
sub run_processes {
	my ($self, @commands) = @_;

	trace("run_processes subroutine - \@commands: " . Dumper(\@commands));

	my $status = 0;

	my ($rc, %process_list) = $self->start_processes(@commands);

	$status = $rc unless ($rc == 0);
        
	$rc = $self->monitor_processes(%process_list);

	$status = $rc unless ($rc == 0);

	return ($status, %process_list);
}

#----------------------------------------------------------------------------------#
# create_dumps
# 
# This is an external subroutine that attempts to create dumps. 
#
# Usage:
#  stf::Commands->create_dumps($process);
#
# Arguments:
#  $process = A process object
#----------------------------------------------------------------------------------#
sub create_dumps {
    my ($self, $process) = @_;
	my $attempts    = 3;
	my $interval    = "30"; 	# to allow javacore/core file to be written out.

	warn("Collecting dumps for: " . $process->{mnemonic});
	
	REPEAT: for $attempt (1..$attempts) {			
		$self->generate_process_diagnostics($process);

		info("Pausing for " . $interval . " seconds");					
		sleep $interval;
		
		if ($process->get_state eq "completed") {
			info("Process " . $process->{mnemonic} . " (pid " . $process->{pid} . ") is no longer running. Abandoning dump collection.");
			return;
		}
	}

	$self->generate_OS_dumps($process);
}

#----------------------------------------------------------------------------------#
# generate_process_diagnostics
# 
# This subroutine generates process diagnostics.
# On Windows it uses jcmd for J9 jdks if it is available, otherwise procdump,
# to generate .DMP files. For the other platforms it uses kill signals to generate
# javacore/core files.
#
# Usage:
#  $self->generate_process_diagnostics($process);
#
# Arguments:
#  $process = A process object
#----------------------------------------------------------------------------------#
sub generate_process_diagnostics {
    my ($self, $process) = @_;
	my $platform = stf::stfUtility->getPlatform();

	# If on Windows and running an OpenJ9 jdk, use jcmd to generate .DMP files,
	# otherwise use procdump.exe.
	# If not on Windows try generating javacores using kill signals.
	
	if ($platform eq "win") {
		my $java;
		my $jcmd;
		FIND_JAVA: foreach my $dir (split(/;/,$ENV{PATH})) {
			$java = catfile($dir, 'java.exe');
			$jcmd = catfile($dir, 'jcmd.exe');
			if (-e $java) {
				last FIND_JAVA;
			}
		}
		
		my %getJavaProperties_options = ();
		$getJavaProperties_options{'PATH'} = $command_line_options{$java};
		%java_properties = stf::stfUtility->getJavaProperties(%getJavaProperties_options);

		if (-f $jcmd && $java_properties{'java.vm.name'} =~ /J9/ ) {
			# Use jcmd to generate dumps
			info("Using jcmd.exe to generate .DMP files");
			my $command = catfile("$sysinternals_dir", "procdump" . $bits . ".exe" );
			my $output_file = $process->{logName} . ".jvmdump";

			my $dump_args = [$process->{pid}, "Dump.java"];
			$self->start_process(
				mnemonic => "JCMD1",
				command  => $jcmd,
				args     => $dump_args,
				echo	 => $TRUE,
				logName  => $output_file
			);

			$dump_args = [$process->{pid}, "Dump.system"];
			$self->start_process(
				mnemonic => "JCMD2",
				command  => $jcmd,
				args     => $dump_args,
				echo	 => $TRUE,
				logName  => $output_file
			);
		} else {
			# Use procdump to generate dumps
			# procdump.exe only works on Vista or later
			# Best way to work this out appears to be the build number
			# Vista is 6000, so look for something smaller
			#   C:\WINNT\system32>wmic os get BuildNumber
			#   BuildNumber
			#   3790
			my $output = `echo foo | wmic os get BuildNumber 2>&1`;
			if ($output =~ /BuildNumber[^0-9]+(\d+)/s) {
				my $buildNumber = $1;
				if ($buildNumber < 6000) {
					info("Not able to generate javacores/core files on Windows builds earlier than Vista");
					return;
				}
			}
			# Determine if we need to run the 32 or 64 bit version by looking at the java build
			# if the first java on the PATH has a jre/lib/AMD64 directory then run the 64bit version
			my $bits = '';
			my $amd64_dir = catfile($java, '..', '..', 'lib', 'amd64');
			if (-e $amd64_dir) {
				$bits='64';
			}

			my $sysinternals_dir = $ENV{'WINDOWS_SYSINTERNALS_ROOT'};
			my $command 	 = catfile("$sysinternals_dir", "procdump" . $bits . ".exe" );
			my $dump_args 	 = ["-accepteula", "-ma", $process->{pid}];
			my $output_file  = $process->{logName} . ".jvmdump";
	
			info("Using procdump.exe to generate .DMP files");
			$self->start_process(
				mnemonic => "PROC",
				command  => $command,
				args     => $dump_args,
				echo	 => $TRUE,
				logName  => $output_file
			);
		}
	} else {
  		info("Sending SIG 3 to the java process to generate a javacore");
  		$self->send_signal($process, 3);     
	}
}

#----------------------------------------------------------------------------------#
# generate_OS_dumps
# 
# This subroutine generates OS core dumps on non-windows platforms.
#
# Usage:
#  $self->generate_OS_dumps($process);
#
# Arguments:
#  $process = A process object
#----------------------------------------------------------------------------------#
sub generate_OS_dumps {
    my ($self, $process) = @_;
	my $platform = stf::stfUtility->getPlatform();
	
	if ($platform eq "win") {
		return; # Currently there's no way of generating an OS core on Windows 
 	} else {
     	info("Sending SIGABRT (kill -6) to the java process to generate a core");
		$self->send_signal($process, 6);

		info("Pausing for 30 seconds");		
		sleep 30;

    	# Add another level of kill.  If the generation of the javacore has hung, then the 
    	# SIGABRT won't process, and we need to use the OS in order to generate a dump.
    	info("Sending SIGXCPU (kill -24) to the java process to generate an OS dump");
		$self->send_signal($process, 24);
   	}
}

#----------------------------------------------------------------------------------#
# send_signal
# 
# This subroutine sends kill signals to a non-windows process.
#
# Usage:
#  $self->send_signal($process, $signal);
#
# Arguments:
#  $process = A process object
#  $signal  = kill signal
#----------------------------------------------------------------------------------#
sub send_signal {
    my ($self, $process, $signal) = @_;	
	my $command     = "kill -" . $signal . " " . $process->{pid};
	my $output_file = $process->{logName} . ".kill_" . $signal;
	my $platform 	= stf::stfUtility->getPlatform();

	if ($platform ne "win") {
	   	$self->start_process(
			mnemonic => "KILL",
			command  => $command,
			echo     => $TRUE,
			logName  => $output_file
		);
	}
}

#----------------------------------------------------------------------------------#
# _heartbeat
# 
# This is an internal subroutine used to produce an heartbeat message to stdout 
# indicating that process X is still running. 
#
# Usage:
#  _heartbeat($process);
#
# Arguments:
#  $process = A process object
#----------------------------------------------------------------------------------#
sub _heartbeat {
    my $process = shift;
    
  	if ($heartbeat) {
		my $timedif = $nextbeat - time;
		
		if ($timedif <= 0) {
			# autoflush the heart beat to prevent axxon timeouts, as windows + perl
			# sometimes buffers output
			local $| = 1;
			info("Heartbeat: Process $process->{uid} is still running");
			$nextbeat = time + $heartbeat_period; #HEARTBEAT_PERIOD;
		}
	}
}
1;
        
