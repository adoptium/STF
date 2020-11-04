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
# stf::ProcessMgmt::Windows - the Windows-specific implementation of the ProcessMgmt interface
# Contains all the logic for creating and managing processes on Windows.
# You should not try to create this directly - you should be using the stf::ProcessMgmt class instead.

package stf::ProcessMgmt::Windows;

use base qw{stf::ProcessMgmt};

use strict;

use warnings;

use Win32::API;

use Win32::Process; #Specifies the constant INFINITE we need for waiting

use File::Spec::Functions qw{catfile};

use Time::Local;

use stf::ProcessMgmt::Result;

use Config;

#Function prototypes
sub _open_file;

#A few useful constants

use constant GENERIC_READ  => 0x80000000;
use constant GENERIC_WRITE => 0x40000000;

use constant FILE_ATTRIBUTE_NORMAL => 128;

use constant FILE_SHARE_READ  => 1;
use constant FILE_SHARE_WRITE => 2;

use constant FILE_BEGIN   => 0;
use constant FILE_CURRENT => 1;
use constant FILE_END     => 2;

use constant OPEN_EXISTING => 3;
use constant OPEN_ALWAYS   => 4;

use constant HANDLE_FLAG_INHERIT            => 0x00000001;
use constant HANDLE_FLAG_PROTECT_FROM_CLOSE => 0x00000002;

use constant STD_INPUT_HANDLE  => -10;
use constant STD_OUTPUT_HANDLE => -11;
use constant STD_ERROR_HANDLE  => -12;

use constant SECURITY_WORLD_SID_AUTHORITY => "\0\0\0\0\0\1";

use constant ACL_REVISION => 2;

use constant OBJECT_INHERIT_ACE       => 0x1;
use constant CONTAINER_INHERIT_ACE    => 0x2;
use constant NO_PROPAGATE_INHERIT_ACE => 0x4;
use constant INHERIT_ONLY_ACE         => 0x8;
use constant INHERITED_ACE            => 0x10;
use constant VALID_INHERIT_FLAGS      => 0x1F;

use constant STANDARD_RIGHTS_REQUIRED => 0x000F0000;
use constant SYNCHRONIZE              => 0x00100000;

use constant FILE_ALL_ACCESS =>
  ( STANDARD_RIGHTS_REQUIRED | SYNCHRONIZE | 0x3FF );

use constant SECURITY_DESCRIPTOR_REVISION => 1;

use constant PROCESS_ALL_ACCESS => 0x001F0FFF;

#Define the various Win32 API calls we need to control processes

my $CreateFile = new Win32::API(
	$ENV{'SystemRoot'} . '\system32\kernel32.dll', 'CreateFile',
	[ 'P', 'N', 'N', 'P', 'N', 'N', 'N' ], 'N'
  )
  or die "Can't import API CreateFile: $!\n";

my $SetFilePointer =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\kernel32.dll',
	'SetFilePointer', [ 'N', 'N', 'N', 'N' ], 'N' )
  or die "Can't import API SetFilePointer: $!\n";

my $SetHandleInformation =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\kernel32.dll',
	'SetHandleInformation', [ 'N', 'N', 'N' ], 'N' )
  or die "Can't import API SetHandleInformation: $!\n";

my $GetStdHandle =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\kernel32.dll',
	'GetStdHandle', ['N'], 'N' )
  or die "Can't import API GetStdHandle: $!\n";

my $CloseHandle = new Win32::API( $ENV{"SystemRoot"} . '\system32\kernel32.dll',
	'CloseHandle', ['N'], 'N' )
  or die "Can't import API CloseHandle: $!\n";

my $CreateProcess = new Win32::API(
	$ENV{'SystemRoot'} . '\system32\kernel32.dll', 'CreateProcess',
	[ 'P', 'P', 'P', 'P', 'N', 'N', 'P', 'P', 'P', 'P' ], 'N'
  )
  or die "Can't import API CreateProcess: $!\n";
  
my $SetProcessAffinityMask = new Win32::API(
	$ENV{'SystemRoot'} . '\system32\kernel32.dll', 'SetProcessAffinityMask',
	[ 'N', 'N' ], 'N'
  	)
  	or die "Can't import API SetProcessAffinityMask: $!\n";

