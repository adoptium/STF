#!/usr/bin/perl

#
# Licensed Materials - Property of IBM
# "Restricted Materials of IBM"
#
# (c) Copyright IBM Corp. 2016 All Rights Reserved
#
# US Government Users Restricted Rights - Use, duplication or disclosure
# restricted by GSA ADP Schedule Contract with IBM Corp.
#

#----------------------------------------------------------------------------------#
# test_parseJavaVersionInfo.pl
#
# Test for the stfUtility parseJavaVersionInfo subroutine
# perl <workspace>/stf.core/scripts/tests/test_parseJavaVersionInfo.pl
#----------------------------------------------------------------------------------#

use strict;
use FindBin qw($Bin);
use lib "$Bin";
use lib "$Bin/..";
use Cwd;

use stf::stfUtility;

my %test_data;
$test_data{'ibm_java6'} = '
java version "1.6.0"
Java(TM) SE Runtime Environment (build pmz6460sr16fp25-20160422_01(SR16 FP25))
IBM J9 VM (build 2.4, JRE 1.6.0 IBM J9 2.4 z/OS s390x-64 jvmmz6460sr16fp25-20160413_299433 (JIT enabled, AOT enabled)
J9VM - 20160413_299433
JIT  - r9_20160328_114196
GC   - GA24_Java6_SR16_20160413_1159_B299433)
JCL  - 20160421_01
';
$test_data{'ibm_java626'} = '
java version "1.6.0"
Java(TM) SE Runtime Environment (build pxz3160_26sr9-20160624_01(SR9))
IBM J9 VM (build 2.6, JRE 1.6.0 Linux s390-31 20160622_308726 (JIT enabled, AOT enabled)
J9VM - R26_jvm.26_20160622_0305_B308726
JIT  - tr.r11_20160328_114192
GC   - R26_jvm.26_20160622_0305_B308726
J9CL - 20160622_308726)
JCL  - 20160620_01
';
$test_data{'ibm_java7'} = '
java version "1.7.0"
Java(TM) SE Runtime Environment (build pwi3270sr9fp50-20160720_01(SR9fp50))
IBM J9 VM (build 2.6, JRE 1.7.0 Windows Server 2012 x86-32 20160630_309948 (JIT enabled, AOT enabled)
J9VM - R26_Java726_SR9_20160630_1817_B309948
JIT  - tr.r11_20160630_120374
GC   - R26_Java726_SR9_20160630_1817_B309948
J9CL - 20160630_309948)
JCL - 20160719_01 based on Oracle jdk7u111-b13
';

$test_data{'ibm_java727'} = '
java version "1.7.0"
Java(TM) SE Runtime Environment (build pxa6470_27sr4-20160718_01(SR4))
IBM J9 VM (build 2.7, JRE 1.7.0 Linux amd64-64 Compressed References 20160716_311780 (JIT enabled, AOT enabled)
J9VM - R27_jvm.27_20160716_0000_B311780
JIT  - tr.r13.java_20160713_121016
GC   - R27_jvm.27_20160716_0000_B311780_CMPRSS
J9CL - 20160716_311780)
JCL - 20160701_01 based on Oracle jdk7u111-b13
';

$test_data{'ibm_java8'} = '
java version "1.8.0"
Java(TM) SE Runtime Environment (build pxr3280sr3-20160318_02(SR3))
IBM J9 VM (build 2.9, JRE 1.8.0 Linux arm-32 20160318_295683 (JIT enabled, AOT enabled)
J9VM - R829_20160318_0616_B295683 (Pure C)
JIT  - tr.r15.arm_20160318_113513
GC   - R829_20160318_0616_B295683
J9CL - 20160318_295683)
JCL - 20151231_01 based on Oracle jdk8u71-b15
';

$test_data{'ibm_java9ea'} = '
java version "1.9.0"
Java(TM) SE Runtime Environment (build pxl6490ea-20160718_01)
IBM J9 VM build 2.9, JRE 1.9.0 Linux ppc64le-64 Compressed References 20160716_311784 (JIT enabled, AOT enabled)
J9VM - R29_20160716_0101_B311784
JIT  - tr.open_20160708_120737_89f4fa11.green
OMR   - 2ebcc227
JCL - 20160705_01 based on Oracle jdk-9+95
';

$test_data{'oracle_java8'} = '
java version "1.8.0_92"
Java(TM) SE Runtime Environment (build 1.8.0_92-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.92-b14, mixed mode)
';

$test_data{'oracle_java9'} = '
java version "9-ea"
Java(TM) SE Runtime Environment (build 9-ea+124)
Java HotSpot(TM) 64-Bit Server VM (build 9-ea+124, mixed mode)
';

foreach my $key ( sort (keys(%test_data)) ) {
	print "Test data for: $key\n";
	print $test_data{$key} . "\n";
	my @lines = split ( '\n', $test_data{$key} );
	my %java_details = stf::stfUtility->parseJavaVersionInfo(\@lines);
	foreach my $key ( sort(keys(%java_details) ) ) {
		print $key . ": " . $java_details{$key} . "\n";
	}
}


exit 0;
