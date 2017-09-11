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

package stfArguments;

use strict;
use warnings;
use diagnostics;
use FindBin qw($Bin);
use lib "$Bin";
use stfArguments;

my @stf_personal_properties = "";
my @stf_default_properties = "";


# Registers property files for subsequent argument processing. 
# This subroutine must be called before 'get_argument'
sub set_argument_data {
	my $stf_personal = shift;
	my $stf_default = shift;

	#print "\n";
 	#print "stf_personal_properties: $stf_personal\n";
 	#print "stf_default_properties: $stf_default\n";
	
	# Read in the contents of the configuration files
	@stf_personal_properties           = read_file_contents($stf_personal);
    @stf_default_properties            = read_file_contents($stf_default); 
}


# Reads the contents of a file into an array
sub read_file_contents
{
	my $version_file = shift;
	open(my $fh,'<',$version_file) or return "";
	my @contents = <$fh>;
	close($fh) or warn "Could not close handle on version file: $version_file: $!";

	return @contents;
}


# Returns the value of an argument.
# Search order is:
#   1) command line arguments.
#   2) User specific property for this STF test variant.
#   3) STF default properties for this STF test variant.
#   4) User specific STF properties
#   5) Default STF properties
#
sub get_argument { 
	my $name = shift;
  
	# Find value for the named property    
	my $value = do_get_argument($name);
	
	# Remove leading and trailing whitespace
	# This is needed for running from a workspace created on a Windows machine 
	# when on a linux machine. 
	# STF was failing because of a CR/LF problem. Without trimming the property
	# value unexpectedly ends with a CR.
	$value = stf::stfUtility->strip($value);

	# If the value contains a reference to variable ${HOME} 
	# replace it with the resolved HOME environment variable (either $HOME or %USERPROFILE%)
	$value =~ s/\$\{HOME\}/${ENV{'HOME'}}/;
	
	# If the resolved value matches '${.*}' then pull actual value from an environment variable.
	if ($value =~ /^\$\{.*\}$/) {
		my $env_variable_name = substr($value, 2, -1);
		$value = $ENV{$env_variable_name};
	}

	return $value;
}


# Gets an argument and fails if the arguments value is null
#
sub get_and_check_argument {
	my $argument_name = shift;
	
	my $argument_value = get_argument($argument_name);
	
	if ( ! defined ($argument_value) || $argument_value eq 'null') {
		my $arg_contents = do_get_argument($argument_name);
		if ($arg_contents =~ /^\$\{(.*)\}$/) {
			# We failed attempting to find value for environment variable
			print "**FAILED** Can't resolve value for '$argument_name', as environment variable '$1' is not set\n";
		} else {
			# Generic failure message
			print "**FAILED** No value set for '$argument_name'\n";
		}
		exit 1;
	}

	# Don't allow an empty value for the argument.
	# Especially important for 'results-root', to prevent deletion of 'old results' in '/*' 
	if ($argument_value eq '') {
		print "**FAILED** Argument value cannot be empty for '$argument_name'\n";
		exit 1;
	}

	return $argument_value;
}


sub arg_info {
	my $argument_name = shift;
	
	my $argument_value = get_argument($argument_name);
	if ( defined ($argument_value)) {
		print "Arg '$argument_name' is defined\n";
	}
	if ( !defined ($argument_value)) {
		print "Arg '$argument_name' not defined\n";
	}
	
	if ($argument_value eq 'null') {
		print "Arg '$argument_name' NULL\n";
	}
	if ($argument_value ne 'null') {
		print "Arg '$argument_name' = '$argument_value'\n";
	}
}


sub do_get_argument {
	my $name = shift;
	
	# Try to find the value of the argument in the command line arguments
	foreach my $a(@ARGV) {
		my $value = extract_if_match($name, $a);
		if (defined $value) {
			return $value;
		}
	}

	# Look in the users STF configuration
	foreach my $a(@stf_personal_properties) {
		my $value = extract_if_match($name, $a);
		if (defined $value) {
			return $value;
		}
	}

	# Finally look in the default STF configuration
	foreach my $a(@stf_default_properties) {
		my $value = extract_if_match($name, $a);
		if (defined $value) {
			return $value;
		}
	}

	die "Failed to find value for property: '$name'";
}


sub get_boolean_argument {
	my $parameter_name = shift;

	my $value = get_argument($parameter_name);
	
	if (lc $value eq 'true') {
		return 1;
	} elsif (lc $value eq '') {
		return 1;
	} elsif (lc $value eq 'false') {
		return 0;
	}
	
	die "Failed to read boolean parameter '$parameter_name' with value '$value'\n" .
		"The value must have a value of either 'true' or 'false'";	
}


sub extract_if_match {
	my $target_name = shift;
	my $arg = shift;
	my $platform = stf::stfUtility->getPlatform();

	# Search for a value in the form '-name.<platform> = value'
	if ($arg =~ /^ *-?$target_name.$platform *=/) {
		my ($value) = $arg =~ /= *(.*) */g;
		return $value;
	}
	
	# Search for a value in the form '-name = value'
	if ($arg =~ /^ *-?$target_name *=/) {
		my ($value) = $arg =~ /= *(.*) */g;
		return $value;
	}
	
	# Search for a boolean flag, in the form '-name'
	if ($arg =~ /^ *-?$target_name *$/) {
		return "";
	}
	
	return undef;
}

sub get_home_dir {
	# 1. Expect $HOME to be set on Unix
	# 2. Use %HOME% if set on Windows, otherwise expect %USERPROFILE% to be set
	# 3. Otherwise set %HOME% to %USERPROFILE% on Windows (means code elsewhere in STF
	#    can rely on a HOME environment variable being set).
	if ( ! $ENV{HOME} ) {
		if ( $ENV{USERPROFILE} ) {
			$ENV{HOME} = $ENV{USERPROFILE};
		}
	}
	return $ENV{HOME} if $ENV{HOME};
	print "Failed to get home directory for current user.";
	exit 1;
}

sub write_arguments_to_file { 
	my $output_file_name = shift;
	my $stf_bin_dir = shift;
	my $updated_test_root = shift;
	my $updated_systemtest_prereqs = shift;
	
   
	open(my $fh, '>', $output_file_name) or die "Not able to write arguments to file '$output_file_name'";

	foreach my $a(@ARGV) {
		print $fh "$a\n\n";
	}
	
	print $fh "-stf-bin-dir=$stf_bin_dir\n\n";
	
	if (defined($updated_test_root)) {
		print $fh "-test-root=$updated_test_root\n\n";
	}
	
	if (defined($updated_systemtest_prereqs)) {
	print $fh "-systemtest-prereqs=$updated_systemtest_prereqs\n\n";
	}
	
	close $fh;
}

1;
