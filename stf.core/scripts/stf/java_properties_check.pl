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

# java_properties_check.pl
#
# Display details about a Java.
#
# perl java_properties_check.pl
# perl java_properties_check.pl -p JAVA_HOME
# - Gets java propertiesfor the Java at the environment variable JAVA_HOME

# perl java_properties_check.pl -p PATH
# - Gets java propertiesfor the Java on the PATH
#
# perl java_properties_check.pl -p <path>
# - Gets java propertiesfor the Java specified.  Assumes the directory provided in the one above bin/java

use strict;
use warnings;

use FindBin qw($Bin);
use lib "$Bin/..";
use Getopt::Long;

use stf::stfUtility;
use stf::Commands;

my %command_line_options = ();
my %java_properties = ();

GetOptions(
  'p=s'        => \$command_line_options{'java_location'},
  'H|h|help|?' => \&usage
);

my %getJavaProperties_options = ();

if ( defined $command_line_options{'java_location'} ) {
	if ( $command_line_options{'java_location'} eq 'PATH' ) {
		$getJavaProperties_options{'PATH'} = 1;
	}
	elsif ( $command_line_options{'java_location'} eq 'JAVA_HOME' ) {
		$getJavaProperties_options{'JAVA_HOME'} = 1;
	}
	else {
		$getJavaProperties_options{'PATH'} = $command_line_options{'java_location'};
	}
}
%java_properties = stf::stfUtility->getJavaProperties(%getJavaProperties_options);

foreach my $key ( sort(keys(%java_properties) ) ) {
	print $key . ": " . $java_properties{$key} . "\n";
}


sub usage {
	print 'Usage:

perl java_properties_check.pl
perl java_properties_check.pl -p JAVA_HOME
- Gets java properties for the Java at the environment variable JAVA_HOME

perl java_properties_check.pl -p PATH
- Gets java properties for the Java on the PATH

perl java_properties_check.pl -p <path>
- Gets java properties for the Java specified.  Assumes the directory provided in the one above bin/java
';
}
