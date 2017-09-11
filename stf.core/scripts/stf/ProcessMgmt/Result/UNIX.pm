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

package stf::ProcessMgmt::Result::UNIX;

use base qw{stf::ProcessMgmt::Result};

use strict;

use warnings;

sub new
{
	my ($class,$rc) = @_;
	
	die "No return code supplied" unless defined $rc;
	
	my $self = {};
	
	bless $self => $class;
    
    $self->{return_code} = $rc;
    
    $self->{core_dumped} = $rc & 0x80;
    
    $self->{code} = $rc >> 8;
    
    $self->{signal} = $rc & 0x7f;
	
	return $self;
}

sub return_code
{
	my $self = shift;
	
	return $self->{return_code};
}

sub exit_code
{
	my $self = shift;
	
	return $self->{code};
}

sub signal
{
	my $self = shift;
	
	return $self->{signal};
}

sub core_dumped
{
	my $self = shift;
	
	return $self->{core_dumped};
}

sub message
{
	return 'UNIX process status codes do not have corresponding messages';
}

sub get_summary
{
	my $self = shift;

	my $rc = $self->return_code();
	my $hex_string = sprintf("%04x",$rc);
	
	my $exit_code = $self->exit_code();
	my $core_str = ($self->core_dumped()) ? 'Core bit set' : 'Core bit not set';
	
	my $signal = $self->signal();
	
	my $signal_string = ($signal == 0) ? 'No signal received.' : "Signal $signal received.";
	
	return << "SUMMARY";
The 16 bit process exit status on UNIX encodes several pieces of data:

The top (most significant) 8 bits are the application exit code 
as passed to the exit() function in the program code. For Java, this may 
be a value passed to System.exit() by the application or produced by 
the VM itself.

The 8th bit is a flag indicating whether a core was dumped.

The bottom 7 bits are the signal number sent to the process - 0 indicates 
no signal was sent.

When a signal is received by the JVM, the behaviour seems to be different
depending on whether the VM is capable of handling the signal. If it can
handle the signal it sets the application exit code and the signal number
and core bit are set to 0.

Only if the JVM can't or doesn't handle the signal (a sig 9 or sig 24 for 
example) do the signal number and core bit get set.

Process status code was: $rc (in hex: $hex_string)
Status code break down is:
Exit code: $exit_code
$core_str
$signal_string
SUMMARY

}


1;