my $GetProcessAffinityMask = new Win32::API(
	$ENV{'SystemRoot'} . '\system32\kernel32.dll', 'GetProcessAffinityMask',
	[ 'N', 'P', 'P' ], 'N'
  	)
  	or die "Can't import API GetProcessAffinityMask: $!\n";

my $AddAccessAllowedAceEx =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\advapi32.dll',
	'AddAccessAllowedAceEx', [ 'P', 'N', 'N', 'N', 'P' ], 'N' )
  or die "Can't import API AddAccessAllowedAceEx: $!\n";

my $InitializeAcl =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\advapi32.dll',
	'InitializeAcl', [ 'P', 'N', 'N' ], 'N' )
  or die "Can't import API InitializeAcl: $!\n";

my $InitializeSecurityDescriptor = new Win32::API(
	$ENV{'SystemRoot'} . '\system32\advapi32.dll',
	'InitializeSecurityDescriptor',
	[ 'P', 'N' ], 'N'
  )
  or die "Can't import API InitializeSecurityDescriptor: $!\n";

my $SetSecurityDescriptorDacl =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\advapi32.dll',
	'SetSecurityDescriptorDacl', [ 'P', 'N', 'P', 'N' ], 'N' )
  or die "Can't import API SetSecurityDescriptorDacl: $!\n";

my $TerminateProcess =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\kernel32.dll',
	'TerminateProcess', [ 'N', 'N' ], 'N' )
  or die "Can't import API TerminateProcess: $!\n";

my $GetExitCodeProcess =
  new Win32::API( $ENV{'SystemRoot'} . '\system32\kernel32.dll',
	'GetExitCodeProcess', [ 'N', 'P' ], 'N' )
  or die "Can't import API GetExitCodeProcess: $!\n";
  
my $WaitForSingleObject = new Win32::API
(
  $ENV{'SystemRoot'}.'\system32\kernel32.dll',
  'WaitForSingleObject',
  ['N','N'],
  'N'
) or die "Can't import API WaitForInputIdle: $!\n";

my $OpenProcess = new Win32::API
(
  $ENV{'SystemRoot'}.'\system32\kernel32.dll',
  'OpenProcess',
  ['N','N','N'],
  'N'
) or die "Can't import API WaitForInputIdle: $!\n";

#Constructor is inherited

sub _recreate_internal_structure
{
	my $self = shift;
	
	#Convert PID to handle
	
	$self->{handle} = $OpenProcess->Call(PROCESS_ALL_ACCESS,0,$self->{pid});
}

