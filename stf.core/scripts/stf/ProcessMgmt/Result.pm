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

package stf::ProcessMgmt::Result;

use strict;

use warnings;

sub new
{
	my $class = shift;
	
	my @args = @_;
	
	#Return the appropriate subclass
	if($^O eq 'MSWin32')
	{
		require stf::ProcessMgmt::Result::Windows;
		
		return new stf::ProcessMgmt::Result::Windows(@args);
	}
	else
	{
		require stf::ProcessMgmt::Result::UNIX;
		
		return new stf::ProcessMgmt::Result::UNIX(@args);
	}
}

#We create default implementations of all the methods that puke
#to define the interface and catch coding screw-ups later.

sub return_code
{
	die "return_code not implemented";
}

sub exit_code
{
	die "exit_code not implemented";
}

sub signal
{
	die "signal not implemented";
}

sub core_dumped
{
	die "core_dumped not implemented";
}

sub message
{
	die "message not implemented";
}

sub get_summary
{
	die "get_summary not implemented";
}

1;

__END__

=head1 NAME

stf::ProcessMgmt::Result - object representing a test result

=head1 SYNOPSIS

	use stf::ProcessMgmt::Result;
	
	my $result = new stf::ProcessMgmt::Result($?);
	
	print $result->get_summary();

=head1 DESCRIPTION

stf::ProcessMgmt::Result encapsulates the knowledge of how process return codes are interpretted on a
particular platform.

There are big differences in what information is encoded in the process return code between Windows
and UNIX. For Windows, the process return code is the integer passed to the exit() function call. Some
windows exit codes have special meanings which are called the "exit message". Note that this messages
are only meaningful if the application is playing along.

=head1 METHODS

=over 4

=item new

Default constructor.

Takes the process return code as its only argument.

=item return_code

Returns the unedited return code - i.e. what fell out of the process at the end of the run.

=item exit_code

Returns the process exit code - what was passed to System.exit() or the exit() library call.

=item signal

On UNIX, if the process was terminated with a signal that wasn't handled this returns the signal number.
If a signal was not returned or we are running on Windows we return undef.

=item core_dumped

On UNIX, if the process was terminated with a signal that caused the process to dump core, this method
returns true. If the core_dumped bit is not set or we are on Windows we return undef.

=item message

Returns the exit message for the particular exit code on Windows. On UNIX returns ''.

=item get_summary

Returns a text string containing a summary of the return code and its meaning.

=back

=cut
