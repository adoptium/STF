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

package stf::ProcessMgmt::UNIX;

use base qw{stf::ProcessMgmt};

use strict;

use warnings;

use stf::ProcessMgmt::Result;

use POSIX "sys_wait_h";

use File::Spec::Functions qw{catfile};

use Time::Local;


#Method called in the constructor to qualify the command.
#
#Will take an unqualified command name (e.g. java) and produce a qualified
#command (such as c:\Program Files\IBM\Java5\jre\bin\java.exe)
sub _qualify_command {
	my $self = shift;

	my $cmd = $self->{command};

#The command could be qualified already - if it points to a file that exists then finish
	return if -f $cmd;

	my @path_elements = split /[:]/, $ENV{PATH};

	foreach my $this_element (@path_elements) {
		my $potential_file = catfile( $this_element, $cmd );

		if ( -f $potential_file ) {
			$self->{command} = $potential_file;

			return;
		}
	}

	warn "Error: cannot find command $cmd on PATH. PATH is $ENV{PATH}";
}

sub _recreate_internal_structure
{
}

sub start {
	my $self = shift;
	my $debug = 0;

	# Create a starttime object attribute
	$self->{starttime} = timelocal(localtime());

	if ( $self->{state} != stf::ProcessMgmt::STATE_UNSTARTED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn
"Error: can only start an unstarted process. Current state is: $state_name";

		return;
	}

#To avoid tripping up tests which expect pristine STDERR. We output debug elsewhere..

	if ( defined $self->{stddbg} && $self->{stddbg} ne '' ) {
		$debug=1;
		#Open the new stream for append
		open( STDDBG, '>>', $self->{stddbg} )
		  or die "Cannot open new stddbg $self->{stddbg}: $!";

		#Enable autoflush
		select STDDBG;
		$| = 1;
	}

	if ( defined $self->{debug}) {
		$debug = $self->{debug};
	}

#To redirect the streams on UNIX we are going to open our I/O streams to the
#target streams, spawn the process and then switch back to our originals for the parent.
#

	#Save our current streams if we need to
	local ( *OLDIN, *OLDERR, *OLDOUT );

	if ( defined $self->{stdin} && $self->{stdin} ne '' ) {
		open( OLDIN, "<&STDIN" ) or die "Cannot save current STDIN: $!";

		#Create the stdin file if it doesn't already exist
		$self->_touch_file( $self->{stdin} );

		#Reopen STDIN from the stdin file
		open( STDIN, '<', $self->{stdin} )
		  or die "Cannot open new stdin $self->{stdin}: $!";
	}
	#  Do stdout LAST because it remains selected as default
	if ( defined $self->{stderr} && $self->{stderr} ne '' ) {
		open( OLDERR, ">&STDERR" ) or die "Cannot save current STDERR: $!";

		#Open the new stream for append
		open( STDERR, '>>', $self->{stderr} )
		  or die "Cannot open new stderr $self->{stderr}: $!";

		#Enable autoflush
		select STDERR;
		$| = 1;
	}

	if ( defined $self->{stdout} && $self->{stdout} ne '' ) {
		open( OLDOUT, ">&STDOUT" ) or die "Cannot save current STDOUT: $!";

		#Open the new stream for append
		open( STDOUT, '>>', $self->{stdout} )
		  or die "Cannot open new stdout $self->{stdout}: $!";

		#Enable autoflush
		select STDOUT;
		$| = 1;
	}

	#Now fork a process

	my $pid = fork();

	if ( $pid > 0 ) {

		#This is the path taken by the parent

#Reset all our streams back if we changed them
#The error messages are a bit difficult because we have no way of reporting them
#If nothing else they make the code a bit easier to understand
		if ( defined $self->{stdin} && $self->{stdin} ne '' ) {
			open( STDIN, "<&OLDIN" ) or die "Cannot reset STDIN: $!";
		}

		if ( defined $self->{stdout} && $self->{stdout} ne '' ) {
			open( STDOUT, ">&OLDOUT" ) or die "Cannot reset STDOUT: $!";
		}

		if ( defined $self->{stderr} && $self->{stderr} ne '' ) {
			open( STDERR, ">&OLDERR" ) or die "Cannot reset STDERR: $!";
		}

		#Store the pid
		$self->{pid} = $pid;
		
		#Change the state
		$self->{state} = stf::ProcessMgmt::STATE_STARTED;

		return 1;
	}
	elsif ( defined $pid ) {

		#This is the path taken by the child

		my @args;

		if ( defined $self->{args} ) {
			@args = @{ $self->{args} };
		}

		my $command = $self->{command};
		
		if ( defined $self->{affinity} )
		{
			if ($^O eq 'linux')
			{
				unshift(@args, $self->{affinity}, $command);
				$command = "taskset";					
			} elsif ($^O eq 'aix')
			{
				my @available_processors = `bindprocessor -q`;
				if($available_processors[0] =~ /\b$self->{affinity}\b/)
				{
					# Requested processor was available in the list reported by the system
					`bindprocessor $$ $self->{affinity}`;
					if($? != 0)
					{
						die "Couldn't bind process $$ to requested processor $self->{affinity}. Error : $!";
					}
				}				
			} elsif ($^O eq 'os390')
			{
				#Dunno yet either
			} else
			{
				print "\n\n####################\nSetting processor affinity on $^O is not yet supported\n####################\n\n";
			}
		}

		if ( $debug ) {
			print STDDBG "exec($command @args\n)"; # Output did not even reveal the command we actually invoked
		}

		# Exec will not return if it works. If it doesn't work we need to print a message and die.
		if ( $self->{dontJoinArgs} )
		{
			exec($command, @args) or do {

				#Sample the error code ($! will get overridden by open())
				my $err_code = $!;
	
				#Reset the error stream
				if ( defined $self->{stderr} && $self->{stderr} ne '' ) {
					open( STDERR, ">&OLDERR" ) or die "Cannot reset STDERR: $!";
				}
	
				die "Could not execute command $command: $err_code";
			};			
		} else
		{
			$command = $self->{command} . ' ' . join( ' ', @args );
			exec($command) or do {

				#Sample the error code ($! will get overridden by open())
				my $err_code = $!;
	
				#Reset the error stream
				if ( defined $self->{stderr} && $self->{stderr} ne '' ) {
					open( STDERR, ">&OLDERR" ) or die "Cannot reset STDERR: $!";
				}
	
				die "Could not execute command $command: $err_code";
			};
		}
		my $print_command  = join( " ", $command, @args );
		print "stf::ProcessMgmt::Unix.pm: Running command\n";
		print "$print_command \n";

	}
	else {

		#This is the path taken if the fork didn't work
		#Reset our streams, print a warning and return undef

		if ( defined $self->{stdin} && $self->{stdin} ne '' ) {
			open( STDIN, "<&OLDIN" ) or die "Cannot reset STDIN: $!";
		}

		if ( defined $self->{stdout} && $self->{stdout} ne '' ) {
			open( STDOUT, ">&OLDOUT" ) or die "Cannot reset STDOUT: $!";
		}

		if ( defined $self->{stderr} && $self->{stderr} ne '' ) {
			open( STDERR, ">&OLDERR" ) or die "Cannot reset STDERR: $!";
		}

		$self->{state} = stf::ProcessMgmt::STATE_ERROR;

		$self->{err_msg} = "Couldn't fork";

		return undef;
	}
}

