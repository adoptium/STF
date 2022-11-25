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
# This script contains STF utility code.
# The subroutines in this file are used by the test specific generated scripts
# and the STF Perl modules.
#----------------------------------------------------------------------------------#

use warnings;
use strict;
use Config;

package stf::stfUtility;

our @ISA = qw(Exporter);
our @EXPORT = qw(err warn info debug trace getDelimiter);

use Config;
use Cwd;
use Cwd 'abs_path';
use Data::Dumper;
use File::Basename;
use File::Copy;
use File::Path qw(mkpath rmtree);
use File::Spec::Functions qw(splitdir catfile);
use File::Temp;
use stf::Constants qw(:all);

require stf::Commands;

# Default logging level to INFO 
$ENV{loggingLevel} = "INFO" unless (defined $ENV{loggingLevel});

my $useHiRes = $TRUE;
eval "use Time::HiRes";
if ($@) {
     $useHiRes = $FALSE;
}

#----------------------------------------------------------------------------------#
# listErrors
#
# Prints out errors from a given list.
#
# Usage:
#  stf::stfUtility->listErrors(\@error_list);
#----------------------------------------------------------------------------------#
sub listErrors {
	my ($self, @error_list) = @_;
	
	for my $diag ( @error_list ) {
	   if ( ref ($diag) eq 'HASH' ) {
	      my ($file, $message) = %$diag;
	      print "Error: file='$file' message='$message'\n";
	   }
	   else {
	      print "Error: message='$diag'\n";
	   }
	}
}

#----------------------------------------------------------------------------------#
# readFileIntoArray
#
# Stores the contents of the supplied file into an array.
#
# Usage:
#  stf::stfUtility->readFileIntoArray(file=>$filename);
#
# Returns:
#  The array containing the lines in the supplied file.
#----------------------------------------------------------------------------------#
sub readFileIntoArray {
	my ($self, $filename) = @_;
	
	my @lines = ();
	
	# strip out any quotes...
	($filename) =~ s/\"//g;
	
	# check the file exists
	if (!(-e $filename)) {
	   stf::stfUtility->logMsg( message => "stfUtility.pm::readFileIntoArray: File $filename does not exist.");
	   return [];
	}
	
	if (!(-r $filename)) {
	   stf::stfUtility->logMsg( message => "stfUtility.pm::readFileIntoArray: Cannot read file $filename.");
	   return [];
	}
	
	# open file
	open (FILE, "<$filename");
	   
	# search array
	@lines = <FILE>;
	
	close FILE;
	return \@lines;
}

#------------------------------------------------------------#
# writeToFile
#
# Writes a string array to the file passed to the method.
#
# Usage:
#  writeToFile(%args)
#  stf::stfUtility->writeToFile( file => $file, content => \@lines, replace => $TRUE);
#  stf::stfUtility->writeToFile( file => $msglog, content => ["One line to append"], replace => $FALSE );
#
# Arguments:
#  %args        = (file => $file, content => $lines, replace => $replace)
#  $file        = file name + path to write to
#  $lines        = the lines to write to the file (array ref)
#  $replace    = if set replace the file otherwise append to it
#------------------------------------------------------------#
sub writeToFile {
	my ($self, %args) = @_;              # The args
	my $file = "";                       # The name of the file to write to
	my $lines = [];                      # The lines to write to the file
	my $replace = $FALSE;                # If set to true replace the file
	
	if (exists $args{file}) {
	   $file = $args{file};         
	}
	
	if (exists $args{content}) {
	   $lines = $args{content};         
	}
	
	if (exists $args{replace}) {
	   $replace = $args{replace};
	}
	
	#if ($file eq "")
	#{
	#   stf::stfUtility->logMsg( message => "ERROR: Unable to create file as no filename was specified (writeToFile(file => filename))");
	#}
	
	if ($file eq "") {
	   return;
	}
	
	if ($replace == $TRUE) {
	   open FILE, ">$file";
	}
	else {
	   open FILE, ">>$file";
	}
	
	foreach my $line (@{$lines}) {
	    # check the line has a new line character if not add one
	    if (($line !~ /\n/) and ($line !~ /\r/) and ($line !~ /\f/) and ($line !~ /\e/)) {
	       $line = $line."\n";
	    }
		if ($file ne "") {
	       print FILE $line;
	    }
	    else {
		#	print $line;
	    }
	}
	
	close FILE;
}

#------------------------------------------------------------#
# getPathSeparator
#
# Returns a platform specific path separator.
#
# Usage:
#  my $sep = stf::stfUtility->getPathSeparator;
#
# Returns:
#  The path separator
#------------------------------------------------------------#
sub getPathSeparator {
	my ($self) = @_;

	my $ps = ":";
	if ($^O eq 'MSWin32') {
	    $ps = ";";
	}
	
	return $ps;
}

#------------------------------------------------------------#
# getPlatform
#
# Returns the current platform.
#
# Usage:
#  my $platform = stf::stfUtility->getPlatform;
#
# Returns:
#  win, linux, zos, aix, osx, or bsd
#------------------------------------------------------------#
sub getPlatform {
	if ($^O eq 'MSWin32') {
		return "win";
	}
	elsif ($^O eq 'linux') {
		return "linux";
	}
	elsif ($^O eq 'os390') {
		return "zos";
	}
	elsif ($^O eq 'aix') {
		return "aix";
	}
	elsif ($^O eq 'darwin') {
		return "osx";
	}
	elsif ($^O =~ 'solaris') {
		return "sunos";
	} elsif ($^O =~ 'bsd') {
		return "bsd";
	}
	else {
		die "Platform $^O is not yet supported";
	}
}

