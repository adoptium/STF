# STF Internals

<a href="#1">Terminology</a>  
<a href="#2">Code Structure</a>  
&nbsp;&nbsp;&nbsp;&nbsp;<a href="#2.1">2.1 stf.core Project</a>

<a name="1"></a>
## Terminology

Some key terms are:
- Plugin - This is a user written test case which 'plugs' into STF at runtime.
-- Each plugin contains 4 methods: pluginInit, setup, execute and teardown.
- Extension - A class which provides operations to plugin code, such as create a directory, unpack a file, etc.
-- Plugins make use of extensions to generate perl code.

<a name="2"></a>
## Code Structure

The code for STF exits within 3 projects:
- stf.core - Holds key STF code. This is sufficient to run tests on their own.
- stf.java - Contains the Java extension, which adds capabilities related specifially to testing Java implementations.</li>
- stf.load - Standalone tool used by STF to run multi-threaded load tests.</li>

<a name="2.1"></a>
### stf.core Project

The code within stf.core is divided into the following packages:
- *src.stf.net.adoptopenjdk.stf* - This top level package holds simple classes for StfException, constants, etc.
- *src.stf.net.adoptopenjdk.stf.runner* - Key classes for running Java code as a STF plugin test.
StfRunner.java contains the critical main method which is invoked by stf.pl.
The 'modes' subpackage contains code for handling the stf '-help' and '-list' options.
- *src.stf.net.adoptopenjdk.stf.environment* - Contains classes to represent the machine and the environment that the test is running on.
Key classes are FileRef.java and DirRef.java to represent files and directories.
The 'properties' sub package contains classes to manage the hierarchical properties supported by STF tests and extension classes.
- *src.stf.net.adoptopenjdk.stf.plugin.interfaces* - Defines key interfaces needed by test classes.
- *src.stf.net.adoptopenjdk.stf.extensions* - Holds logic and common funnctions needed by extension classes.
- *src.stf.net.adoptopenjdk.stf.extensions.core* - Implementation of the virtually essential 'StfCoreExtension', which
provides basic actions needed by most tests.
- *src.stf.net.adoptopenjdk.stf.processes* - Support for tests which run child processes.
- *src.stf.net.adoptopenjdk.stf.processes.definitions* - Utility classes used to build command lines for running
different types of processes. eg, running Java, jlink or load test processes.
The subpackage 'generic' contains code which makes writing process definition classes easier.
- *src.stf.net.adoptopenjdk.stf.codeGeneration* - Code for creating perl scripts, so that tests can run child processes on the current platform.
- *src.stf.net.adoptopenjdk.stf.results* - Utility classes for examining test results. Supports the filtering of results from known failing tests.
- *src.stf.net.adoptopenjdk.stf.util* - Simple generic classes for parsing duration strings, running child processes, etc.