sub start {
	my $self = shift;

	# Create a starttime object attribute
	$self->{starttime} = timelocal(localtime());

	if ( $self->{state} != stf::ProcessMgmt::STATE_UNSTARTED ) {
		my $state_name =
		  stf::ProcessMgmt::_get_description_for_state( $self->{state} );

		warn
"Error: can only start an unstarted process. Current state is: $state_name";

		return;
	}

	
	# Save STDIN, STDOUT, STDERR file handles
	open (STDIN_OLD, '<&STDIN');
	open (STDOUT_OLD, '>&STDOUT');
	open (STDERR_OLD, '>&STDERR');

	# Redirect STDIN, STDOUT, STDERR as required
	if ( defined( $self->{stdin} ) && $self->{stdin} ne '' ) {
	   open STDIN, "<$self->{stdin}" or die "Can't open " . $self->{stdin} . ": $!\n";
	}
	if ( defined( $self->{stdout} ) && $self->{stdout} ne '' ) {
	   open STDOUT, ">>$self->{stdout}" or die "Can't open " . $self->{stdout} . ": $!\n";
	}
	if ( defined( $self->{stderr} ) && $self->{stderr} ne '' ) {
	   open STDERR, ">>$self->{stderr}" or die "Can't open " . $self->{stderr} . ": $!\n";
	}

	my $StartupInfo = "\0" x 68;
	my $ProcessInformation = "\0" x 40;

	# Convert the command in to the short path (8:3) format
	# We use GetLongPathName inside this because GetShortPathName does
	# not appear to work on paths that are not in the correct case.
	# For some reason GetLongPathName works and fixes this!
	my $short_path_name =
	  Win32::GetShortPathName( Win32::GetLongPathName( $self->{command} ) );

	# If $short_path_name is the empty string then the file doesn't exist!
	if ( !$short_path_name ) {
		die "Command $self->{command} does not exist";
	}

	if ( !defined $self->{args} ) {
		$self->{args} = [];
	}

	my $long_path = Win32::GetLongPathName( $self->{command} ) . "\0";
	my $args      = join( " ", $short_path_name, @{ $self->{args} } ) . "\0";
	my $print_command  = join( " ", $long_path, @{ $self->{args} } );
    
	# print "stf::ProcessMgmt::Windows.pm: Running command\n";
	# print "$print_command \n";

    my $result;
    
    if ((defined $self->{new_console}) && ($self->{new_console} == 1))
    {
	   $result = $CreateProcess->Call(
		  $long_path,
		  $args,
		  0,    #Was undef and causing complaints
		  0,    #Was undef and causing complaints
		  1,
	      CREATE_NEW_CONSOLE,
		  0,
		  ".\0",
		  $StartupInfo,
		  $ProcessInformation,
	   );
    } else
    {
       $result = $CreateProcess->Call(
		  $long_path,
		  $args,
		  0,    #Was undef and causing complaints
		  0,    #Was undef and causing complaints
		  1,
	      0,
		  0,
		  ".\0",
		  $StartupInfo,
		  $ProcessInformation,
	   );
    }

	# Restore STDIN, STDOUT, STDERR
	open (STDIN, '<&STDIN_OLD');
	open (STDOUT, '>>&STDOUT_OLD');
	open (STDERR, '>>&STDERR_OLD');

	if ($result) {

		my $handle;
		my $pid;
		if($Config{ptrsize} == 8) {
			( $handle, $pid ) = unpack "Qx8L", $ProcessInformation;
		}
		else {
			( $handle, $pid ) = unpack "Lx4L", $ProcessInformation;
		}

		$self->{handle} = $handle;
		$self->{pid}    = $pid;
		
		if($self->{affinity})
		{
			my $processor_mask = "\0"x4;
			my $system_mask = "\0"x4;
			
			my $affinity_result = $GetProcessAffinityMask->Call($handle,$processor_mask,$system_mask);
		
			my $processor_mask_value = unpack("L",$processor_mask);
 			my $system_mask_value = unpack("L",$system_mask);			
			
			if ($self->{affinity} le $system_mask_value)
			{
				my $set_affinity_result = $SetProcessAffinityMask->Call($handle,$self->{affinity});
				
				if (!$set_affinity_result)
				{
					my $error_number = Win32::GetLastError();

					my $error_string = Win32::FormatMessage($error_number);

					if ( $error_number == 193 ) {
						$error_string =
			    			'Invalid Win32 application. If your command is a Perl '
						  . 'script you must use Perl as your command and your fully qualified '
						  . 'script as the first argument.';
					}

					$self->{err_msg} = $error_string;

					$self->{state} = stf::ProcessMgmt::STATE_ERROR;

					return undef;
				}
			}	
		}

		$self->{state} = stf::ProcessMgmt::STATE_STARTED;

		return 1;
	}
	else {
		my $error_number = Win32::GetLastError();

		my $error_string = Win32::FormatMessage($error_number);

		if ( $error_number == 193 ) {
			$error_string =
			    'Invalid Win32 application. If your command is a Perl '
			  . 'script you must use Perl as your command and your fully qualified '
			  . 'script as the first argument.';
		}

		$self->{err_msg} = $error_string;

		$self->{state} = stf::ProcessMgmt::STATE_ERROR;

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

		return;
	}

	if ( $TerminateProcess->Call( $self->{handle}, 9 ) ) {
		my $code = "\0" x 4;
		$GetExitCodeProcess->Call( $self->{handle}, $code );
		($code) = unpack "L", $code;

		$self->{result} = new stf::ProcessMgmt::Result($code);
		
		$self->{state} = stf::ProcessMgmt::STATE_COMPLETED;

		$CloseHandle->Call( $self->{handle} );

		return 1;
	}
	else {
		warn "Could not terminate process PID=$self->{pid}: "
		  . Win32::FormatMessage( Win32::GetLastError() );

		return undef;
	}
}