#------------------------------------------------------------#
# getPlatformArch
#
# Returns the current platform arch.
#
# Usage:
#  my $platform = stf::stfUtility->getPlatformArch;
#
# Returns:
#  x86, ppc, ppcle, 390, arm, riscv, loongarch or sparc
#------------------------------------------------------------#
sub getPlatformArch {
	my $platform = stf::stfUtility->getPlatform;
	
	if ($platform eq 'win') {
		return "x86";
	}
	elsif ($platform eq 'linux') {
		if ($Config{archname} =~ 'x86') {
			return "x86";
		}
		if ($Config{archname} =~ 'ppc64le') {
			return "ppcle";
		}
		elsif ($Config{archname} =~ 'powerpc') {
			return "ppc";
		}
		elsif ($Config{archname} =~ '390') {
			return "390";
		}
		elsif ($Config{archname} =~ 'arm') {
			return "arm";
		}
		elsif ($Config{archname} =~ 'riscv') {
			return "riscv";
		}
		elsif ($Config{archname} =~ 'loongarch') {
			return "loongarch";
		}
		elsif ($Config{archname} =~ 'sparc') {
			return "sparc";
		}
		else {
			die "Platform arch $Config{archname} is not yet supported";
		}
	}
	elsif ($platform eq 'zos') {
		return "390";
	}
	elsif ($platform eq 'aix') {
		return "ppc";
	}
	elsif ($platform eq 'bsd') {
		# This should be expanded per Linux to handle powerpc64 and aarch64
		 return "x86";
	}
	else {
		die "Platform arch for $platform is not yet supported";
	}
}

#------------------------------------------------------------#
# getDelimiter
#
# Returns a platform specific delimiter.
#
# Usage:
#  my $sep = stf::stfUtility->getDelimiter;
#
# Returns:
#  The delimeter
#------------------------------------------------------------#
sub getDelimiter {
	my ($self) = @_;
	
	my $delimiter = '/';
	if ($^O eq "MSWin32") {
		$delimiter = '\\';		
	}
	
	return $delimiter;
}

#------------------------------------------------------------#
# getQuote 
#
# Returns a platform specific quote.
#
# Usage:
#  my $sep = stf::stfUtility->getQuote;
#
# Returns:
#  The quote
#------------------------------------------------------------#
sub getQuote {
	my ($self) = @_;

	my $quote = "\'";
	if ($^O eq 'MSWin32') {
    	$quote = "\"";
	}
	
	return $quote;
}

#------------------------------------------------------------#
# getNow 
#
# Returns the current time in 3 different formats, see below.
#
# Usage:
#  my ($now) = stf::stfUtility->getNow();
#  my ($now, $date) = stf::stfUtility->getNow(date => $TRUE);
#  my ($now, $time) = stf::stfUtility->getNow(time => $TRUE);
#  my ($now, $date, $time) = stf::stfUtility->getNow(date => $TRUE, time => $TRUE);
#
# Returns up to three results:
#  1. Always - time and date as a string format = yymmdd-hhmmss.
#  2. If a date hash argument was received, the yymmdd part.
#  3. If a time hash argument was received, the hhmmssd part.
#------------------------------------------------------------#
sub getNow {
	my ($self, %args) = @_;
	
	#     0     1     2      3      4     5      6      7      8
	my ($sec, $min, $hour, $mday, $mon, $year, $wday, $yday, $isdst) = localtime(time);
	
	my $now  = sprintf("%02d", $year - 100)
	         . sprintf("%02d", $mon  + 1)
	         . sprintf("%02d", $mday) . '-'
	         . sprintf("%02d", $hour) 
	         . sprintf("%02d", $min) 
	         . sprintf("%02d", $sec);
	
	my $date = "";
	my $time = "";
	my @data = ();
	
	if (exists $args{date}) {
	   $date = sprintf("%02d", ($year % 100) ). '-'
	         . sprintf("%02d", $mon + 1). '-'
	         . sprintf("%02d", $mday); 
	
	   push(@data, $date);
	}
	
	if (exists $args{time}) {
	   $time = sprintf("%02d", $hour) . ':'
	         . sprintf("%02d", $min) . ':' 
	         . sprintf("%02d", $sec);
	
	   push(@data, $time);
	}
	
	if (scalar @data > 0) {
	   unshift(@data, $now);
	   return @data;
	}
	
	return $now;
}

#------------------------------------------------------------#
# chmodPath 
#
# Changes the permissions for a directory path.
#
# Usage:
#  stf::stfUtility->chmodPath(path => $path, code => $code);
#  stf::stfUtility->chmodPath(path => $path, code => $code, root => $root);
#  stf::stfUtility->chmodPath(path => $path, code => $code, home => $TRUE);
#  stf::stfUtility->chmodPath(path => $path, code => $code, root => $root, home => $TRUE);
#------------------------------------------------------------#
sub chmodPath {
	my ($self, %args) = @_;

    my $path = "";             # the path to change the permissions on
    my $code = "";             # permission code
    my $root = "";             # optional parameter added to the front of the path but not chmoded itself
    my $home = $FALSE;         # if the path to be chmoded is the users home directory it will not be chmoded unless
                               # this is set to TRUE
	
	if (exists $args{path}) {
	   $path = $args{path};         
	}
	if (exists $args{code}) {
	   $code = $args{code};         
	}
	if (exists $args{root}) {
	   $root = $args{root};         
	}
	if (exists $args{home}) {
	   $home = $args{home};         
	}

	my @dirs = File::Spec->splitdir($path);
	shift(@dirs);
	my @rootdirs = ();
	my @list = ();
	my $curdir = $root;
	
	$root =~ s/\\/\\\\/g; #Escape the backslashes
	$root =~ s/\//\\\//g; #Escape the forwardslashes
	$path =~ s/\\/\\\\/g; #Escape the backslashes
	$path =~ s/\//\\\//g; #Escape the forwardslashes
	
	if (defined $root and $root ne "") {
		# Strip out slashes and convert to lower case because numbers of slashes
		# between directories may vary and on Windows the type of slash and case
		# may vary also.
		my $comppath = lc($path);
		my $comproot = lc($root);
		$comppath =~ s/\\//g;
		$comproot =~ s/\\//g;
		$comppath =~ s/\///g;
		$comproot =~ s/\///g;
	 	 
		if ($comppath !~ /${comproot}.*/) {
			stf::stfUtility->logMsg( message => "ERROR: Root directory ($root) does not appear in the path directory ($path)");
			return;	  	 	
		}
		 
		@rootdirs = File::Spec->splitdir($root);
		shift(@rootdirs);
	}
	else {
		stf::stfUtility->logMsg( message => "ERROR: Root directory argument is mandatory");
		return;	
	} 
	
	if (! defined $home or $home == $FALSE) {
		my $home_dir = $ENV{HOME};
		if (!$home_dir) {
			$home_dir = ""; # prevent perl warnings when $HOME isn't set
		}
		$home_dir =~ s/\\/\\\\/g; #Escape the backslashes
		$home_dir =~ s/\//\\\//g; #Escape the forwardslashes
		if ($path =~ /$home_dir/ and $root !~ /$home_dir/) {
			stf::stfUtility->logMsg( message => "ERROR: To change the access to the HOME directory supply \$TRUE as the 4th argument ");
			return;
		}
	}
	
	my $rootdir = "";
	
	# change the permissions on each directory
	DIR:foreach my $dir(@dirs) {
	   if (scalar @rootdirs > 0) {
	      $rootdir = shift(@rootdirs);
	      if ($rootdir = $dir) {
	         next DIR;
	      }
	   }
	   $curdir = catfile($curdir, $dir);
	   push(@list, $curdir);
	}
	
	unshift(@list, $code);
	chmod(@list);
}