sub stop {
	my $self = shift;
	
	if ( $self->{state} != stf::ProcessMgmt::STATE_STARTED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn
"Error: can only stop a started process. Current state is: $state_name";

		return undef;
	}
	
	my $pid = $self->{pid};
	
	my $kill_result = kill(1, $pid);
	
	if($kill_result > 0)
	{
		#The kill signalled something
		
		#Do a wait to get the process status
		#If it doesn't die in 10 seconds we'll return false
		return $self->wait(10);
	}
	else
	{
		#The kill didn't signal anything
		warn "Kill didn't signal any process (aimed at PID = $pid)";
		
		return undef;
	}
}

sub terminate {
	my $self = shift;
	
	if ( $self->{state} != stf::ProcessMgmt::STATE_STARTED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn
"Error: can only terminated a started process. Current state is: $state_name";

		return undef;
	}
	
	my $pid = $self->{pid};
	
	my $kill_result = kill(9, $pid);
	
	if($kill_result > 0)
	{
		#The kill signalled something
		
		#Do a wait to get the process status
		return $self->wait(10);
	}
	else
	{
		#The kill didn't signal anything
		warn "Kill didn't signal any process (aimed at PID = $pid)";
		
		return undef;
	}
}

sub poll {
	my $self = shift;

	if($self->{state} == stf::ProcessMgmt::STATE_COMPLETED)
	{
		#This process has already died
		return undef;
	}
	elsif($self->{state} == stf::ProcessMgmt::STATE_ERROR)
	{
		#We're in error - say the process has finished
		return undef;
	}
	elsif($self->{state} == stf::ProcessMgmt::STATE_UNSTARTED)
	{
		warn "You can't poll an unstarted process.";
		
		return undef;
	}

	my $wait_result = waitpid( $self->{pid}, &WNOHANG );

	if ( $wait_result == 0 ) {

		# A 0 return code means "timeout" for waitpid with WNOHANG,
		# implying that the child process is alive and still running.
		# We can exit with "alive" as a return code.
		return 1;
	}
	elsif ( $wait_result == -1 ) {

		# Shroedinger's cat . . .
		# We have no child processes at all. This does NOT mean
		# that the process we're polling for is alive or dead - we
		# need to do a kill -0 further down to find that out.
	}
	else {

		# waitpid returned something other than 0 or -1, so
		# the process was a child of ours, but is now dead.
		# We can return with a "dead" return code.
		# process has finished. save away its vital statistix.
		$self->{result} = new stf::ProcessMgmt::Result($?);

		$self->{state} = stf::ProcessMgmt::STATE_COMPLETED;

		return undef;
	}

	# If we're here, then the waitpid was not informative.
	# This means we must use a kill -0 to test if the process
	# is still around.
	my $res = kill 0, $self->{pid};
	if ( $res == 1 ) {

		# We signalled one process successfully. This means that
		# the process must be alive in some sense. It could be a
		# zombie if it's not one of our children. (If it was one
		# our children, the waitpid further up would've reaped it).
		return 1;
	}
	elsif ( ( $res == 0 ) && ( $! == 1 ) ) {

		# EPERM = 1
		# The process was not signalled because we don't have
		# permission to send signals to it. It must be alive
		# or a zombie as detailed above.
		return 1;
	}
	elsif ( ( $res == 0 ) && ( $! == 3 ) ) {

		# ESRCH = 3
		# No process was found, therefore the process we're
		# looking for must be dead.

 #However, we have no means of getting the exit code so we'll call this an error
 #and warn people
		warn
"Error polling process $self->{pid}. Cannot find process completion code.";
		$self->{state} = stf::ProcessMgmt::STATE_ERROR;
		return undef;
	}
	else {

		# Something else has gone wrong :( Not sure how this
		# could happen.
		warn "Unknown error polling process $self->{pid} : $!";
		$self->{state} = stf::ProcessMgmt::STATE_ERROR;

		return undef;
	}
}

