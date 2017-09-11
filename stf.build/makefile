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

EMPTY=

# Environment variable OSTYPE is set to cygwin if running under cygwin.
# When the shell is switched to cmd.exe OSTYPE is no longer set, so set a make
# macro if running under cygwin.
ifndef CYGWIN
  OSTYPE?=$(shell echo $$OSTYPE)
  ifeq ($(OSTYPE),cygwin)
    CYGWIN:=1
  else
    CYGWIN:=0
  endif
endif
#$(warning CYGWIN is $(CYGWIN))

# Use a macro to say whether we are running under Windows.
# OS may not be set to Windows_NT under cygwin
ifeq ($(OS),Windows_NT)
  ifneq ($(CYGWIN),1)
    WINDOWS:=1
  endif
endif

# Work out the resolved root directory name of the repository from the current makefile.

# Assumes directory structure is
#
# git repository is checked out to <testing_root>$(D)git$(D)stf
# So
# <testing_root>$(D)git$(D)stf.core$(D)test_project1
# <testing_root>$(D)git$(D)stf.core$(D)test_project2
#
# prereqs are installed to <testing_root>$(D)systemtest_prereqs
# So
# <testing_root>$(D)systemtest_prereqs$(D)prereq1
# <testing_root>$(D)systemtest_prereqs$(D)prereq2
#
# To change override SOURCE_ROOT and $(D) or PREREQS_ROOT on the make command line
# make SOURCE_ROOT=<my_SOURCE_ROOT> PREREQS_ROOT=<my_prereqs_root>

# The current makefile is the last makefile in MAKEFILE_LIST

#$(warning MAKEFILE_LIST is $(MAKEFILE_LIST))
ifeq ($(MAKEFILE_LIST),)
  $(warning The MAKEFILE_LIST variable is not supported, this inbuilt variable is available on gnu make 3.80+)
  $(warning Expecting the SOURCE_ROOT variable set instead) 
  ifeq ($(SOURCE_ROOT),)
    $(error The SOURCE_ROOT variable is not set)
  endif
else
  CURRENT_MAKEFILE:=$(word $(words $(MAKEFILE_LIST)),$(MAKEFILE_LIST))
  $(warning CURRENT_MAKEFILE is $(CURRENT_MAKEFILE))
  #MAKEFILE_ROOT_DIR:=$(dir $(CURRENT_MAKEFILE))$(D)..$(D)..
  MAKEFILE_ROOT_DIR:=$(dir $(CURRENT_MAKEFILE))$(D)..
  $(warning MAKEFILE_ROOT_DIR is $(MAKEFILE_ROOT_DIR))

  ifneq ($(WINDOWS),1)
    SOURCE_ROOT?=$(shell cd "$(MAKEFILE_ROOT_DIR)"; pwd)
  else
    SOURCE_ROOT:=$(subst \,\,$(strip $(subst Directory of,,$(shell dir "$(subst \,$(D),$(MAKEFILE_ROOT_DIR))" | findstr $(D)C:"Directory of"))))
  endif

  # If we are running under cygwin, turn the $(D)cygdrive style path into the real Windows path
  # and then set the shell to cmd.exe.
  # The tests all expect to be running under real Windows.
  ifeq ($(CYGWIN),1)
    WINDOWS:=1
    #$(warning Running under cygwin)
    #$(warning Before cygpath SOURCE_ROOT set to $(SOURCE_ROOT))
    SOURCE_ROOT:=$(shell cygpath -m '$(SOURCE_ROOT)')
  endif
endif

$(warning SOURCE_ROOT set to $(SOURCE_ROOT))