#------------------------------------------------------------#
# splatTree
#
# Removes the given directory. Takes account of directories on
# windows that may have file names too long for rmtree to deal with
#
# Usage:
#  stf::stfUtility->splatTree(dir => $dir);
#------------------------------------------------------------#
sub splatTree {	
	my ($self, %args) = @_; 

    my $dir = "";             # the directory tree to delete
	
	if (exists $args{dir}) {
	   $dir = $args{dir};
	}

	if ($dir eq "" ) {
	    die "Error: stfUtility.pm: splatTree directory argument not supplied or blank\n";
	}
	
	if ($^O eq "MSWin32" ) {
		# Some tests create directories that are so long that rmtree will die
		stf::stfUtility->shortenPaths(dir => $dir);
	}

	my $err;
		
	rmtree( $dir, {error => \$err});
	if (defined (@$err[0])) {
	    stf::stfUtility->list_errors( @$err);
	    die "Error: splatTree failed running rmtree to delete $dir\n";
	}
}

#------------------------------------------------------------#
# shortenPaths
#
# Used by splatTree above. Recursively descends into the given
# directory and renames all directories to a single digit.
#
# Usage:
#  stf::stfUtility->shortenPaths(dir => $dir);
#------------------------------------------------------------#
sub shortenPaths {
	my ($self, %args) = @_;
	
	my $longPath = "";

	if (exists $args{dir}) {
	   $longPath = $args{dir};
	}

	if ($longPath eq "" ) {
	    die "Error: stfUtility.pm: shortenPaths directory argument not supplied or blank\n";
	}

	opendir(DIR, $longPath) or return 0;
	my @files = readdir(DIR);
	closedir(DIR);
	
	my @subDirs;
	my $newName = 0;
	foreach my $file(@files) {
		if ($file eq '.' || $file eq '..') {
			next;
		}

		if (length($file) < 3) {
			next;
		}

		$file = $longPath . "/$file";
		if (-f $file) {
			next;
		}

		my $newFile = $longPath . "/$newName";
		$newName++;
		rename($file, $newFile) or next;
		push(@subDirs, $newFile);
	}

	foreach my $subDir (@subDirs) {
		my $rv = eval {
			stf::stfUtility->shortenPaths( dir => $subDir );
			1;
		};
		
		if (!(defined $rv)) {
			print("Crashed trying to descend into $subDir\n")
		}
	}
}

#------------------------------------------------------------#
# searchReplace
#
# Searches a file for every occurrence of a search string and
# replaces them with the substitute string.
#
# Usage:
#  my $number = stf::stfUtility->searchReplace(file => $filename, search => $search_string, replace => $replace_string)
#
# Returns:
#  The number of replacements
#------------------------------------------------------------#
sub searchReplace {
	my ($self, %args) = @_;
	
	my $file = "";                       # the file to search
	my $search_string = "";              # string representing the string to search the file for (use single quotes to quote string)
	my $replace_string = "";             # substitute string

	if (exists $args{file}) {
	   $file = $args{file};
	}
	if (exists $args{search}) {
	   $search_string = $args{search};
	}
	if (exists $args{replace}) {
	   $replace_string = $args{replace};
	}

	# initialise replacement 
	my $cnt = 0;
	      
	# Open the file in read and write mode     
	open (FILEIN, "<$file");
	
	my @lines = ();
	# walk through the read file, creating a updated copy of the file...
	foreach my $line (<FILEIN>)
	{
		if ($line =~ /\Q${search_string}\E/)
		{    
		   ($line) =~ s/\Q${search_string}\E/${replace_string}/g;
		   $cnt++;
		}
		push (@lines, $line);
	}
	
	close FILEIN;
	
	stf::stfUtility->writeToFile(file => $file, content => \@lines, replace => $TRUE);
	return $cnt;
}

#------------------------------------------------------------#
# err
#
# Prints messages at the error logging level.
#
# Usage:
#  err("Unexpected dump found: " . $dump);
#------------------------------------------------------------#
sub err {
    my $messageText = shift;
    
    if (defined $ENV{loggingLevel}) {
    	if ($ENV{loggingLevel} eq "ERR" || $ENV{loggingLevel} eq "WARN" || $ENV{loggingLevel} eq "INFO" || $ENV{loggingLevel} eq "DEBUG" || $ENV{loggingLevel} eq "TRACE") {
			stf::stfUtility->logMsg(message => $messageText);
    	}
    } 
}

