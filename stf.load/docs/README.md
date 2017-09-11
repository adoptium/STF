STF Load Test
=============

## Contents

  * [Overview](#overview)
  * [Load Test Features](#load-test-features)
     * [Test selection](#test-selection)
        * [Sequential selection](#sequential-selection)
        * [Random selection](#random-selection)
     * [Inventory files](#inventory-files)
        * [Included inventory files](#included-inventory-files)
        * [Exclude inventory files](#exclude-inventory-files)
        * [Test weightings](#test-weightings)
     * [Test adaptors](#test-adaptors)
        * [JUnit tests](#junit-tests)
        * [Mauve tests](#mauve-tests)
        * [Arbitrary Java tests](#arbitrary-java-tests)
     * [Execution logs](#execution-logs)
     * [Formatting execution log ltm and ltd files](#formatting-execution-log-ltm-and-ltd-files)
        * [Detail execution log formatter](#detail-execution-log-formatter)
        * [Failure execution log formatter](#failure-execution-log-formatter)
        * [Summary execution log formatter](#summary-execution-log-formatter)
        * [MetaData execution log formatter](#metadata-execution-log-formatter)
     * [Execution log disk space and pruning](#execution-log-disk-space-and-pruning)
     * [Java dumps](#java-dumps)
     * [Progress reporting](#progress-reporting)
     * [Hang detection](#hang-detection)
     * [Algorithm summary](#algorithm-summary)
  * [STF Java Interface](#stf-java-interface)
     * [Java methods](#load-test-jvm-arguments)
        * [Load test JVM arguments](#load-test-jvm-arguments)
        * [Arguments for the load test program itself](#arguments-for-the-load-test-program-itself)
        * [To control test runtime](#to-control-test-runtime)
        * [Suite Control](#suite-control)
        * [Test selection](#test-selection)
     * [Example Java test code](#example-java-test-code)
     * [Example run](#example-run)
  * [The Load Test Program Itself](#the-load-test-program-itself)
     * [Load test arguments](#load-test-arguments)
     * [Comments about load test arguments](#comments-about-load-test-arguments)
     * [Example command line](#example-command-line)
  * [Porting tests](#porting-tests)
  * [Reproducing failures](#reproducing-failures)
  * [Future Enhancements](#future-enhancements)


## Overview

The load test application is a multi-threaded Java program which repeatedly executes
collections of Java tests. It aims to find JVM issues which occur only when the JVM
has been active for a period of time, such as:
 - just in Time (JIT) compiler errors.
 - garbage collector errors.
 - memory leaks.

It does this JVM stress testing by recycling existing or open source tests.
The available tests for a load test run are listed in 'inventory' files:
 - xml formatted files. 1 test per line.
 - to help with managing subsets an inventory file can include other inventory files.
 - broken tests can be added to an inventory exclude file.
 - frequency of execution is controlled by optional per-test weighting attribute.

Load test does its best to help with debugging in the face of a potentially unstable JVM:
 - generates javacore on first failure.
 - records start and end of each test to execution log file.
 - seed for randomly selecting tests can be recycled to get same test ordering.
 - stdout/stderr captured on a per test basis. Logged on failure with no intermingling of other test output.
 - minimal memory footprint. Can run indefinitely.

Load test aims to expose the bare minimum of complexity in the configuration of a
load test scenario. A minimal set of arguments would allow light load to be
placed on a single worker thread, whereas a more complex scenario would specify several
test suites all running multiple threads and selecting their tests with different algorithms.
The key arguments are:
 - time based run vs. fixed test invocation count.
 - random vs. sequential test selection.
 - for random selection, a fixed or randomly generated starting seed.
 - number of worker threads.
 - test repetition. Defaults to 1.


## Load Test Features

#### Test selection

##### *Sequential selection*
If a suite is set to run tests sequentially then tests are executed from top to
bottom of the test inventory file. Once the end of the inventory has been reached
selection starts again from the top.

If a suite has tests A, B, C, D, E and the suite has been told to run 7 tests
then it would execute ABCDEAB. With a repeat count of 2 it would run AABBCCD.

##### *Random selection*
When running with random selection the next test to run is decided by the suites
random number generator. The starting value can either be set explicitly by the
test or a new seed selected by using the default value of '-1'.

Reusing a seed from a failing run will reproduce the same sequence of tests.
However, the reproduction will only happen if there haven't been any inventory
changes. This is because the tests are numbered at start-up time, so any additions
or removals will change test number to test mappings.

The load test program outputs the seed value being used near the start of the
test run. To reuse the seed you can do either:
1. Add a '.setSuiteSeed(long)' call in the tests source code.
1. After a test run edit the 'exceute.pl' script and replace the existing
'-suite.{suite-name}.seed' value.

For example, if running with a suite containing tests A, B, C, D, E, and repeat
count=1, seed=-1, could result in the selection of seed 9223852004 and the running
of tests CEACBEE.
Running the same suite with a repeat count of 3 could result in CCCEEEA.


#### Inventory files

The tests to be executed are listed in xml format 'inventory' files. Here is an example:
```
<inventory>
	<junit class="net.adoptopenjdk.stf.MiniJUnitTest" />
	<arbitraryJava class="net.adoptopenjdk.stf.ArbitraryJavaTest" method="runSimpleTest" />
</inventory>
```
The keywords 'junit' and 'arbitraryJava' are known as 'test types'.

An inventory files can contain any sequence of supported test types. So the example above directs stf.load
 test to run junit and arbitraryJava tests.

STF load test copies the inventory files to the results directory, and these files
are used by the executed perl code (in say execute.pl). This is done for several
reasons:
1. The used inventory files are visible in the any results file uploads - e.g. after a Jenkins job
which executes the test.
1. You can edit the inventory files are rerun 'execute.pl', with having to create a repository and
build sandbox environments or having to make permanent edits.

Copied inventory files can be found in the directory at '${resultsDir}/${resultsPrefix}inventory'.

##### *Included inventory files*
In order to allow composition and reuse of existing inventory files, inventory
files can include other inventory files. For example:
```
<inventory>
    <include inventory="/test.load/config/inventories/sampleLoadTest/subtests/arbitraryJavaInventory.xml"/>
    <include inventory="/test.load/config/inventories/sampleLoadTest/subtests/junitInventory.xml"/>

	<junit class="net.adoptopenjdk.stf.MiniJUnitTest" />
</inventory>
```

The include node specifies the location of the include file within the workspace,
ie, it starts with the top level project. It uses the '/' character as a platform
neutral directory separator.

Included inventory files can themselves include other inventory files.

Included inventory files are copied to the results directory at test execution time. The directory structure
is replicated so that inventory files can be copied without modification. This makes it easy to modify
the inventory files locally following a test run to aid a failure diagnosis.

##### *Exclude inventory files*

Tests which are known to fail and which you don't want to run can be listed
in an 'exclude' file. Tests which are listed in the inventory file and the
corresponding exclude file are not executed.

Exclude files are identified by the following rules:
1. Exclude files live in the same directory as the inventory file.
1. The exclude file has the same base file name as the inventory file.
2. The base name of the exclude file ends with the regular expression '_exclude.*'.

Exclude files have the same format as inventory files.

For example, if there is an inventory file 'junitInventory.xml' then the tests listed
in 'junitInventory_exclude-201495.xml' and 'junitInventory_exclude-sql-tests.xml' would not
be executed.

The rationale behind this approach is that if tests are temporarily disabled by commenting them out
of a test list file (such as an inventory file) then it's all too easy to forget to uncomment them
when an underlying defect is fixed. Exclude files make the disabled tests more visible, so a forgotten
test stands a better chance of being noticed and re-enabled.

##### *Test weightings*

Test entries in inventory files have an optional 'weighting' attribute. This
can be used to increase of decrease the odds of running the test when random
test selection is being used. The weighting value is ignored when running with sequential
test selection.

For example:
```
<inventory>
	<junit class="net.adoptopenjdk.stf.MiniJUnitTest" weighting="1"/>
	<junit class="net.adoptopenjdk.stf.FailingJUnitTest" weighting="0.5"/>
	<junit class="net.adoptopenjdk.stf.ArbitraryJavaTest" weighting="1.25"/>
</inventory>
```

In order to allow fast test selection based on random numbers load test builds
an array of all possible tests. This essentially allows test selection based on
code like 'nextTest = testList[rnd.nextInt(testList.length)]'.
If no tests have a weighting specified, and are all on a default value of 1, then
each test has a single entry in the selection array.

Weightings allow fractional values, so load test finds the best multiplier which will
allow fast selection of tests whilst still preserving the desired selection
probabilities. For example, with the following tests:
```
   test A, weighting = 1
   test B, weighting = 0.5
   test C, weighting = 1.25
```
A multiplier of 4 will allow the specified probabilities to be achieved: AAAABBCCCCC.
So randomly picking for this array 11 times would, on average, result in
4A's, 2B's and 5C's. This exactly matches the specified distribution.

It's not always possible to pick a multiplier that will perfectly reproduce the
desired weightings, so in such cases load test will chooses the closest multiplier.
For example, if the test list were:
```
   test X, weighting = 0.41
   test Y, weighting = 0.39
   test Z, weighting = 0.201
```
If the maximum allowed multiplier is 10 then the best choice would be 5, which produces
a selection array of XXYYZ. In practice the errors between the desired and actual distribution
are small, and certainly drowned out by the differences that random test selection causes.

Weightings of less than 1 make it convenient to reduce the probability of running a particularly
slow test by only requiring an edit of that tests entry.


#### Test adaptors

Load test is able to call different types of tests by delegating their execution to
adaptor classes, which use reflection to execute that type of test.

There are currently 3 types of test adaptors:
 - JUnit - to run any standard JUnit tests.
 - mauve - invokes 'mauve' tests from the mauve open source project: https://www.sourceware.org/mauve.
 - arbitraryJava - calls specific java methods.

The key responsibilities of an adaptor are:
 - Runs the test.
 - Decides if the test passed or failed.
 - Invoke first failure diagnostics capture as soon as an failure is spotted.

stf.load test aims to allow the easy reuse of existing tests by not requiring any code changes to the tests themselves.

##### *JUnit tests*

JUnit tests can be added to a load test inventory with lines such as:
```
    <junit class="net.adoptopenjdk.test.binaryData.TestByteArray2IntegerNumBytes"/>
```

All JUnit tests in the class are executed and a custom run listener tracks the progress of the JUnit test methods.

##### *Mauve tests*

The Mauve tests are open source java class library tests. See
https://en.wikipedia.org/wiki/Mauve_%28test_suite%29 and https://www.sourceware.org/mauve for more details.

The Mauve adapter uses reflection to run the 'public void test(TestHarness harness)' of the named mauve class.

The output from running a mauve test is scanned to decide if the test passed or failed.
The Mauve code which verifies test conditions prefixes some output text with a 'PASS:' or 'FAIL:' string.
However, many Mauve tests don't give any positive or negative confirmation so, although assumed
to pass, are given an 'unknown' result status.

Of course, executing the mauve tests requires that they are available. See the https://github.com/AdoptOpenJDK/openjdk-systemtest project for details
of how to enable this feature. Once the test cases are built a single test can be executed outside of the test.load
harness as follows:
```

export PREREQS=$HOME/systemtest_prereqs

java -classpath $PREREQS/mauve/mauve.jar gnu.testlet.SingleTestHarness gnu.testlet.java.util.zip.ZipFile.DirEntryTest
```

##### *Arbitrary Java tests*

The arbitrary Java adapter uses reflection to call a named java method.

If the test completes it is given a result value of 'unknown'. It can only fail if the
test method throws an exception.

Some example inventory lines:
```
    <arbitraryJava class="net.adoptopenjdk.test.simple.ConvertDecimal" method="invokeTest" />
    <arbitraryJava class="net.adoptopenjdk.test.invoke.AsTypeTest" method="testVoid" constructorArguments="1" />
    <arbitraryJava class="net.adoptopenjdk.test.gc.heaphog.ObjectTree" method="runTest" methodArguments="120000000" weighting="30"/>
    <arbitraryJava class="net.adoptopenjdk.stf.sample.ArbitraryJavaTest" constructorArguments="ADD" method="runTest" methodArguments="5, 2,3" />
````

If no constructor or method arguments are supplied then it attempts to call the noargs method.
If 'constructorArguments' or 'methodArguments' are supplied then the adapter attempts to call
the most appropriate method. Each individual argument value is initially treated as a string and
will attempt to match against the following types in this order:
 - java.lang.String
 - int
 - long
 - float
 - double
 - boolean

If a constructor or test method with a matching method signature can't be found then
an exception is thrown, which leads to the failure of the test.


#### Execution logs

To help with debugging load test records the start and end of each tests execution.
The information captured in each record is:
 - Mnemonic to describe what's happened.
 - Timestamp in milliseconds.
 - Thread number. Numbered from 0. Not reset for each suite.
 - Test number. Also numbered from 0. Test numbers listed at start of run.
 - Suite number. Numbered from 0.
 - Stdout/stderr output if the test failed.

You can expect to find 2 for each test execution; 1 to record the start of the test
execution and 1 to record the outcome.

The mnemonics used for each record are:
 - S - Started test.
 - P - Passed test.
 - F - Test Failed.
 - U - Test completed with Unknown result. The test has executed with no sign of a
 pass, fail or exception. Regarded as a pass on the grounds that there is no
 sign of failure.
 - T - Test failed as it failed with a Throwable exception.
 - Z - Test attempted to call System.exit with a 0 exit value. The exit has been
 blocked and the test is treated as a pass.
 - E - Test attempted to call System.exit with a non-zero value. The exit was
 blocked and the test is recorded as having failed.

The amalgamated stdout/stderr for a failing test is recorded in the tests failing
record. This is captured on a per test basis, so even if 2 tests were concurrently
failing they will each have their own output with no intermingling.


#### Formatting execution log ltm and ltd files

Data about the test execution is stored in a binary format log, to
allow the storage of as much history as possible in the available disk space.
It uses 10 bytes per entry, which allows the recording of about 52000 test invocations per MB.

The data is recorded in:
 - A single '.ltm' (load test metadata. file version number, time zone, test lists, etc) file.
 - 1 or more '.ltd' (load test data) files.

The execution log files are created at ${resultsDir}/${resultsPrefix}executionLog.ltd.

The easiest way to run the program is probably to run a perl script which lives in the stf
scripts directory: 'formatExecutionLog.pl'. This can be run directly if you put the '${build-output}/stf/scripts'
directory on your path or on linux systems create an alias for the script.
A typical command would be something like '$HOME/git/stf/scripts/formatExecutionLog.pl --detail /stf/MathLoadTest/results/1.LT.executionlog'

##### Detail execution log formatter

To dump the execution log, and get detailed information about each test start/end, run formatExecution with a '--detail' or '-d' argument.

Running with '--verbose' or '-v' will make the formatter dump the captured output from any failing tests.

The following sample output shows the first few records from a passing run:
```
$ $HOME/git/stf/scripts/formatExecutionLog.pl --detail /stf/SampleLoadTest/results/1.LT.executionlog
Formatting execution log for: /stf/SampleLoadTest/results/1.LT.executionlog

Ownership of 6 worker threads:
  Suite 0 owns threads: 0 to 4
  Suite 1 owns thread: 5

                                                                                                              <-- suite 0 --><1>
 Timestamp       Delta Thr Event      Test   Test name                                                         0  1  2  3  4  5
14:47:19.131     +15ms  0 Started       0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -  o                
14:47:19.131     +15ms  4 Started       0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -  |           o    
14:47:19.131     +15ms  2 Started       1 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -  |     o     |    
14:47:19.131     +15ms  3 Started       1 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -  |     |  o  |    
14:47:19.131     +15ms  0 Completed     0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -  V     |  |  |    
14:47:19.131     +15ms  1 Started       0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -     o  |  |  |    
14:47:19.131     +15ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -     |  |  |  |  o
14:47:19.131     +15ms  4 Completed     0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -     |  |  |  V  |
14:47:19.133     +17ms  1 Completed     0 net.adoptopenjdk.stf.ArbitraryJavaTest:runSimpleTest()            -     V  |  |     |
14:47:19.136     +20ms  2 Completed     1 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -        V  |     |
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     o
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     o
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     o
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     o
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           |     o
14:47:19.136     +20ms  3 Completed     1 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -           V     |
14:47:19.136     +20ms  5 Completed    10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -                 V
14:47:19.136     +20ms  5 Started      10 net.adoptopenjdk.stf.ArbitraryJavaTest:runTest()                  -                 o
...
```

##### Failure execution log formatter

The failure formatter scans the execution log to find any failing tests. When run with '--failures'
or '-f' it will output a 1 line summary for each failing test.

If run with '--verbose' or '-v' it will also output the captured text for each failing test.

```
$ $HOME/git/stf/scripts/formatExecutionLog.pl --failures /stf/SampleFailingLoadTest/results/1.LT.executionlog
Formatting execution log for: /stf/SampleFailingLoadTest/
results/1.LT.executionlog

Test failures:
  Failure 1) Test number=0 Test=net.adoptopenjdk.stf.FailingJUnitTest
  Failure 2) Test number=0 Test=net.adoptopenjdk.stf.FailingJUnitTest
  Failure 3) Test number=0 Test=net.adoptopenjdk.stf.FailingJUnitTest
  Failure 4) Test number=0 Test=net.adoptopenjdk.stf.FailingJUnitTest
```

The verbose output would look like:
```
$ $HOME/git/stf/scripts/formatExecutionLog.pl --failures --verbose /stf/SampleFailingLoadTest/results/1.LT.executionlog
Test failures:
  Failure 1) Test number=0 Test=net.adoptopenjdk.stf.FailingJUnitTest
testStarted : testPi(net.adoptopenjdk.stf.FailingJUnitTest)
testFailure: testPi(net.adoptopenjdk.stf.FailingJUnitTest): Wrong value of pi ex
pected:<3.0> but was:<3.141592653589793>
junit.framework.AssertionFailedError: Wrong value of pi expected:<3.0> but was:<
3.141592653589793>
        at junit.framework.Assert.fail(Assert.java:57)
        at junit.framework.Assert.failNotEquals(Assert.java:329)
        at junit.framework.Assert.assertEquals(Assert.java:78)
        at junit.framework.TestCase.assertEquals(TestCase.java:244)
        at net.adoptopenjdk.stf.FailingJUnitTest.testPi(FailingJUnitTest.java:37
)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
...
```

##### Summary execution log formatter

The summary formatter gives a high level view of a test run. To produce
this view run with '--summary' or '-s'

The first part of the output shows the coverage of each log file. See below for
information about how the data is recorded across multiple files which are pruned
as needed to prevent running out of disk space.

For example:
```
$ $HOME/git/stf/scripts/formatExecutionLog.pl --summary /stf/SampleLoadTest/results/1.LT.executionlog
Log file summaries
  Part 1  Covers 10:00:41.934 to 10:00:41.956  Started:57 Passed:56 Failed:0
  Part 2  Covers 10:00:41.956 to 10:00:41.990  Started:57 Passed:56 Failed:0
  Part 3  Covers 10:00:41.991 to 10:00:42.011  Started:58 Passed:55 Failed:0
  Part 4  Missing
  Part 5  Missing
  Part 6  Covers 10:00:42.049 to 10:00:42.059  Started:36 Passed:37 Failed:0

Log file counts
  Number log files found  : 4
  Number log files missing: 2

Overall test result counts. Note: Partial results due to missing log file(s)
  Started: 208
  Passed : 204
  Failed : 0
 ```

##### MetaData execution log formatter

The information from the .ltm metadata file can be dumped by running with '--metadata' or '-m'.

This lists data such as
- timezone that the test was run in.
- number of suites and threads.
- list of tests, with their allocated test number.  


#### Execution log disk space and pruning

To prevent the execution logs from consuming an excessive amount of disk space,
and to allow load tests to run indefinitely, there are 2 arguments to control the
execution logging and its log rotation.

Firstly, there is the 'LoadTestProcessDefinition.setMaxTotalLogFileSpace()' which
tells the load test application the maximum amount of disk space it can use for
execution logs. This is a string in the same form as Java's -Xmx, ie, '{number}(g|G|m|M|k|K)'.

Secondly, there is a 'LoadTestProcessDefinition.setMaxSingleLogSize()' method
which sets the maximum size for any single log file. As with the maxTotalLogFileSpace
this can be specified with an absolute size, eg, '50M', or it can be
set to a fractional value, eg '1/10'. If set to a fractional value load test
will calculate the actual limit based on the corresponding fraction of the total
log file space. For example, you could run with a maxTotalLogFileSpace of 600M and a
maxSingleLogSize of '1/50' which result in the maximum size of a single log file
of 12M.

As the log file space used reaches the limit old log files are deleted. The log
containing the lowest priority data is deleted. If disk space permits then load
test does its best to retain the log file containing the first failure and all
log files before the first failure.
The full order of priority, from highest
to lowest, is:
1. Log with first failure.
1. Complete set of logs leading to initial failure.
1. Final log.
1. Any other logs with failures.
1. Log immediately before a failing log.
1. Other log files.

#### Java dumps

If load test is running on an an IBM JVM then it takes a set of 'first-failure' dumps
when the first test fails using the 'com.ibm.jvm.Dump' class.

It attempts to take the dump as soon as the failure is spotted, but be aware that tests
in other threads may complete before the failure is spotted and the dump requested. For
a more accurate view of what other tests were running at the time of the failure you'll
need to dump the execution logs.

If the load test is being run by STF and it has exceeded it's maximum allowed runtime
then STF process monitoring will attempt to capture as many diagnostic Java files as
the possible. What can actually be captured will vary depending on the platform, but on
linux it will be 3 sets of javacore dumps and then a set of core, snap and jitdump files.
Any process which exceeds it's maximum allowed runtime is treated as a test failure.

#### Progress reporting

Load test outputs periodic status reports. As well as showing progress these also
prevent any outer test harness which treats output not being written to stdout as a
hung test from doing so erroneously. Here is some typical output:
```
...
11:51:26.194 - Completed 1.8%. Number of tests started=486561
11:51:46.217 - Completed 3.6%. Number of tests started=916629 (+430068)
11:52:06.139 - Completed 5.4%. Number of tests started=1346891 (+430262)
11:52:26.160 - Completed 7.2%. Number of tests started=1776672 (+429781)
11:52:46.174 - Completed 9.0%. Number of tests started=2203310 (+426638)
11:53:06.190 - Completed 10.8%. Number of tests started=2631735 (+428425)
11:53:26.208 - Completed 12.6%. Number of tests started=3060563 (+428828)
11:53:46.221 - Completed 14.4%. Number of tests started=3488503 (+427940)
11:54:06.136 - Completed 16.2%. Number of tests started=3918253 (+429750)
...
```

The progress reports are numbered so that it's easy to confirm that none have been lost.

The default progress reporting interval is 20 seconds, but it can optionally
be overridden by a milliseconds value of the environment variable 'LT_REPORTING_FREQUENCY'.
For example, to get updates every quarter of a second:
```
export LT_REPORTING_FREQUENCY=250
```

#### Hang detection

Load test outputs the string '**POSSIBLE HANG DETECTED**' if no tests have completed
within the previous 15 minutes. Before the process is terminated the diagnostics
capture sequence described in 'java dumps' is triggered. If the test is being run on an
IBM JVM this should result in 3 sets of java dumps, a core file, snap and jitdump files being
created.

#### Algorithm summary

The core of the load test program is driven by the following algorithm:
```
check arguments
read inventory files

for all suites {
    for suite-thread-count {
        run worker thread {
            // logic for each worker thread
            while (still need to run tests) {
                pick next test;

                record test-start to execution log;
                flush execution log;
                result = execute test;
                record test result to execution log;
                flush execution log;

                if (result == failed && is-first-failure) {
                    if (!first failure dumps created) {
	                    generate java dumps;
	                }
                }
                increment counts;
            }
        }
    }
}

// Main thread waits for all worker threads
while (worker thread still running) {
    output intermittent progress report;
    sleep;
}

output test results;
```

## STF Java Interface

STF provides a builder style interface to the load test program in the LoadTestProcessDefinition.java class.
This allows a test to programmatically specify how to run the load test.

Once the test has created such a definition it then runs it using the standard STF process
management. The current tests typically describe how to run a load and then execute this
as a single foreground process, although if they wanted to they would be free to run the
load test process concurrently with other processes.

The building of the load definition has the following parts:
  1. Any extra JVM arguments.
  1. Build class path entries need to run the tests.
  1. Information about the test suite to be run.
  1. Optionally. Define 2nd and subsequent suites.

#### Java methods

##### *Load test JVM arguments*

Methods which supply options for the JVM running the load test are:
- addJvmOption
- addModules - Add a Java 9 module onto the classpath.
- addProjectToClasspath - Allows access to the contents of a top level project.
- addJarToClasspath
- addDirectoryToClasspath

##### *Arguments for the load test program itself*
- setAbortIfOutOfMemory - Control what happens on OutOfMemoryException.
- setReportFailureLimit - Specify number of test failures to report in detail.
- setAbortAtFailureLimit - Set how many tests are allowed to fail before aborting a run.
- setMaxTotalLogFileSpace - Set size limit for recording test execution activity.
- setMaxSingleLogSize - Set size limit for individual log file.

##### *To control test runtime*
At least one of these methods must be called:
- setSuiteNumTests - each suite can set how many tests are to be executed.
- setTimeLimit - Overall run time limit. No new tests are started after running for this long. eg '1h30m', '2m30s', etc

##### *Suite control*
For each suite the test needs to specify:
- addSuite - which sets the name for the suite.
- setSuiteThreadCount - the number of threads running tests for the suite.

##### *Test selection*
The decision about the next test to run is controlled by:
- setSuiteInventory - Lists the tests to run.
- setSuiteSequentialSelection
- setSuiteRandomSelection
- setSuiteSeed - for random selection this sets the starting seed, or use the default of '-1' to randomly pick the starting seed.
- setSuiteTestRepeatCount - is the number of times to execute a selected test.
- setSuiteThinkingTime - specifies a minimum and maximum time for each worker thread to sleep between tests, eg '250ms..1s'. To disable use the range of '0ms..0ms'. If enabled a random number generator is used to decide how long to sleep.

#### Example Java test code

The following code is taken from the example in SampleLoadTest.java and shows an example load test invocation:

```java
public void execute(StfCoreExtension stfCore) throws Exception {
  String inventoryFile1 = "/stf.samples/config/inventories/sampleLoadTest/sampleInventory.xml";
  String inventoryFile2 = "/stf.samples/config/inventories/sampleLoadTest/subtests/arbitraryJavaInventory.xml";
  int numTests = InventoryData.getNumberOfTests(stfCore, inventoryFile1);

  LoadTestProcessDefinition loadTestSpecification = stfCore.createLoadTestSpecification()
      .addProjectToClasspath("stf.samples")
      .addJarToClasspath(JavaProcessDefinition.JarId.JUNIT)
      .addJarToClasspath(JavaProcessDefinition.JarId.HAMCREST)
      .setTimeLimit("12s")				// Don't start any tests after 12 seconds
      .setMaxTotalLogFileSpace("500M")    // Optional. Prevent logging from exceeding 500M of log files
      .setMaxSingleLogSize("1/50")        // Optional. Run with limit of 50 logs, each up to 10M.
      .addSuite("suite1")					// Arguments for the first suite follow
      .setSuiteThreadCount(Runtime.getRuntime().availableProcessors()-3, 2,16)  // Leave 1 cpu for the JIT, 1 for GC, 1 for the other suite. But always run at least two threads and never more than 16
      .setSuiteInventory(inventoryFile1)	//   Point at the file which lists the tests. There are no exclusion files.
      .setSuiteNumTests(numTests * 10)    //   Number of tests to run varies with size of inventory file
      .setSuiteTestRepeatCount(3)		    //   Run each test 3 times
      .setSuiteThinkingTime("5ms", "75ms")//   Waiting time between tests is randomly selected between 5ms and 75ms. Can also use 's' for seconds.
      .setSuiteSequentialSelection()	    //   Not random selection. Sequential from start. eg, 0,1,2,3,4,5,0,1,...
      .addSuite("suite2")					// Add 2nd (optional) suite
      .setSuiteThreadCount(1)			    //   Run in a single thread
      .setSuiteInventory(inventoryFile2)	//   Use the sample inventory file, which has 2 exclusion files.
      .setSuiteNumTests(1000)			    //   Run 1000 tests in total
      .setSuiteTestRepeatCount(25)	    //   Run which ever test is picked 25 times before picking next test
      .setSuiteRandomSelection();		    //   Randomly choose test to run. Note this suite doesn't have a 'thinking' time.

  // Run load test and wait for it to finish
  // Stdout and stderr output will be echoed and prefixed with the 'LT' mnemonic.
  // The load test will be killed if not completed within the 5 minute time limit.
  stfCore.doRunForegroundProcess("Run load test for project", "LT", ECHO_ON,
                                  ExpectedOutcome.cleanRun().within("5m"),
                                  loadTestSpecification);
}
```


## The Load Test Program Itself

#### Load test arguments

The load test application is normally run from within an STF based test, but it
is a Java program so it can also be run directly.

It's also quite useful during troubleshooting to modify the load test arguments in the
execute.pl for a failing test. The JVM or load test arguments can be editing in a text
editor whilst repeatedly getting perl to run the execute.pl script in another
command line window.

| Argument                                                         | Comment |
|------------------------------------------------------------------|---------|
| -resultsDir {directory}                                          | Mandatory. Is an existing directory to which the results and execution log will be written |
| -resultsPrefix {name}                                            | Optional string which is used as a prefix to all files written to the results directory |
| -timeLimit {time-value}                                          | Optional. No tests will be started after running for this long |
| -abortIfOutOfMemory {boolean}                                    | Optional. Default to 'true' to exist test on out of memory exception. Set to 'false' to keep going |
| -reportFailureLimit {number}                                     | Optional. This is the number of test failures which will be reported in detail (with name of failing test, stack trace, etc). Set '-1' to disable for reporting of all failing tests |
| -abortAtFailureLimit {number}                                    | Optional. Load test will abort when this many tests have failed. Set to '-1' to disable so that a run never aborts after a failure |
| -maxTotalLogFileSpace {number}{unit}                             | Mandatory. Limits the disk space used for execution logging |
| -maxSingleLogSize {number}{unit} <code>&#124;</code> 1/{number}  | Mandatory. Maximum size for an individual log file. Or calculated as a fraction of maxTotalLogFileSpace (eg '1/20') |
| -suite.{name}.threadCount {number}                               | Mandatory. Is the number of threads to be used for running the tests of this suite |
| -suite.{name}.inventoryFile {file}                               | Mandatory. Specifies the file which lists the test that can be run for the suite |
| -suite.{name}.inventoryExcludeFile {file}                        | Mandatory. If some tests are known to fail and therefore shouldn't be executed then they can be listed in an exclude file. Use the value 'none' if there are no such tests |
| -suite.{name}.totalNumberTests {number}                          | Optional. This is the number of tests which will run before completing this suite. If not specified the load test will run until the time limit |
| -suite.{name}.selection 'sequential' <code>&#124;</code> 'random'| Mandatory. Controls how the next test will be selected. When running sequentially it loops back to the start of the inventory after executing the last test in the list |
| -suite.{name}.seed {number}                                      | Mandatory. Sets the starting value for the random number generator used to select the next text. A value of '-1' will use a new seed. Use a pervious value to duplicate a run |
| -suite.{name}.repeatCount {number}                               | Optional. This is the number of times that a test will be executed before a different test is selected. Defaults to '1' |
| -suite.{name}.thinkingTime {number}ms..{number}ms                | Optional. Controls random delay in each load test thread before executing successive tests. Defaults to a disabled value of '0ms..0ms' |

Where:
  * {directory} points to an existing directory
  * {file} points to an existing file
  * {name} as an ascii string, eg "1.LT."
  * {time-value} is a series of value and unit pairs. Supports hours, minutes and seconds. eg, "1h15m" or "10s"
  * {number} is a whole number, eg "250"
  * {unit} is a single character sizing in the form [g|G|m|M|k|K].

#### Comments about load test arguments

The load test requires at least one 'suite' to execute. Each batch of suite
arguments are grouped by a common suite name.

A load test run must be bounded to some extent, so if each suite doesn't
specify a 'totalNumberTests' value then you must use a '-time-limit' value.
If both 'totalNumberTests' and '-time-limit' bounds are set then the load test will
run until the either condition becomes true.

## Adding additional load tests

The general pattern for turning a bunch of unit type tests into a load test is:
1. Get hold of test source. Third party tests can be added to the prereqs directory. Original test cases and build scripts are added to the appropriate git repo.
1. Add a new load test class. It's usually easiest to do a copy+paste+modify of
SampleLoadTest or one of the other load-test based tests.
1. Create inventory files, listing all of the new tests.
1. Run and test locally.
1. Add new test targets to the project/project.build/makefile.

The 'run and test locally' step can be quite involved and is probably best broken down into stages.
If you are lucky you don't have to go through all stages but the full list from least to
most significant is:
* Run all of the tests in a single thread with a repeat count of 1 1. Filter out the broken tests.
* Run test single threaded with a repeat count (of say 500, 1000 or 2000) that is significant
enough to get the dynamic compiler (Oracle Hotspot or IBM JIT) involved. Investigate failures. Filter out broken tests.
* Run single threaded with random ordering. Do in chunks if there is a really large number of tests.
Sometimes tests will fail because of the actions of an earlier test break a later one. Probably need
to investigate and decide which test to remove.
* Run with 2 threads in random order. Investigate failures. Usually concludes with the removal
of tests which are inherently not thread safe, or do something which impacts other
tests (changing local, forcing out of memory errors, etc).
* Run with 2 threads for significant time period. Until running cleanly.
* Run with more threads. Again for significant time period until clean.

Remember that if the tests require vendor specific features they should be added to the git repo for that vendor.

Some of the reasons why tests are not runnable in a load test are:
* Test is broken. It won't even pass running it just once in a single threaded test.
* Badly behaved. Eg, sets machine time or locale, which impacts on other tests.
* Deliberately forces an out of memory error (breaks other tests).
* Test never completes.
* Too fragile. Depends on fixed duration sleeps.
* Test is not threadsafe. eg, uses a singleton or static data. Can only be run in a single threaded suite.
* Does AWT GUI operations. Incompatible with other GUI tests. Run single threaded.


## Reproducing failures

This section lists some techniques which have been used to substantially reduce the reproduction time, or
improve the reproduction rate, and in some cases extract the single test which exposes a JVM defect.

Firstly, when faced with an inventory of several thousand tests then several cycles of binary chopping
the inventory can reduce reproduction time as the JVM doesn't have to spend time executing irrelevant tests.

Try switching from random test selection to sequential with a repetition of 500 to 1000. If this reproduces
then dumping the execution log usually makes it easy to identify the triggering test.

If the previous point fails to reproduce then the failure may need the concurrent execution of
another test. In this circumstance set up another suite running the same tests, with either random selection
or sequential selection and a different repeat number. If this reproduces then you'll usually see a
long list of passing tests from the first suite which then consistently fails as soon as the accomplice test runs.

Try increasing or decreasing the thread count. Paradoxically reproduction rates are typically far better
with fewer threads. Can the defect still be exposed when running with a single worker thread?

Running for longer doesn't always increase the likelihood of detecting a failure. Some JIT related defects
either happen within the first, say, 5 minutes or not all. This is a circumstance in which setting
a '-timeLimit' argument and STF repeat count can help.

Experiment with Hotspot / JIT options, e.g. '-Xjit:optLevel=hot' forces the IBM JIT compiler to optimise the java classes to the 'hot' level which may make the occurrence of a defect more reliable.

If the load test has been run by STF, and you're going to be going round the modify/run cycle many times, then
it's sometimes easiest to edit the previously run 'execute.pl' and manually rerun it.


## Future Enhancements

Some potential future enhancement include:
* Improved analysis at the test execution log. For example:
  * Human readable test names (instead of the current test numbers).
  * Produce a list of tests that overlap the execution of a failing test.
  * Filter activity for specific threads.
  * Filter activity for named tests. Calculate failure rate.
* Add TestNG test adaptor.
* Allow load test arguments to be overridden on the STF command line. These would take
precedence over values specified in the test case. eg, to reproduce the thread count and
random seed for a failing test: ``stf -test=SampleLoadTest -override="-suite.suite1.threadCount=4,-suite.suite1.seed=622344555"``
* Automatic test isolation. The process of binary chopping lists of tests to get a minimal set with faster reproduction times is quite a mechanical process, so it ought to be possible to automate this.
