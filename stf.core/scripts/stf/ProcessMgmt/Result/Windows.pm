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

package stf::ProcessMgmt::Result::Windows;

use base qw{stf::ProcessMgmt::Result};

use strict;

use warnings;

use Win32::API;

sub new
{
	my ($class,$rc) = @_;
	
	die "No return code supplied" unless defined $rc;
	
	my $self = {};
	
	bless $self => $class;
	
	$self->{return_code} = $rc;
	$self->{message} = Win32::FormatMessage($rc);
	
	if(! defined($self->{message}))
	{
		$self->{message} = q{};
	}
	
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
	
	#The exit code is the return code on Windows
	
	return $self->{return_code};
}

sub signal
{
	#No signal information on Windows
	return undef;
}

sub core_dumped
{
	#No core_dumped information Windows
	return undef;
}

sub message
{
	my $self = shift;
	
	return $self->{message};
}

sub get_summary
{
	my $self = shift;
	
	my $return_code = $self->return_code();
	my $exit_code = $self->exit_code();
	my $message = $self->message();
	
	chomp $message;
	
	my $summary = << "SUMMARY";
The process completed with RC $return_code.

Unlike UNIX where the return code is complicated, on Windows the return 
code corresponds to the exit code (the value passed to exit() or 
System.exit()).

The corresponding Windows message for return code $return_code is: 
$message

Note that the message will only be meaningful if the application is 
co-operative and deliberately returns the appropriate error code 
for the situation.
	
SUMMARY

	return $summary;
}

1;


__END__
