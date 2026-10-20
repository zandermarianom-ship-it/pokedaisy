@echo off
rem PokeDaisy desktop (prototype): double-click to pick a ROM, or drag a .gba onto this file.
rem Finds a Java 17 or newer (skipping older ones, e.g. an old Java 8 on the PATH) and starts PokeDaisy.
setlocal EnableExtensions EnableDelayedExpansion
set "HERE=%~dp0"
set "JAVAW="
set "FOUND="

rem 1. A Java inside this folder, then JAVA_HOME, then the usual install folders (newest last wins), then the PATH.
call :try "%HERE%jre\bin\javaw.exe"
if defined JAVA_HOME call :try "%JAVA_HOME%\bin\javaw.exe"
for %%R in ("%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Java" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft" "%ProgramFiles%\Amazon Corretto") do (
    if exist "%%~R" for /f "delims=" %%D in ('dir /b /ad "%%~R" 2^>nul') do call :try "%%~R\%%D\bin\javaw.exe"
)
for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" call :try "%%~$PATH:J"

if not defined JAVAW (
    echo PokeDaisy needs Java 17 or newer.
    if defined FOUND (
        echo.
        echo Found only older Java: !FOUND!
    )
    echo.
    echo Your browser will open the free Java download page. Install the "JRE" for Windows x64 ^(.msi^),
    echo then double-click PokeDaisy.bat again. You can keep your old Java; PokeDaisy picks the new one.
    start "" "https://adoptium.net/temurin/releases/?os=windows&arch=x64&package=jre&version=21"
    pause
    exit /b 1
)

if not exist "%HERE%userdata" mkdir "%HERE%userdata"
echo Using Java %JVER% at %JAVAW%> "%HERE%userdata\launcher.log"
rem The app reads its fonts from the working folder, as Android reads its assets.
cd /d "%HERE%assets"
if defined PD_DEBUG (
    rem PokeDaisy-debug.bat: java.exe in this window, so every message shows, and the window stays open.
    echo Using Java %JVER% at %JAVAW%
    "%JAVAW:javaw.exe=java.exe%" -Xmx1g -Djava.library.path="%HERE%native" -Dscratch="%HERE%userdata" -cp "%HERE%lib\*" desktop.WindowsKt %*
    echo.
    echo PokeDaisy stopped. If something went wrong, take a photo of this window.
    pause
    exit /b 0
)
start "" "%JAVAW%" -Xmx1g -Djava.library.path="%HERE%native" -Dscratch="%HERE%userdata" -Dlogfile="%HERE%userdata\pokedaisy.log" -cp "%HERE%lib\*" desktop.WindowsKt %*
exit /b 0

rem :try "path\to\javaw.exe" - takes it if it's Java 17+ and newer than what was found so far.
:try
if not exist "%~1" exit /b 0
set "JAVA_EXE=%~dp1java.exe"
if not exist "!JAVA_EXE!" exit /b 0
set "V="
for /f "tokens=3" %%V in ('""!JAVA_EXE!" -version 2>&1 | findstr /i "version""') do if not defined V set "V=%%~V"
if not defined V exit /b 0
for /f "tokens=1 delims=." %%M in ("!V!") do set "MAJOR=%%M"
rem Java 8 and older report "1.8.0_xxx": major 1.
if "!MAJOR!"=="1" (
    set "FOUND=!FOUND! !V!"
    exit /b 0
)
set /a MAJORN=MAJOR 2>nul
if !MAJORN! LSS 17 (
    set "FOUND=!FOUND! !V!"
    exit /b 0
)
if defined JAVAW if !MAJORN! LEQ !BESTN! exit /b 0
set "JAVAW=%~1"
set "JVER=!V!"
set "BESTN=!MAJORN!"
exit /b 0