#------------------------------------------------------------#
# warn
#
# Prints messages at the warning logging level.
#
# Usage:
#  warn("Killing processes: " . join(" ", sort keys %process_list));
#------------------------------------------------------------#
sub warn {
    my $messageText = shift;
    
    if (defined $ENV{loggingLevel}) {
    	if ($ENV{loggingLevel} eq "WARN" || $ENV{loggingLevel} eq "INFO" || $ENV{loggingLevel} eq "DEBUG" || $ENV{loggingLevel} eq "TRACE") {
			stf::stfUtility->logMsg(message => $messageText);
    	}
    } 
}

#------------------------------------------------------------#
# info
#
# Prints messages at the info logging level.
#
# Usage:
#  info("  o Process $process->{uid} is still running as expected");
#------------------------------------------------------------#
sub info {
    my $messageText = shift;
    
    if (defined $ENV{loggingLevel}) {
    	if ($ENV{loggingLevel} eq "INFO" || $ENV{loggingLevel} eq "DEBUG" || $ENV{loggingLevel} eq "TRACE") {
			stf::stfUtility->logMsg(message => $messageText);
    	}
    } 
}

#------------------------------------------------------------#
# debug
#
# Prints messages at the debug logging level.
#
# Usage:
#  debug("The pskill tool ran without error");
#------------------------------------------------------------#
sub debug {
    my $messageText = shift;
    
    if (defined $ENV{loggingLevel}) {
    	if ($ENV{loggingLevel} eq "DEBUG" || $ENV{loggingLevel} eq "TRACE") {
			stf::stfUtility->logMsg(message => $messageText);
    	}
    } 
}

#------------------------------------------------------------#
# trace
#
# Prints messages at the trace logging level.
#
# Usage:
#  trace("Process $process->{uid} is still running");
#------------------------------------------------------------#
sub trace {
    my $messageText = shift;
    
    if (defined $ENV{loggingLevel} && $ENV{loggingLevel} eq "TRACE") {
    	    stf::stfUtility->logMsg(message => $messageText);
    } 
}

#------------------------------------------------------------#
# logMsg
#
# Prints messages and writes the messages to a file if one 
# is specified.
#
# Usage:
#  stf::stfUtility->logMsg(file => $file, message => $msg);
#  stf::stfUtility->logMsg(message => $messageText);
#------------------------------------------------------------#
sub logMsg {
	my ($self, %args) = @_;

    my $file      = "";         # the file to log the message to
    my $message   = "";         # the string to add to the log

	if (exists $args{file}) {
	   $file = $args{file};
	}
	if (exists $args{message}) {
	   $message = $args{message};
	}

	my $entry = "$message\n";

	my $timestamp;
	# Get a timestamp with millisecond accuracy if the HiRes perl module 
	# is available, otherwise downgrade to second accuracy.
	if ($useHiRes) {
		my $log_timestamp = Time::HiRes::gettimeofday();

		# Work out how many milliseconds the timestamp contains
		my $timeWithNoMillis = int($log_timestamp);
        my $for_millisecond = int(($log_timestamp-$timeWithNoMillis)*1000);
        
		# Format the time to HH:MM:SS.mmm format so that it in includes the milliseconds
		my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime($timeWithNoMillis);
        $timestamp = sprintf("%02d:%02d:%02d.%03d", $hour, $min, $sec, $for_millisecond);
	}
	else {
		my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst)=localtime(time);
		
        $timestamp = sprintf("%02d:%02d:%02d    ", $hour, $min, $sec);
	}
	
	# Prefix the message with the timestamp
	$entry = "${timestamp} - $entry";
	
	# Prefix all logging with 'STF'
	$entry = "STF $entry";
	
	if ( $file ne "" ) {
		stf::stfUtility->writeToFile(file => $file, content =>[$entry]);
	}
	
	print $entry;
}

#------------------------------------------------------------#
# strip
#
# Strips the leading and trailing whitespace from a string.
#
# Usage:
#  $string = stf::stfUtility->strip($str);
#
# Arguments:
#  The string to remove the leading and trailing whitespace from.
#
# Returns:
#  The trimmed string
#------------------------------------------------------------#
sub strip {
  my ($self, $str) = @_;

  $str =~ s/^\s+//g;
  $str =~ s/\s+$//g;

  return $str;
}