sub wait {
	my ( $self, $wait_time ) = @_;

	if ( $self->{state} != stf::ProcessMgmt::STATE_STARTED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn
"Error: can only wait on a started process. Current state is: $state_name";

		return 1;
	}

	if ( defined $wait_time ) {
		$wait_time = int $wait_time;

		my $start_time   = time();
		my $give_up_time = $start_time + $wait_time;

		while ( time() < $give_up_time ) {

			# If we've finished quit polling
			last unless $self->poll();

			my $seconds_been_waiting = time() - $start_time;

			if ( $seconds_been_waiting < 120 ) {

				# We've been waiting for less than 2 minutes
				sleep(1);
			}
			elsif ( $seconds_been_waiting < 3600 ) {

				# We've been waiting for less than an hour
				sleep(10);
			}
			else {

				# If we've been waiting for more than an hour already
				# wait another minute before checking again
				sleep(60);
			}
		}
		# check if process has completed
		if ( $self->{state} == stf::ProcessMgmt::STATE_COMPLETED ) {
			
			return 1;
		} else {
			
			return undef;
		}
	}
	else {

		#Hard wait
		my $rc = waitpid( $self->{pid}, 0 );

		if ( $rc == -1 ) {
			warn
"Error: process being waited for ($self->{pid}) is not a child of the current process";

			return undef;
		}
		elsif ( $rc == 0 ) {
			warn
"Error: process being waited for ($self->{pid}) didn't return from an infinite wait.";

			return undef;
		}
		else {

			#We must have finished
			$self->{result} = new stf::ProcessMgmt::Result($?);

			$self->{state} = stf::ProcessMgmt::STATE_COMPLETED;

			return 1;
		}
	}
}

sub err_msg {
	my $self = shift;
	
	return $self->{err_msg};
}

sub get_result {
	my $self = shift;
	
	if ( $self->{state} != stf::ProcessMgmt::STATE_COMPLETED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn "Error: can only get result from a completed process. Current state is: $state_name";

		return undef;
	}
	
	return $self->{result};
}

sub get_state {
	my $self = shift;
	
	$self->poll();
	
	return stf::ProcessMgmt::_get_description_for_state($self->{state});
}

sub pid {
	my $self = shift;
	
	return $self->{pid};
}

1;

__END__

=head1 NAME

stf::ProcessMgmt::UNIX - the UNIX-specific implementation of the ProcessMgmt interface

=head1 DESCRIPTION

Contains all the logic for creating and managing processes on UNIX.

You should not try to create this directly - you should be using the stf::ProcessMgmt class instead.

=cut