ifeq ($(WINDOWS),1)
  ESC:=^
  ESC_DQ:="""
  D:=\\
  P:=;
  EXE:=.exe
  BAT:=.bat
  TEMP?=C:\\TEMP
  SHELL:=cmd.exe
  CAT:=type
  WHICH=where
  NULL=nul
  EXPORT=set
  dir_exists = $(shell if exist $(1) echo dir_exists)
  file_exists = $(shell if exist $(1) echo file_exists)
else
  ESC:=\$(EMPTY)
  ESC_DQ:=\"
  D:=/
  P:=:
  EXE:=
  BAT:=
  TMP?=$(D)tmp
  TEMP?=$(TMP)
  CAT:=cat
  WHICH=which
  NULL=$(D)dev$(D)null
  EXPORT=export
  dir_exists = $(shell if [ -d  $(1) ] ; then echo dir_exists; fi;)
  file_exists = $(shell if [ -f  $(1) ] ; then echo file_exists; fi;)
endif

ifeq (,$(JAVA_HOME))
  $(error ERROR: JAVA_HOME not set)
endif

PREREQS_ROOT=$(SOURCE_ROOT)$(D)..$(D)..$(D)systemtest_prereqs
TEST_PREREQS_ROOT:=$(PREREQS_ROOT)
$(warning TEST_PREREQS_ROOT is $(TEST_PREREQS_ROOT))
ifeq ($(CYGWIN),1)
  $(warning Running cygpath -m '$(TEST_PREREQS_ROOT)')
  TEST_PREREQS_ROOT:=$(shell cygpath -m '$(TEST_PREREQS_ROOT)')
else
  ifneq ($(WINDOWS),1)
    $(warning Running cd "$(TEST_PREREQS_ROOT)"; pwd)
    TEST_PREREQS_ROOT:=$(shell cd "$(TEST_PREREQS_ROOT)"; pwd)
  else
    # On Windows 'dir' won't work with double back slashes
    TEST_PREREQS_ROOT:=$(subst $(D),\,$(TEST_PREREQS_ROOT))
    $(warning Running dir "$(TEST_PREREQS_ROOT)")
    TEST_PREREQS_ROOT:=$(subst \,$(D),$(strip $(subst Directory of,,$(shell dir "$(subst $(D),\,$(TEST_PREREQS_ROOT))" | findstr $(D)C:"Directory of"))))
    $(warning TEST_PREREQS_ROOT is $(TEST_PREREQS_ROOT))
  endif
endif
ifeq (dir_exists,$(call dir_exists,$(PREREQS_ROOT)))
  PREREQS_ROOT:=$(TEST_PREREQS_ROOT)
else
  ifneq (configure,$(MAKECMDGOALS))
    $(error systemtest_prereqs directory $(PREREQS_ROOT) does not exist.  Either setup the directory $(PREREQS_ROOT) or use PREREQS_ROOT=xxxx on the make command line to point to a different location.   Running make configure will get most of the prereqs.  See docs/build.md for more details.)
  endif
endif

# Create unix style path name - fixes are required to escape backslashes in all places they may be found.
PREREQS_ROOT_UNIX = $(subst \,$(D),$(PREREQS_ROOT))

# To build to a different location specify TARGET_ROOT=<a directory> on the make command line
TARGET_ROOT:=$(SOURCE_ROOT)

ANT_JAVA_HOME:=$(JAVA_HOME)
ANT_LAUNCHER=$(ANT_HOME)$(D)lib$(D)ant-launcher.jar

ifeq (,$(ANT_JAVA_HOME))
  $(warning ANT_JAVA_HOME not set, will use $(JAVA_HOME) to run ant)
endif

ifndef ANT_HOME
  # Is there an ant in $(PREREQS_ROOT)$(D)apache-ant-1.10.1?
  # If not, try to find ant on the path and use that one
  ANT_HOME:=$(PREREQS_ROOT)$(D)apache-ant-1.10.1
  $(warning ANT_HOME not set, looking in $(ANT_HOME))
  ifeq (file_exists,$(call file_exists,$(ANT_LAUNCHER)))
    $(warning Found $(ANT_LAUNCHER))
  else
    $(warning Cannot find $(ANT_LAUNCHER), looking for ant on the PATH)
    # On Windows run where ant.bat
    # On Unix run which ant, and use realpath to resolve symlinks (ant is likely to be symlinked e.g. to usr$(D)bin$(D)ant).
    ifeq ($(WINDOWS),1)
      ANT_BINDIR:=$(dir $(firstword $(shell $(WHICH) ant$(BAT) 2>$(NULL))))
      $(warning $(WHICH) ant$(BAT) 2>$(NULL) returned)
      $(warning $(ANT_BINDIR))
      ifneq (,$(ANT_BINDIR))
        ANT_HOME:=$(ANT_BINDIR)$(D)..
        $(warning ANT_HOME set to $(ANT_HOME))
        $(warning Found $(ANT_BINDIR), will start build with $(ANT_LAUNCHER).  Run make configure to install the required ant version 1.10.1 or follow the prereq install instructions in build$(D)build.md)
      else
        $(error Unable to locate ant to start the build. Either add ant to PATH, set ANT_HOME or follow the prereq install instructions in build$(D)build.md)
      endif
    else  # Unix
      ANT_BINDIR:=$(dir $(firstword $(shell $(WHICH) ant$(BAT) 2>$(NULL))))
      $(warning $(WHICH) ant$(BAT) 2>$(NULL) returned)
      $(warning $(ANT_BINDIR))
      ifneq (,$(ANT_BINDIR))
        ANT_BINDIR:=$(realpath $(ANT_BINDIR)$(D)ant$(BAT))
        ANT_HOME:=$(abspath $(ANT_BINDIR)$(D)..$(D)..)
        $(warning ANT_HOME set to $(ANT_HOME))
        $(warning Found $(ANT_BINDIR), will start build with $(ANT_LAUNCHER).  Run make configure to install the required ant version 1.10.1 or follow the prereq install instructions in build$(D)build.md)
      else
        $(error Unable to locate ant to start the build. Either add ant to PATH, set ANT_HOME or follow the prereq install instructions in build$(D)build.md)
      endif
    endif
  endif
endif

ifeq (1,$(ERROR))
  $(error Fix above errors and retry)
endif

JAVA:=$(JAVA_HOME)$(D)bin$(D)java$(EXE)
JAVA_OUTPUT:=$(shell $(JAVA) -fullversion 2>&1)
$(warning $(JAVA) -fullversion returned)
$(warning $(JAVA_OUTPUT))

ifneq (,$(JAVA8_HOME))
  $(warning JAVA8_HOME set to $(JAVA8_HOME))
  JAVA8:=$(JAVA8_HOME)$(D)bin$(D)java$(EXE)
  JAVAC8:=$(JAVA8_HOME)$(D)bin$(D)javac$(EXE)
  JAVA8_OUTPUT:=$(shell $(JAVA8) -fullversion 2>&1)
  $(warning $(JAVA8) -fullversion returned)
  $(warning $(JAVA8_OUTPUT))
else
  $(warning JAVA8_HOME not set, will use $(JAVA_HOME) as java8 compiler)
  JAVA8_HOME:=$(JAVA_HOME)
endif

ifneq (,$(JAVA9_HOME))
  $(warning JAVA9_HOME set to $(JAVA9_HOME))
  JAVA9:=$(JAVA9_HOME)$(D)bin$(D)java$(EXE)
  JAVAC9:=$(JAVA9_HOME)$(D)bin$(D)javac$(EXE)
  JAVA9_OUTPUT:=$(shell $(JAVA9) -fullversion 2>&1)
  $(warning $(JAVA9) -fullversion returned)
  $(warning $(JAVA9_OUTPUT))
else
  $(warning JAVA9_HOME not set, will use $(JAVA_HOME) as java9 compiler)
  JAVA9_HOME:=$(JAVA_HOME)
endif

BUILD_COMMAND:=$(ANT_JAVA_HOME)$(D)bin$(D)java$(EXE) -classpath $(ANT_LAUNCHER) org.apache.tools.ant.launch.Launcher -buildfile $(TARGET_ROOT)$(D)stf.build$(D)build.xml -Dprereqs_root=$(PREREQS_ROOT)
BUILD_COMMAND:=$(BUILD_COMMAND) -Djava8_home=$(JAVA8_HOME)
BUILD_COMMAND:=$(BUILD_COMMAND) -Djava9_home=$(JAVA9_HOME)

# Provide a means for users to pass -java-args to the STF tests
# Java arguments may contain imbedded quotes.  These need to be escaped on the STF command line.
# An IBM (test / debugging) arguments example:
# java -Xjit:counts="- - - - - - 1 1 1 1000 250 250 - - - 10000 100000 10000" -Xgcpolicy:optthruput
# requires on Windows
# stf.pl -java-args="-Xjit:counts="""- - - - - - 1 1 1 1000 250 250 - - - 10000 100000 10000""" -Xgcpolicy:optthruput"
# and on unix
# stf.pl -java-args="-Xjit:counts=\"- - - - - - 1 1 1 1000 250 250 - - - 10000 100000 10000\" -Xgcpolicy:optthruput"

ifneq (,$(JAVA_ARGS))
  JAVA_ARGS_ARG:=-java-args="$(subst ",$(ESC_DQ),$(JAVA_ARGS))"
endif

STF_COMMAND:=perl $(TARGET_ROOT)$(D)stf.core$(D)scripts$(D)stf.pl $(JAVA_ARGS_ARG) -systemtest-prereqs=$(PREREQS_ROOT)

# Targets to run the samples
SAMPLE_TARGETS:=test.list \
test.help \
test.SampleClientServer \
test.SampleClientServer2 \
test.SampleConcurrentProcesses \
test.SampleCustomExtensionTest \
test.SampleEchoFile \
test.SampleFailingLoadTest \
test.SampleJUnitTestRun \
test.SampleLoadTest \
test.SampleRunJDKTool \
test.SampleRunJmod \
test.SampleRunProcess \
test.SampleSecondaryJvm \
test.SampleSubTests \
test.SampleValidateFileAbsence \
test.SampleWriteFiletest

.PHONY: build configure clean refresh_source test $(SAMPLE_TARGETS)

# Targets to configure and build
build: refresh_source
	echo Running $@
	$(BUILD_COMMAND) build
configure:
	echo Running $@
	$(BUILD_COMMAND) configure
clean:
	echo Running $@
	$(BUILD_COMMAND) clean
refresh_source:
ifneq ($(SOURCE_ROOT),$(TARGET_ROOT))
	echo Running $@
ifeq ($(WINDOWS),1)
	xcopy $(D)D $(D)E $(D)Y $(D)I $(SOURCE_ROOT) $(TARGET_ROOT)
endif
endif

.PHONY: $(SAMPLE_TARGETS)

test: $(SAMPLE_TARGETS)

test.list:
	$(STF_COMMAND) -list
test.help:
	$(STF_COMMAND) -test=SampleClientServer -help
test.SampleClientServer:
	$(STF_COMMAND) -test=SampleClientServer
test.SampleClientServer2:
	$(STF_COMMAND) -test=SampleClientServer2
test.SampleConcurrentProcesses:
	$(STF_COMMAND) -test=SampleConcurrentProcesses
test.SampleCustomExtensionTest:
	$(STF_COMMAND) -test=SampleCustomExtensionTest
test.SampleEchoFile:
	$(STF_COMMAND) -test=SampleEchoFile
test.SampleJUnitTestRun:
	$(STF_COMMAND) -test=SampleJUnitTestRun
test.SampleRunJDKTool:
	$(STF_COMMAND) -test=SampleRunJDKTool
test.SampleRunJmod:
	echo Skipping $(STF_COMMAND) -test=SampleRunJmod
test.SampleRunProcess:
	$(STF_COMMAND) -test=SampleRunProcess
test.SampleSecondaryJvm:
	$(EXPORT) JAVA_HOME_SECONDARY=$(JAVA_HOME)&& $(STF_COMMAND) -test=SampleSecondaryJvm -java-args-execute-initial="-Xmx100M" -java-args-execute="-Xmx200M" -java-args-execute-secondary-initial="-Xmx300M" -java-args-execute-secondary="-Xmx400M"
test.SampleSubTests:
	$(STF_COMMAND) -test=SampleSubTests
test.SampleValidateFileAbsence:
	$(STF_COMMAND) -test=SampleValidateFileAbsence
test.SampleWriteFile:
	$(STF_COMMAND) -test=SampleWriteFile
test.SampleLoadTest:
	$(STF_COMMAND) -test=SampleLoadTest
test.SampleFailingLoadTest:
	$(STF_COMMAND) -test=SampleFailingLoadTest

help:
	@echo make or make build: Builds stf projects
	@echo make test: Runs all samples
	@echo make $(SAMPLE_TARGETS): Runs all samples
	@echo make test.xxxx: Runs individual sample xxxx
	