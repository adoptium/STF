# System Test Framework (STF) Build

Basic process is:
1. Install prerequisite software
2. clone the repository
3. `make configure` (installs some other prereqs)
4. `make` (or make build - builds the repository)
5. `make test` (runs the STF sample tests)

## Prerequisite software which cannot be installed by the STF build scripts
These prereqs must be installed before attempting to build STF
1. [Apache Ant](http://ant.apache.org) version 1.8.4 or later
2. Java 8 or later (any implementation)
3. [GNU make](https://www.gnu.org/software/make/) version 3.79 or later
4. [GNU Wget](https://www.gnu.org/software/wget/) (optional - only required if you want to install the other prereqs automatically)

## Prerequisite software which can be installed by the STF build scripts
### Installing the prereqs using the build scripts
1. An internet connection is required
2. Review the list of prereqs listed under 'Installing prereqs manually' and confirm that you accept their license terms.
3. `git clone https://github.com/adoptium/STF.git stf`
4. Change into the stf.build directory `cd <git-root>;stf.build`
5. `make configure`

### Installing prereqs manually
1. Create a systemtest_prereqs directory alongside the git repository directory - e.g. /home/user/systemtest_prereqs (alongside /home/user/git)
2. Download and install the prereqs as described in the table below.

| Dependency            | License                                                       | Used by    | Steps to obtain                                                                                                                                                                                                                                            |Install instructions                                                                                                                                                                                                                                                   |Installed via make / ant configure? |
|-----------------------|---------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| apache-ant-1.10.1     | https://ant.apache.org/license.html                           | stf.build  | Download from https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.1-bin.zip                                                                                                                                                                            | Unzip to PREREQS_ROOT/apache-ant-1.10.1                                                                                                                                                                                                                                 | Yes                                |
| log4j-2.13.3             | https://logging.apache.org/log4j/2.0/license.html             | stf.*      | Download from https://archive.apache.org/dist/logging/log4j/2.3/apache-log4j-2.13.3-bin.zip                                                                                                                                                                        | Copy to PREREQS_ROOT/log4j-2.13.3/log4j-api-2.13.3.jar and PREREQS_ROOT/log4j-2.13.3/log4j-core-2.13.3.jar                                                                                                                                                                                                                      | Yes                                |
| GNU make 3.79 or later| https://www.gnu.org/licenses/gpl.html                         | stf.build  | Windows - Download from http://gnuwin32.sourceforge.net/packages/make.htm<br>Unix: may already be installed on the test machine, a prebuilt version may already be available, otherwise build from source - see https://www.gnu.org/software/software.html	         | Add GNU make to PATH (ahead of any native platform make) before executing make or make test, or copy make to PREREQS_ROOT/gmake/<platform> where platform is linux_x86-32, linux_x86-64, linux_ppc-32, linux_390-31, linux_arm-32, win_x86-32, aix_ppc-64, zos_390-64                                                                    | No                                 |
| perl 5.6.1 or later   | http://perldoc.perl.org/index-licence.html                    | stf.core   | Windows - tests can be executed using Strawberry perl.  Other perl implementations may be OK too.                                       | Add to PATH                                                                                                                                                                                                                                                           | No                                 |
| Windows Sysinternals  | https://technet.microsoft.com/en-us/sysinternals/bb469936.aspx| stf.core   | Download from https://download.sysinternals.com/files/SysinternalsSuite.zip                                                                                                                                                                                         | Unzip to PREREQS_ROOT/windows_sysinternals                                                                                                                                                                                                                              | Yes                                |
| wget                  | https://www.gnu.org/copyleft/gpl.html                         | stf.build  | Windows - download from https://sourceforge.net/projects/gnuwin32/files/wget/1.11.4-1/wget-1.11.4-1-bin.zip                                                                                                                                                                   | Add to PATH                                                                                                                                                                                                                                                           | No                                 |

## Building from a command line
1. `git clone https://github.com/adoptium/STF.git stf`
2. Change into the stf.build directory `cd <git-root>;stf.build`
3. `make`

## Working in Eclipse - developing STF and STF test cases
STF and STF test case development must be done in an Eclipse environment. STF uses the Eclipse metadata
in the project .classpath files to work out the test dependencies, these are then translated into -classpath command
line arguments when the tests execute outside of Eclipse.
1. Create a new Eclipse workspace (once configured the workspace will reference multiple git repositories and a local directory containing the test prereqs).
2. Install the prerequisites
- Follow the instructions above to install the prereqs which can't be installed by STF.
- Follow the instructions above to install the remaining prereqs manually or via the build scripts.
- Create a General Project in Eclipse called systemtest_prereqs
- File --> Import --> File System
- Select the directory containing the prereqs. Select import into the new systemtest_prereqs folder.  Do not tick
the 'Create top level folder' check box (otherwise you get an extra 'systemtest_prereqs' folder which you do not
want).
3. `git clone https://github.com/adoptium/STF.git stf`
4. Import the STF projects into Eclipse (Find and import Eclipse projects)
5. Eclipse should now build the projects without errors (check the 'Problems' view).