#------------------------------------------------------------#
# copyTree
#
# Copies a directory tree.
#
# Usage:
#  $rc = stf::stfUtility->copyTree(    
#    from    =>  fromdir,
#    to        =>  todir,
#    includeList    => /@includeList);
#    excludeList    => /@excludeList);
#
# Arguments:
#    from    = the directory to copy from
#    to    = the directory to copy to
#    @includeList    = (.ext1, .ext2, ...);
#    @excludeList    = (a.ext1, b.ext2, ...);
#
# Returns
#  0 if successful and 1 if unsuccessful
#------------------------------------------------------------#
sub copyTree { 
   my ($self, %args) = @_;              # The args
   my $fromdir = "";                    # The directory to copy from
   my $todir = "";                      # The directory to copy to
   my $includeList = "";                # The optional array of file extensions to include in the jar
   my $excludeList = "";                # The optional array of file names to exclude from the jar 
   
   if (exists $args{from}) {
      $fromdir = $args{from};
      
      # strip out any quotes...
      ($fromdir) =~ s/\"//g;
   }
   
   if (exists $args{to}) {
      $todir = $args{to};
               
      # strip out any quotes...
      ($todir) =~ s/\"//g;
   }
   
   if (exists $args{includeList}) {
      $includeList = $args{includeList};         
   }
   
   if (exists $args{excludelist}) {
      $excludeList = $args{excludelist};         
   }

   if (!-d $fromdir) {
      return 1;
   }

   if (!-d $todir) {
      mkpath $todir or return 1;
      
      chmod(0775, $todir) or return 1;
   }

   # get current dir
   my $currdir = getcwd();
   chdir $fromdir;

   # Support relative paths for $todir
   unless (File::Spec->file_name_is_absolute($todir)) {
     $todir = catfile($currdir, $todir);
   }

   my @files = glob '.* *';
   foreach my $file (@files ) {
      my $from = catfile($fromdir, $file);
      my $to = catfile($todir,$file);
      if (-d $file) {
	      if (!-l $file and ($file ne "." and $file ne "..")) {
	         if ($includeList eq "" and $excludeList eq "") {
	            $self->copyTree(from    => $from, 
	                            to      => $to);
	         }
	         elsif ($includeList ne "" and $excludeList eq "") {
	            $self->copyTree(from    => $from, 
	                            to      => $to,
	                            includeList => $includeList);
	         } 
	         elsif ($includeList eq "" and $excludeList ne "")  {
	          	$self->copyTree(from    => $from, 
	                            to      => $to,
	                            excludeList => $excludeList);
	         } 
	         else {
	          	$self->copyTree(from    => $from, 
	                            to      => $to,
	                            includeList => $includeList,
	                            excludeList => $excludeList);
	         }
	         chdir $fromdir or return 1;
	      }
      }
      else {
         my $match = 0; 
         if ($includeList eq "") {
            $match = 1;
         }
         else {
            my ($base, $path, $type) = fileparse($file,'\.[\w\-\+]*');
            foreach my $extension (@{$includeList}) {
               if ($extension eq $type) {
                  $match = 1;
               }
            }
         }
         
         if ($excludeList ne "") {
             my ($base, $path, $type) = fileparse($file,'\.[\w\-\+]*');
             my $fileName = $base . $type;  
             foreach my $exclude (@{$excludeList}) {
                if ($fileName eq $exclude) {
                   $match = 0;
                }
             }
         }
 		 
         if ($match == $TRUE) {
            copy($from, $to) or return 1;
         }
      }
   }

   # change to the directory we were in
   chdir $currdir or return 1;
   
   return 0;
}

#----------------------------------------------------------------------------------#
# getWorkspaceRoot
#
# Returns the full path of the workspace root.
#
# Usage:
#  stf::stfUtility->getWorkspaceRoot();
#
# Returns:
#  The workspace root
#----------------------------------------------------------------------------------#
sub getWorkspaceRoot {
	my $self = @_;
	
	my $stfUtilityPath = $INC{"stf/stfUtility.pm"};
	s/stfUtility.pm$// for $stfUtilityPath;
	
	return abs_path(catfile($stfUtilityPath, "..", "..", ".."));
}

#----------------------------------------------------------------------------------#
# getJavaVersionInfo
#
# Usage:
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(JAVA_HOME => 1);
#  %java_details = stf::stfUtility->getJavaVersionInfo();
# - Gets details for the Java at JAVA_HOME
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(PATH => 1);
# - Gets details for the Java on the PATH
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(PATH => <path>);
# - Gets details for the Java at <path>
#----------------------------------------------------------------------------------#
sub getJavaVersionInfo {

	my ($self, %options) = @_;

	my $valid_input = 1;
	foreach my $key (sort keys %options) {
		if ( $key ne 'PATH' &&
		     $key ne 'JAVA_HOME' ) {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->getJavaVersionInfo: Received unrecognised option $key\n" );
			$valid_input = 0;
		}
	}
	
	my %java_details;
	 
    my $tempInst;
    if ($^O eq "MSWin32" ) {
		$tempInst = File::Temp::tempdir(CLEANUP => 1);
	} else { 
		$tempInst="/tmp";
	}
	
    my $fullversion="JRE 0.0.0 IBM OS build unknown_build (SR0 FP0)";
	my $verlog = catfile($tempInst,"version_info");
	debug("stf::stfUtility->getJavaVersionInfo: verlog is $verlog");

	my $path_to_java = "";

	if ( $valid_input == 1 ) {
		if ( defined $options{'PATH'} ) {
			if ( $options{'PATH'} eq '1' ) {
				$path_to_java = "";
			}
			else {
				$path_to_java = "$options{'PATH'}/bin/";
			}
		}
		elsif ( !defined $ENV{'JAVA_HOME'} || $ENV{'JAVA_HOME'} eq "" ) {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->getJavaVersionInfo: Environment variable JAVA_HOME is not set, unable to retrieve Java version information");
			$valid_input = 0;
		}
		else {
			$path_to_java = "$ENV{'JAVA_HOME'}/bin/";
		}
	}
	
	if ( $valid_input == 1 ) {
		my $command = $path_to_java . "java";
		# stdout goes to $verlog.stdout
		# stderr goes to $verlog.stderr
		my ($rc, $process) = stf::Commands->run_process(mnemonic => "stf::stfUtility->getJavaVersionInfo", command => "$command", args => "-version", logName => $verlog, echo => 1);
	
	    if ($rc != 0) {
			stf::stfUtility->logMsg( message => "stf::stfUtility->getJavaVersionInfo: $command did not complete with rc = 0");
	    }
	    my $lines = $self->readFileIntoArray("$verlog.stderr");

	    # Strip out messages relating to zOS file permissions, because we may be on an NFS mount on zOS
	    $lines = $self->strip_zos_unable_to_switch_to_IFA_processor_message($lines);
		foreach my $line (@{$lines}) {
			debug ("stf::stfUtility->getJavaVersionInfo: Content from $verlog is $line");
		}

	    %java_details = $self->parseJavaVersionInfo($lines);

	}

    return %java_details;
}

