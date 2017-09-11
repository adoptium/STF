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

#
# Script: formatExcecutionLog.pl
# Support script to run the formatExecutionLog program.
#

use strict;
use FindBin qw($Bin);
use lib "$Bin/..";
use Cwd qw(abs_path);

my $stf_root = abs_path("$Bin/../..");

# Bare command for running FormatExecutionLog class
my $cmd = $ENV{'JAVA_HOME'} . "/bin/java" .
        " -classpath $stf_root/stf.load/bin" .
        " net.adoptopenjdk.loadTestAnalysis.FormatExecutionLog";

# Add all arguments as supplied to this script
foreach my $argnum (0 .. $#ARGV) {
    $cmd = $cmd . " " . $ARGV[$argnum];
}

# Run FormatExecutionLog
system($cmd);
