@echo off

: Set the BASEPATH variable below to the directory where XSS is.

: Copyright (c) 2003 Rasmus Sten <rasmus@bricole.se>

set BASEPATH=C:\info\dev-xss2\xss

set CONFIG=%BASEPATH%\config.xml
set DEBUG=false
set XSSCLASSES=%BASEPATH%\classes;C:\info\dev-minmejl\tech\java-classes;c:\info\dev-redbull\java-classes
set LIBPATH=%BASEPATH%\jars

IF "%1" == "/DEBUG" (
    set DEBUG=true
)

: Make sure that delayed variable expansion is available in the shell.
IF NOT "!ZXSDKJWE!" == "" (
    VERIFY OTHER 2>nul
    SETLOCAL ENABLEDELAYEDEXPANSION

    IF ERRORLEVEL 1 (
        echo Shell does not support delayed variable expansion.
        goto bail   
    )
)

IF "%JAVA_HOME%"=="" (
    echo No JAVA_HOME variable set! Cannot find Java environment.
    goto bail
)

:set XSS_CP=%CLASSPATH%;%XSSCLASSES%
set XSS_CP=%XSSCLASSES%

FOR %%f IN (%LIBPATH%\*.jar) DO (
    set XSS_CP=!XSS_CP!;%%f
)

echo on
%JAVA_HOME%\bin\java -Dxss.debug=%DEBUG% -cp %XSS_CP% se.bricole.xss.server.Server %CONFIG%
@echo off

goto clean
:bail
echo Bailing out.
:clean