sub parseJavaVersionInfo {
	
	my ($self, $lines) = @_;

	# Is this an IBM or Oracle Java?

	my $java_vendor           = 'Unknown'; # IBM or Oracle
	my $java_version_string   = 'Unknown'; # 1.6.0, 1.7.0 etc.  from java -version output
	my $build_string          = 'Unknown'; # e.g. pwa6490ea-20160104_02 or 1.8.0_92-b14
	my $build_prefix          = 'Unknown'; # e.g. pwa6490ea IBM only
	my $build_date            = 'Unknown'; # e.g. 20160104 IBM only
	my $build_suffix          = 'Unknown'; # e.g. 02 IBM only
	my $os                    = 'Unknown'; # w, a, x, m IBM only
	my $arch                  = 'Unknown'; # p, z, i, l, r IBM only
	my $bits                  = 'Unknown'; # 31, 32, 64
	my $release               = 'Unknown'; # 60, 60_26, 70 etc. or 1.7.0, 1.8.0, 9 etc.
	my $short_release         = 'Unknown'; # 6, 7, 8 etc.
	my $release_suffix        = 'Unknown'; # e.g. 'ea'  IBM only
	my $sr                    = '';        # Just the number
	my $fp                    = '';        # Just the number or b14 etc. for Oracle
	my $java_platform         = 'Unknown'; # e.g. linux_x86-32
	my $java_platform_long    = 'Unknown'; # e.g. linux_x86-32_ibm or linux_x86-32_oracle

    foreach my $line ( @{$lines} ) {
    	# Find the line containing the java version string
   	    if ( $line =~ /^java version/ ) {
	    	#print "Parsing line: $line\n";
	    	# Match "java version" followed by one or spaces followed by zero or more double quotes
	    	# Extract one or more characters which are not a quote
	    	# Match on zero or more remaining characters 
	    	( $java_version_string )= $line =~ /^java version\s+[\"]*([^\"]+).*/;
	    }
	    if ( $line =~ /^openjdk version/ ) {
	    	( $java_version_string )= $line =~ /^openjdk version\s+[\"]*([^\"]+).*/;
	    }
	    
    	if ( $line =~ m/IBM/ ) {
    		$java_vendor = 'IBM';
    	}
    	if ( $line =~ m/HotSpot/ ) {
    		$java_vendor = 'Oracle';
    	}
    	if ( $line =~ m/OpenJDK/ ) {
    		$java_vendor = 'OpenJDK';
    	}
    }
	if ( $java_vendor eq 'IBM' ) {
	    foreach my $line ( @{$lines} ) {
	    	# Find the line containing the build identifier string
    		if ( $line =~ /Java\(TM\) SE Runtime Environment \(build / ) {
		    	#print "Parsing line: $line\n";
		    	# Example:
		    	# Java(TM) SE Runtime Environment (build pmz6460sr16fp25-20160422_01(SR16 FP25))
				# Java(TM) SE Runtime Environment (build JRE 1.9.0 IBM Windows build 2016-09-02-182741)
    			( $build_string ) = $line =~ /^Java\(TM\) SE Runtime Environment \(build ([^\-]+\-[^\_]+\_\d\d).*/;
		    	if ( ! defined ($build_string) || $build_string eq "" ) {
					stf::stfUtility->logMsg ( message => "stf::stfUtility->parseJavaVersionInfo: Unable to parse java -version output line\n$line");
		    		next;
		    	}
    			( $build_prefix, $build_date, $build_suffix ) = $build_string =~ /([^\-]+)\-([^\_]+)\_(\d\d)/;
				( $os, $arch, $bits, $release, $release_suffix ) = $build_prefix =~ /^[^x|w|a|m]+([x|w|a|m])([a|i|l|p|r|z])(\d\d)([\d]+\_[\d]+|[\d]+)(.*)/;
				# release_suffix is now empty, or something like 'ea', or 'sr3' or 'sr3fp30'
				if ( $release_suffix =~ m/fp/ ) {
					( $release_suffix, $fp ) = $release_suffix =~ /(.*)fp(\d+).*$/;
				}
				if ( $release_suffix =~ m/sr/ ) {
					( $release_suffix, $sr ) = $release_suffix =~ /(.*)sr(\d+).*$/;
				}
				else {
					$sr = $release_suffix;
				}
			    my $lang_level = $release;
			    my $vm_level = "";
				if ( $release =~ m/_/ ) {
					( $lang_level, $vm_level ) = $release =~ /(.*)_(.*)/;
				}
				$lang_level = $lang_level / 10;  # Change 60 to 6 etc.
				$short_release = $lang_level . $vm_level;
			}
		}
	}

	if ( $java_vendor eq 'Oracle' ) {
	    foreach my $line ( @{$lines} ) {
	    	# Find the line containing the build identifier string
			if ( $line =~ /Java\(TM\) SE Runtime Environment \(build / ) {
		    	#print "Parsing line: $line\n";
		    	# Examples:
		    	# Java(TM) SE Runtime Environment (build 1.8.0_92-b14)
			# Java(TM) SE Runtime Environment (build 9-ea+124)
    			( $build_string ) = $line =~ /^Java\(TM\) SE Runtime Environment \(build ([^\)]+).*/;
				# Use the '+' to distinguish between the formats
				if ( $line =~ /\+/ ) {
	    			( $release, $sr, $fp ) = $build_string =~ /([^\-]+)\-([^\+]+)\+(.*)/;
				}
				else {
	    			( $release, $sr, $fp ) = $build_string =~ /([^\_]+)\_([^\-]+)\-(.*)/;
	    		}
    		}
	    	# Is the Java 64 bit?
	    	$bits = 32;
    		if ( $line =~ /64-Bit/ ) {
    			$bits = 64;
    		}
		}
		# We can't deduce operating system or architecture from Oracle build identifier, so let's try to work it out ourselves
		if ( $Config{osname} =~ /linux/ ) {
			$os = 'x';
		}
		elsif ( $Config{osname} =~ /Win/ ) {
			$os = 'w';
		}
		else {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->parseJavaVersionInfo: add code for Oracle Java running on osname " . $Config{osname} );
		}

		if ( $Config{archname} =~ /x86/ && $bits == 32 ) {
			$arch = 'i';
		}
		elsif ( $Config{archname} =~ /x86/ && $bits == 64 ) {
			$arch = 'a';
		}
		elsif ( $Config{archname} =~ /arm/  ) {
			$arch = 'r';
		}
		elsif ( $Config{archname} =~ /riscv/  ) {
			$arch = 'v';
		}
		elsif ( $Config{archname} =~ /loongarch/  ) {
			$arch = 'la';
		}
		else {
		    print "Logging\n";
			stf::stfUtility->logMsg ( message => "stf::stfUtility->parseJavaVersionInfo: add code for Oracle java running on archname " . $Config{archname} );
		}
		# Assign 'short release' based on the java version string
		if ( $java_version_string =~ /1.6.0/ ) { $short_release = '6'; }
		if ( $java_version_string =~ /1.7.0/ ) { $short_release = '7'; }
		if ( $java_version_string =~ /1.8.0/ ) { $short_release = '8'; }
		if ( $java_version_string =~ /^9.*/ ) { $short_release = '9'; }
	}
	
	if ( $java_vendor eq "OpenJDK" ) {
	    foreach my $line ( @{$lines} ) {
	    	# Find the line containing the build identifier string
			if ( $line =~ /OpenJDK Runtime Environment \(build / ) {
		    	#print "Parsing line: $line\n";
		    	# Examples:
			# OpenJDK Runtime Environment (build 1.8.0-internal-jenkins_2017_05_17_18_01-b00)
                        # OpenJDK Runtime Environment (build 1.8.0_91-b14)
                        # OpenJDK Runtime Environment (build 9-internal+0-adhoc.sxa.openjdk-jdk9)
                                                                       			
    			( $build_string ) = $line =~ /^OpenJDK Runtime Environment \(build ([^\)]+).*/;
			# Use the '-internal' or '+' to distinguish between the formats
			if ( $line =~ /\+/ ) {
	    			( $release, $sr, $fp ) = $build_string =~ /([^\-]+)\-([^\+]+)\+(.*)/;
			} elsif ( $line =~ /\-internal/ )  {
	    			( $release, $sr, $fp ) = $build_string =~ /([^\-]+)\-[^\_]+\_([^\-]+)\-(.*)/;
			} else {
	    			( $release, $sr, $fp ) = $build_string =~ /([^\_]+)\_([^\-]+)\-(.*)/;
	    		}
    		}
	    	# Is the Java 64 bit?
	    	$bits = 32;
    		if ( $line =~ /64-Bit/ ) {
    			$bits = 64;
    		}
		}
		# We can't deduce operating system or architecture from Oracle build identifier, so let's try to work it out ourselves
		if ( $Config{osname} =~ /linux/ ) {
			$os = 'x';
		}
		elsif ( $Config{osname} =~ /Win/ ) {
			$os = 'w';
		}
		elsif ( $Config{osname} =~ /BSD/ ) {
			$os = 'b';
		}
		else {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->parseJavaVersionInfo: add code for Oracle Java running on osname " . $Config{osname} );
		}

		if ( $Config{archname} =~ /x86/ && $bits == 32 ) {
			$arch = 'i';
		}
		elsif ( $Config{archname} =~ /x86/ && $bits == 64 ) {
			$arch = 'a';
		}
		elsif ( $Config{archname} =~ /arm/  ) {
			$arch = 'r';
		}
		elsif ( $Config{archname} =~ /sparc/  ) {
			$arch = 'q';
		}
		else {
		    print "Logging\n";
			stf::stfUtility->logMsg ( message => "stf::stfUtility->parseJavaVersionInfo: add code for Oracle java running on archname " . $Config{archname} );
		}
		# Assign 'short release' based on the java version string
		if ( $java_version_string =~ /1.6.0/ ) { $short_release = '6'; }
		if ( $java_version_string =~ /1.7.0/ ) { $short_release = '7'; }
		if ( $java_version_string =~ /1.8.0/ ) { $short_release = '8'; }
		if ( $java_version_string =~ /^9.*/  ) { $short_release = '9'; }
	}
	
	
	if ( $os eq 'x' ) { $java_platform = 'linux_'; }
	if ( $os eq 'w' ) { $java_platform = 'win_'; }
	if ( $os eq 'a' ) { $java_platform = 'aix_'; }
	if ( $os eq 'm' ) { $java_platform = 'zos_'; }
	if ( $os eq 'b' ) { $java_platform = 'bsd_'; }
	if ( $arch eq 'a' ) { $java_platform .= 'x86-'; }
	if ( $arch eq 'i' ) { $java_platform .= 'x86-'; }
	if ( $arch eq 'l' ) { $java_platform .= 'ppcle-'; }
	if ( $arch eq 'p' ) { $java_platform .= 'ppc-'; }
	if ( $arch eq 'r' ) { $java_platform .= 'arm-'; }
	if ( $arch eq 'v' ) { $java_platform .= 'riscv-'; }
	if ( $arch eq 'la' ) { $java_platform .= 'loongarch-'; }
	if ( $arch eq 'q' ) { $java_platform .= 'sparc-'; }
	if ( $arch eq 'z' ) { $java_platform .= '390-'; }
	if ( $java_platform ne 'Unknown' ) {
		$java_platform .= $bits;
		$java_platform_long = lc($java_platform . "_" . $java_vendor);
	}

   	#print "java_version_string: $java_version_string\n";
   	#print "java_vendor: $java_vendor\n";
   	#print "build_prefix: $build_prefix\n";
   	#print "build_string: $build_string\n";
   	#print "build_date: $build_date\n";
   	#print "build_suffix: $build_suffix\n";
   	#print "os: $os\n";
   	#print "arch: $arch\n";
   	#print "bits: $bits\n";
   	#print "release: $release\n";
   	#print "short_release: $short_release\n";
   	#print "sr: $sr\n";
   	#print "fp: $fp\n";
   	#print "java_platform: $java_platform\n";
   	#print "java_platform_long: $java_platform_long\n";

	my %java_details;
	$java_details{'JAVA_VERSION_STRING'} = $java_version_string;
	$java_details{'JAVA_VENDOR'} = $java_vendor;
	$java_details{'JAVA_BUILD_STRING'} = $build_string;
	$java_details{'JAVA_BUILD_PREFIX'} = $build_prefix;
	$java_details{'JAVA_BUILD_DATE'} = $build_date;
	$java_details{'JAVA_BUILD_SUFFIX'} = $build_suffix;
	$java_details{'JAVA_OS'} = $os;
	$java_details{'JAVA_ARCH'} = $arch;
	$java_details{'JAVA_BITS'} = $bits;
	$java_details{'JAVA_RELEASE'} = $release;
	$java_details{'JAVA_SHORT_RELEASE'} = $short_release;
	$java_details{'JAVA_SERVICE_REFRESH'} = $sr;
	$java_details{'JAVA_FIXPACK'} = $fp;
	$java_details{'JAVA_PLATFORM'} = $java_platform;
	$java_details{'JAVA_PLATFORM_LONG'} = $java_platform_long;
	
	return %java_details;
}

sub strip_zos_unable_to_switch_to_IFA_processor_message {
	
	my ($self, $lines) = @_;
	
	# If not on zOS, return
	if ($^O !~ /os390/) {
		return $lines;	
	}

	my $stripped_array = [];

	# If there are no messages, return;
	if (!defined $lines or scalar @$lines == 0) {
		return $lines;
	}
	
	for (my $index; $index < scalar @$lines; $index++) {
		
		if (@$lines[$index] !~ /Unable to switch to IFA processor/) {
			push (@$stripped_array, @$lines[$index]);
		} else {
			stf::stfUtility->logMsg ( message => "Stripping \"Unable to switch to IFA processor\" message from file contents (This is normal if the SDK is on an NFS mount)");
		}
		
	}
	
	#$errors = $stripped_array;
	return $stripped_array;
	
}

#----------------------------------------------------------------------------------#
# getJavaProperties
#
# Usage:
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(JAVA_HOME => 1);
#  %java_details = stf::stfUtility->getJavaVersionInfo();
# - Gets details for the Java at JAVA_HOME
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(PATH => 1);
# - Gets details for the Java on the PATH
#
#  %java_details = stf::stfUtility->getJavaVersionInfo(PATH => <path>);
# - Gets details for the Java at <path>
#----------------------------------------------------------------------------------#
sub getJavaProperties {

	my ($self, %options) = @_;

	my $valid_input = 1;
	foreach my $key (sort keys %options) {
		if ( $key ne 'PATH' &&
		     $key ne 'JAVA_HOME' ) {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->getJavaProperties: Received unrecognised option $key\n" );
			$valid_input = 0;
		}
	}
	
	my %java_properties;
	 
    my $tempInst;
    if ($^O eq "MSWin32" ) {
		$tempInst = File::Temp::tempdir(CLEANUP => 1);
	} else { 
		$tempInst="/tmp";
	}
	
	my $propertieslog = catfile($tempInst,"java_properties");
	debug("stf::stfUtility->getJavaProperties: propertieslog is $propertieslog");

	my $path_to_java = "";

	if ( $valid_input == 1 ) {
		if ( defined $options{'PATH'} ) {
			if ( $options{'PATH'} eq '1' ) {
				$path_to_java = "";
			}
			else {
				$path_to_java = "$options{'PATH'}/bin/";
			}
		}
		elsif ( !defined $ENV{'JAVA_HOME'} || $ENV{'JAVA_HOME'} eq "" ) {
			stf::stfUtility->logMsg ( message => "stf::stfUtility->getJavaProperties: Environment variable JAVA_HOME is not set, unable to retrieve Java properties");
			$valid_input = 0;
		}
		else {
			$path_to_java = "$ENV{'JAVA_HOME'}/bin/";
		}
	}

	if ( $valid_input == 1 ) {
		my $command = $path_to_java . "java";
		# stdout goes to $propertieslog.stdout
		# stderr goes to $propertieslog.stderr
		my ($rc, $process) = stf::Commands->run_process(mnemonic => "stf::stfUtility->getJavaProperties", command => "$command", args => "-XshowSettings:properties -version", logName => $propertieslog, echo => 0);
	
	    if ($rc != 0) {
			stf::stfUtility->logMsg( message => "stf::stfUtility->getJavaProperties: $command did not complete with rc = 0");
	    }
	    my $lines = $self->readFileIntoArray("$propertieslog.stderr");

	    # Strip out messages relating to zOS file permissions, because we may be on an NFS mount on zOS
	    $lines = $self->strip_zos_unable_to_switch_to_IFA_processor_message($lines);
	    
	    my $property = "";
	    my $value = "";
		foreach my $line (@{$lines}) {
			if ( $line =~ /=/ ) {
				if ( $property ne "" ) {
					$property =~ s/\s+$//;
					$property =~ s/^\s+//;
					$value =~ s/^\s+//;
					$value =~ s/\s+$//;
					$java_properties{$property} = $value;
				}
				($property, $value) = $line =~ /(.*)\=(.*)/;
			}
			else {
				$line =~ s/^\s+//;
				$line =~ s/\s+$//;
				$value = "$value $line";
			}
		}

		if ( $property ne "" ) {
			$property =~ s/\s+$//;
			$property =~ s/^\s+//;
			$value =~ s/^\s+//;
			$value =~ s/\s+$//;
			$java_properties{$property} = $value;
		}

	}

    return %java_properties;
}

1;
