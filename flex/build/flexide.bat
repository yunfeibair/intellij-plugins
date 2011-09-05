@ECHO OFF

::----------------------------------------------------------------------
:: Flex IDE Startup Script
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Location of the JDK 1.6 installation directory
:: which will be used for running the IDE.
:: ---------------------------------------------------------------------
SET JDK=%FLEXIDE_JDK%
IF "%JDK%" == "" SET JDK=%JDK_HOME%
IF "%JDK%" == "" GOTO error

SET JAVA_EXE=%JDK%\jre\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" GOTO error

:: ---------------------------------------------------------------------
:: Location of the directory where the IDE is installed
:: In most cases you do not need to change the settings below.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
SET IDE_HOME=%IDE_BIN_DIR%\..

SET MAIN_CLASS_NAME=%FLEXIDE_MAIN_CLASS_NAME%
IF "%MAIN_CLASS_NAME%" == "" SET MAIN_CLASS_NAME=com.intellij.idea.Main

IF NOT "%FLEXIDE_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%FLEXIDE_PROPERTIES%"

:: ---------------------------------------------------------------------
:: You may specify your own JVM arguments in .vmoptions file.
:: Put one option per line there.
:: ---------------------------------------------------------------------
SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\flexide.exe.vmoptions
SET ACC=
FOR /F "usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"

SET REQUIRED_JVM_ARGS="-Xbootclasspath/a:%IDE_HOME%/lib/boot.jar" -Didea.paths.selector=@@system_selector@@ %IDE_PROPERTIES_PROPERTY%
SET SPECIAL_JVM_ARGS=-Didea.platform.prefix=Flex -Didea.no.jre.check=true
SET JVM_ARGS=%ACC% %REQUIRED_JVM_ARGS% %SPECIAL_JVM_ARGS% %REQUIRED_FLEXIDE_JVM_ARGS%

SET OLD_PATH=%PATH%
SET PATH=%IDE_BIN_DIR%;%PATH%

SET CLASS_PATH=%IDE_HOME%\lib\bootstrap.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\util.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\extensions.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\trove4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jna.jar
:: TODO[yole]: remove
SET CLASS_PATH=%CLASS_PATH%;%JDK%\lib\tools.jar

:: ---------------------------------------------------------------------
:: You may specify additional class paths in FLEXIDE_CLASS_PATH variable.
:: It is a good idea to specify paths to your plugins in this variable.
:: ---------------------------------------------------------------------
IF NOT "%FLEXIDE_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%FLEXIDE_CLASS_PATH%

"%JAVA_EXE%" %JVM_ARGS% -cp "%CLASS_PATH%" %MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
GOTO end

:error
ECHO ---------------------------------------------------------------------
ECHO ERROR: cannot start Flex IDE.
ECHO No JDK found. Please validate either FLEXIDE_JDK or JDK_HOME points to valid JDK installation.
ECHO ---------------------------------------------------------------------
PAUSE

:end