sub pid {
	my $self = shift;
	
	return $self->{pid};
}

#N.b. there is no difference between stop() and terminate() on Windows.
sub terminate {
	my $self = shift;

	$self->stop();
}

sub poll {
	my $self = shift;

	return !$self->wait(0);
}

sub wait {
	my ( $self, $wait_time ) = @_;
	
	if($self->{state} == stf::ProcessMgmt::STATE_COMPLETED)
	{
		#This process has already died
		return 1;
	}
	elsif($self->{state} == stf::ProcessMgmt::STATE_ERROR)
	{
		#We're in error - say the process has finished
		return 1;
	}
	elsif($self->{state} == stf::ProcessMgmt::STATE_UNSTARTED)
	{
		warn "You can't wait on an unstarted process.";
		
		return 1;
	}
	
	if ( defined $wait_time ) {
		$wait_time = 1000 * int $wait_time;
	}
	else {
		$wait_time = INFINITE;
	}

	if ( 0 == $WaitForSingleObject->Call( $self->{handle}, $wait_time ) ) {
		#Process ended - extract the result
		my $code = "\0" x 4;
        my $ret = $GetExitCodeProcess->Call( $self->{handle}, $code );
 		if($ret == 0)
		{
			warn "GetExitCodeProcess failed (rc==0). Artificially setting return code as -1";
			$code = -1;
		}
		else
		{
			($code) = unpack "L", $code;		
		}
		$self->{result} = new stf::ProcessMgmt::Result($code);
		$CloseHandle->Call( $self->{handle} );
		$self->{state} = stf::ProcessMgmt::STATE_COMPLETED;
		return 1;
	}
	else {
		#Process still running
		return undef;
	}
}

sub err_msg {
	my $self = shift;

	return $self->{err_msg};
}

sub get_result
{
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

#Method called in the constructor to qualify the command.
#
#Will take an unqualified command name (e.g. java) and produce a qualified
#command (such as c:\Program Files\IBM\Java5\jre\bin\java.exe)
sub _qualify_command {
	my $self = shift;

	my $cmd = $self->{command};

#The command could be qualified already - if it points to a file that exists then finish
	return if -f $cmd;

    my @suffixes = ('.exe','.com','.bat','.cmd','');

    foreach my $this_suffix (@suffixes) {
       my $potential_file = "$cmd$this_suffix";
       if ( -f $potential_file ) {
          $self->{command} = $potential_file;
          return;
       }
    }

# If it doesn't exist, look for it on the path

	my @path_elements = split /[;]/, $ENV{PATH};

	foreach my $this_element (@path_elements) {
		$this_element =~ s/"//g;
		foreach my $this_suffix (@suffixes)
		{
			my $potential_file = catfile( $this_element, $cmd.$this_suffix );
	
			if ( -f $potential_file ) {
				$self->{command} = $potential_file;
	
				return;
			}
		}
	}

	warn "Error: cannot find command $cmd on PATH. PATH is $ENV{PATH}";
}

# Open a a file using the Windows API with the file sharing attributes set.
#
sub _open_file {
	my ( $path, $mode ) = @_;

	my $handle = $CreateFile->Call(
		$path . "\0",    # Path of file
		( $mode eq 'rw' ? GENERIC_WRITE : GENERIC_READ ),    # File permissions
		FILE_SHARE_READ | FILE_SHARE_WRITE
		,       # We want FILE_SHARE_DELETE + FILE_SHARE_READ + FILE_SHARE_WRITE
		0,    # Default security descriptor
		( $mode eq 'rw' ? OPEN_ALWAYS : OPEN_EXISTING )
		,                         # If file exists append don't overwrite
		FILE_ATTRIBUTE_NORMAL,    # File attributes
		0                         # No template file
	) or return 0;

	# If we have opened the file for read-write move file pointer to the end
	# so that we append to the file.
	if ( $mode eq 'rw' ) {
		$SetFilePointer->Call( $handle, 0, 0, FILE_END );
	}

	$SetHandleInformation->Call( $handle, HANDLE_FLAG_INHERIT,
		HANDLE_FLAG_INHERIT )
	  or return 0;
	return $handle;
}

1;

__END__
