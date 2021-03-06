${atChar}echo off

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

set BTRACE_LIB=%BTRACE_HOME%\lib

if not exist "%BTRACE_LIB%\cli-${project.version}.jar" goto noBTraceHome

if "%JAVA_HOME%" == "" goto noJavaHome

set BTRACE_CP=
for %%a in (%BTRACE_LIB%\*.jar) do (
    set BTRACE_CP=%BTRACE_CP%;%%a
)

  "%JAVA_HOME%/bin/java" -Dnet.java.btrace.probeDescPath=. -Dnet.java.btrace.dumpClasses=false -Dnet.java.btrace.debug=false -Dnet.java.btrace.unsafe=false -cp "%BTRACE_CP%;%JAVA_HOME%/lib/tools.jar" net.java.btrace.client.Main %*
  goto end
:noJavaHome
  echo Please set JAVA_HOME before running this script
  goto end
:noBTraceHome
  echo Please set BTRACE_HOME before running this script
:end
