# STF - Getting started

<a href="#1">Introduction</a>  
<a href="#2">Setting up development environment</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.1">Prerequisites</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.2">Configure Eclipse</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.3">Clone and build the stf git repository</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.4">Clone and build the openjdk git repository</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.5">Verifying the environment</a>  
<a href="#3">STF mini guide</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.1">How do I see all available tests?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.2">How do I find the source code for a test?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.3">Where can I find the log files for a run?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.4">How do I find a test's command line options?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.5">How do I see the generated perl code which is executed when the test runs?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.6">How can I re-execute the test?</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#3.7">How can I tweak a Java command?</a>  

<a name="1"></a>
## Introduction

This page documents the steps needed to get STF based tests running
from source on a development machine.

On completion of these instructions you'll have an
Eclipse installation with a workspace
that contains the source code for STF itself and the AdoptOpenJDK tests which use it. These tests will be runnable from the command prompt and should pass.

Eclipse is used a development tool to make the development/debugging of test
automation quicker and easier, with local compile time checking, and tests which can execute
directly from the Eclipse workspace.

Native (C code) tests need to be built using the ant based build, which can be invoked from within the Eclipse 'Ant' View or from a command line.

As the title suggests, the purpose of this document is to get you to the point where you can run tests. Refer to STF-Manual.md for information such as:
- Overview of how STF works
- How to add automation for a new test
- Debugging and fixing test automation
- Coordination of multiple concurrent processes
- Rules and guidelines for test automation

<a name="2"></a>
## Setting up the development environment

<a name="2.1"></a>
### Prerequisites

The easiest way to setup the STF and test prereqs is to run the configure build targets.  STF and each test repository containing STF dependent tests has such a target.  When  the configure target is run wget is used to download the various software packages, which are then installed as required.

However, there are a few prereqs which cannot be installed in this way, since the are required for the configure step itself or are too complex to install via . These prereqs are:
1. Java
1. Eclipse (use a version which comes with a git client and supports Java 9)
1. wget
1. ant
1. GNU make
1. perl
1. Microsoft Visual Studio (Windows only)

Some of the prereqs (e.g. perl, make) may already be installed on the test machine.

You should review the licenses of the prereqs before running the configure step to make sure you agree with their terms and conditions. Refer to stf.build/docs/build.md (or equivalent files in other projects which contain tests which use STF).

<a name="2.2"></a>
## Configure Eclipse
Download and extract Eclipse to a suitable directory. Then start Eclipse and create a new workspace.

Note: Do not create a workspace whose path includes a space character. STF will abort the run if the workspace path contains a space.

Verify that the Eclipse version works with at least Java 8 by opening
'Window -> Preferences -> Java -> Compiler' and checking that 'Compiler compliance level' is at least 1.8.

<a name="2.3"></a>
### Clone and build the stf git repository
1. Follow the instructions in stf.build/docs/readme.md to clone the stf git repository and set up the prereqs.
1. Import the projects into Eclipse (Find and import Eclipse projects)
1. Eclipse will attempt to build java classes itself, but this will not succeed until the prereqs have been imported into the Eclipse workspace.
1. Import the prerreqs as a 'General' Eclipse project under the project name 'systemtest_prereqs'.
1. The stf projects should now build without build errors.

<a name="2.4"></a>
### Clone and build the openjdk-systemtest git repository
1. Follow the instructions in openjdk.build/docs/readme.md to clone the openjdk-systemtest git repository and set up the prereqs.
1. Import these projects into Eclipse alongside the stf projects (Find and import Eclipse projects)
1. Add any additional prereqs from the openjdk-systemtest make configure step to the systemtest_prereqs folder (use File --> Import).
1. The openjdk.xxx projects should also now build without build errors.
1. The native (C code) test cases cannot be built automatically by Eclipse.
 To build these run the ant build.  This can be done from within Eclipse by opening the Ant View,
 dragging the openjdk.build/build.xml file into the View and then running the 'build' target.
  Note that a suitable C compiler needs to be installed prior to invoking the native build.
   See the Prerequisites section above for more details.

<a name="2.5">
### Verifying the environment

#### Windows

```
perl -v
This is perl 5, version 22, subversion 1 (v5.22.1) built for...
(or similar)
```

```
java -version
java version "1.8.0_nn"
...
(or similar)
```

```
%JAVA_HOME%/bin/java -version
java version "1.8.0_nn"
...
(or similar)
```

