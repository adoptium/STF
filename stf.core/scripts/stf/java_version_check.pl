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

# java_version_check.pl
#
# Display details about a Java.
#
# perl java_version_check.pl
# perl java_version_check.pl -p JAVA_HOME
# - Gets details for the Java at the environment variable JAVA_HOME

# perl java_version_check.pl -p PATH
# - Gets details for the Java on the PATH
#
# perl java_version_check.pl -p <path>
# - Gets details for the Java specified.  Assumes the directory provided in the one above bin/java

use strict;
use warnings;

use FindBin qw($Bin);
use lib "$Bin/..";
use Getopt::Long;

use stf::stfUtility;
use stf::Commands;

my %command_line_options = ();
my %java_details = ();

GetOptions(
  'p=s'        => \$command_line_options{'java_location'},
  'H|h|help|?' => \&usage
);

my %getJavaVersionInfo_options = ();

if ( defined $command_line_options{'java_location'} ) {
	if ( $command_line_options{'java_location'} eq 'PATH' ) {
		$getJavaVersionInfo_options{'PATH'} = 1;
	}
	elsif ( $command_line_options{'java_location'} eq 'JAVA_HOME' ) {
		$getJavaVersionInfo_options{'JAVA_HOME'} = 1;
	}
	else {
		$getJavaVersionInfo_options{'PATH'} = $command_line_options{'java_location'};
	}
}
%java_details = stf::stfUtility->getJavaVersionInfo(%getJavaVersionInfo_options);

foreach my $key ( sort(keys(%java_details) ) ) {
	print $key . ": " . $java_details{$key} . "\n";
}


sub usage {
	print 'Usage:

perl java_version_check.pl
perl java_version_check.pl -p JAVA_HOME
- Gets details for the Java at the environment variable JAVA_HOME

perl java_version_check.pl -p PATH
- Gets details for the Java on the PATH

perl java_version_check.pl -p <path>
- Gets details for the Java specified.  Assumes the directory provided in the one above bin/java
';
}
