#!/usr/bin/perl

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This file holds test subroutines for SampleCustomExtensionTest.java
#

package ExampleModule;

our $TRUE = 1;
our $FALSE = 0;


sub checkGCData
{
   my ($module, $logFile) = @_;
	
   my $total_fails = 0;
   my $errors = [];
   
   print "GC data verfication starting...\n";
   
   my $log = storeFile($logFile);
   my $start = $FALSE;
   my $end = $FALSE;
   
   my $cnt = 0;
   foreach my $line (@{$log})
   {   	
   	  $cnt++;
   	  my $match = $FALSE;
   	  if ($line =~ /GC agent started at /)
   	  {
   	  	 $start = $TRUE;
   	  	 $match = $TRUE;
   	  }
   	  
   	  if ($line =~ /^$/)
   	  { 
   	  	 $match = $TRUE;
   	  }
   	  
   	  if (($line =~ /GC Finish:/) && ($start == $TRUE))
   	  {
   	  	 $match = $TRUE;
   	  }	 
   	  
   	  if (($line =~ /GC Start:/) && ($start == $TRUE))
   	  {
   	  	 $match = $TRUE;
   	  }	 
   	  
   	  if ($line =~ /GC count at program end:/)
   	  {
   	  	 $end = $TRUE;
   	  	 last;
   	  }
   	  
   	  if ($line =~ /\*\*JVMTI ERROR\*\*/)
   	  {
	      $total_fails++;
	      chomp($line);
		  push(@{$errors}, $line." found in the $logFile at line $cnt");		       	  	  
   	  }
   	  
   	  if ($start == $TRUE && $match == $FALSE)
   	  {
   	  	 $total_fails++;
	     push(@{$errors}, "Unexpected output found in the $logFile at line $cnt");		    
	     
   	  }		  	 
   }
   
   if ($end == $FALSE)
   {
      $total_fails++;
	  push(@{$errors}, "Expected Agent ended message missing from $logFile");		    
	     
   }		  	 
      
   logMsg("GC data verfication complete");
   return ($errors, $total_fails);   
}       


sub checkHeapData
{
   my ($module, $logFile) = @_;
	
   my $total_fails = 0;
   my $errors = [];
   
   logMsg("Heap data verfication starting...");
   
   my $log = storeFile($logFile);
   my $start = $FALSE;
   my $match = $FALSE;
   my $cnt = 0;
   foreach my $line (@{$log})
   {   	
   	  $cnt++;
   	  $match = $FALSE;
   	  if ($line =~ /Heap agent started at /)
   	  {
   	  	 $start = $TRUE;
   	  	 $match = $TRUE;
   	  	 last;
   	  }
   	  
   	  if (($line =~ /HeapReference info: class_tag \d, referrer_class_tag \d, size \d, length \d/) && ($start == $TRUE))
   	  {
   	  	 $match = $TRUE;
   	  }
   	  
   	  if ($line =~ /\*\*JVMTI ERROR\*\*/)
   	  {
	      $total_fails++;
	      chomp($line);
		  push(@{$errors}, $line." found in the $logFile at line $cnt");		       	  	  
   	  }
   	  
   	  if ($start == $TRUE && $match == $FALSE)
   	  {
   	  	 $total_fails++;
	     push(@{$errors}, "HEAP: Unexpected output found in the $logFile at line $cnt");		    
   	  }		  	 
   }
   
   if ($match == $FALSE)
   {
      $total_fails++;
	  push(@{$errors}, "HEAP: Unexpected output found in the $logFile");		    
   }		  	 
   

   logMsg("Heap data verfication complete");
   return ($errors, $total_fails);   
}                


sub logMsg 
{
   my ($errorMessage) = @_;
   print "$errorMessage\n";
}


sub storeFile
{
   # get filename and search argument
   my ($filename) = @_;
   my @lines = ();
   
   # strip out any quotes...
   ($filename) =~ s/\"//g;

   # check the file exists
   if (!(-e $filename))
   {
      print "SYSTEM ERROR: The system cannot find the file $filename.\n";
      return [];
   }

   if (!(-r $filename))
   {
      print "SYSTEM ERROR: The system does not have read access to the file $filename.\n";
      return [];
   }

   # open file
   open (FILE, "<$filename");
      
   # search array
   @lines = <FILE>;

   close FILE;
   return \@lines;
}

1;