```
%USERPROFILE%\git\stf\stf.core\scripts\stf.pl -test=UtilLoadTest</b>
...
STF 16:04:08.625 - Overall result: PASSED
```
#### Unix

```
perl -v
This is perl 5, version 18, subversion 2 (v5.18.2) built for ...
(or similar)
```

```
java -version
java version "1.8.0_nn"
...
(or similar)
```

```
$JAVA_HOME/bin/java -version
java version "1.8.0"
...
(or similar)
```

```
perl $HOME/git/stf/stf.core/scripts/stf.pl -test=UtilLoadTest
...
STF 15:56:06.778 - Overall result: PASSED
```

<a name="3"></a>
## STF mini guide

<a name="3.1"></a>
### How do I see all available tests?
STF can scan your workspace and list all classes which are runnable as STF based tests.
This is obviously useful for getting a full list of all tests, but is also useful when
you want the full test name but only know part of the name.

```
perl $HOME/git/stf/stf.core/scripts/stf.pl -test-root="$HOME/git/openjdk-systemtest" -list

perl $HOME/git/stf/stf.core/scripts/stf.pl -test-root="$HOME/git/openjdk-systemtest" -list | grep -i memory

GEN 10:51:58.145 -   | test.jlm               | TestJlmRemoteMemoryAuth        |
GEN 10:51:58.145 -   | test.jlm               | TestJlmRemoteMemoryNoAuth      |

perl $HOME/git/stf/stf.core/scripts/stf.pl -test=SampleClientServer
...
GEN 10:48:01.185 - Test command summary:
GEN 10:48:01.185 -   Step  Stage       Command       Description
GEN 10:48:01.185 -  -----+--------+-----------------+------------
GEN 10:48:01.185 -     1  execute  Run java          Run server
GEN 10:48:01.185 -     2  execute  Run java*4        Run client
GEN 10:48:01.185 -     3  execute  Monitor           Wait for clients to complete
GEN 10:48:01.185 -     4  execute  kill              Stop server process
...
STF 10:48:05.354 - Overall result: PASSED
```


<a name="3.2"></a>
### How do I find the source code for a test?

Once you have the name of a test you can find its source code in Eclipse by
typing Ctrl-shift-t and type in the name of the test automation STF plugin, eg, SampleClientServer

<a name="3.3"></a>
### Where can I find the log files for a run?

All log files are written to a results subdirectory under /tmp/stf on unix or C:\stf_temp on Windows (default locations, can be overridden via the -results-root stf.pl argument).

<a name="3.4"></a>
### How do I find a test's command line options?

The full set of STF depends on exactly which test you want to run. So
to get a UNIX style list of the available options you need to use '-help'
argument in conjunction with a '-test=x' argument.

```
perl $HOME/git/stf/stf.core/scripts/stf.pl -test-root="$HOME/git/openjdk-systemtest" -test=UtilLoadTest -help
```

<a name="3.5"></a>
### How do I see the generated perl code which is executed when the test runs?

<p>The actions described in the testcase are executed by perl code because
it is suitable for operating system level operations and is available on
all platforms. To see that commands which were executed for a test look
in '&lt;stf-results-dir&gt;/&lt;test-name&gt;/execute.pl'.

<a name="3.6"></a>
### How can I re-execute the test?

If a test has a significant setup time and you want to repeatedly rerun then
it is usually easiest to just rerun the execute script. Providing that the test
doesn't do any teardown work this can be run after the stf run, or alternatively
you can manually run the setup, execute and teardown scripts.

For fully manual execution:
```
perl $HOME/git/stf/stf.core/scripts/stf.pl -test-root="$HOME/git/openjdk-systemtest" -test=UtilLoadTest -dry-run</b>
...
C:\stf_temp\20170801-133510-UtilLoadTest
(STF creates the results directory yyyymmdd-hhmmss-testname)
perl /tmp/stf/20170801-133510-UtilLoadTest/setUp.pl</b>
perl /tmp/stf/20170801-133510-UtilLoadTest/execute.pl</b>
perl /tmp/stf/20170801-133510-UtilLoadTest/teardown.pl</b>
...
```

<a name="3.7"></a>
### How can I tweak a Java command?
1. Because STF generates unique setup.pl, execute.pl and teardown.pl scripts each time it runs
you are free to edit any of the commands in those scripts and then reexecute as shown in the previous section.
 
1. Alternatively, and depending on the test, a more complex but flexible option is to
extract the the command for a step and manually run it on the command line. Generally each step
has a cut and pastable version of every command, with all paths etc. fully expanded.

</body>
</html>
