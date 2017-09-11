# stf

This repository contains the System Test Framework (STF) used by test cases in the javasvt repository to execute multi process, multi step Java tests.

[Quick start (Unix)](#unix)  
[Quick start (Windows)](#windows)  
[More documentation](stf.build/docs/build.md)

<a name="unix"></a>
## Quick start (Unix)

This quick start is for people who want to clone and build the project.  To set up a development environment for enhancing STF or creating new test cases, refer to [this document](stf.build/docs/build.md).

Before running the build for the first time make sure GNU make, ant and wget are on your PATH.

wget is only required for the make configure step, which only needs to be done once.

Either copy, paste and execute [this script](stf.build/scripts/stf_clone_make.sh) which runs the command below, or run the commands yourself.


```shell
# 1. Create a directory for the git clone
mkdir -p $HOME/git

# 2. Clone the git repository
cd $HOME/git
git clone https://github.com/AdoptOpenJDK/stf.git stf

# 3. Set JAVA_HOME to a Java 8 or later Java

export JAVA_HOME=<java-home>

# 4.Install the prereqs
# This requires wget to be on the PATH
cd $HOME/git/stf/stf.build
make configure

# 5. Build
cd $HOME/git/stf/stf.build
make

# 6. Run the STF samples
cd $HOME/git/stf/stf.build
make test
echo See /tmp/stf to view the test results
```

<a name="windows"></a>
## Quick Start (Windows)

This quick start is for people who want to clone and build the project.  To set up a development environment for enhancing STF or creating new test cases, refer to [this document](stf.build/docs/build.md).

Before running the build for the first time make sure GNU make, ant and wget are on your PATH.

wget is only required for the make configure step, which only needs to be done once.

Either copy, paste and execute [this script](stf.build/scripts/stf_clone_make.bat) which runs the command below, or run the commands yourself.

```dos
REM 1. Create a directory for the git clones
mkdir c:\%USERPROFILE%\git

REM 2. Clone the test cases
cd c:\%USERPROFILE%\git
git clone https://github.com/AdoptOpenJDK/stf.git stf

REM 3. Set JAVA_HOME to a Java 8 or later Java
SET JAVA_HOME=<java-home>

REM 4. Get the test case prereqs
cd C:\%USERPROFILE%\git\stf\stf.build
make configure

REM 5. Build
cd C:\%USERPROFILE%\git\stf\stf.build
make

REM 6. Run the samples
cd C:\%USERPROFILE%\git\stf\stf.build
make test
echo See c:\stf_temp to view the test results
```
