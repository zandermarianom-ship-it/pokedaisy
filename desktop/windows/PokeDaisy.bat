@echo off
rem PokeDaisy desktop (prototype): double-click to pick a ROM, or drag a .gba onto this file.
setlocal
set "HERE=%~dp0"
set "JAVAW="
if exist "%HERE%jre\bin\javaw.exe" set "JAVAW=%HERE%jre\bin\javaw.exe"
if not defined JAVAW if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JAVAW=%%~$PATH:J"
if not defined JAVAW (
    echo PokeDaisy needs Java 17 or newer, and none was found.
    echo.
    echo Your browser will open the free Java download page: install the "JRE" for Windows x64 ^(.msi^),
    echo tick "Set JAVA_HOME" in the installer, then double-click PokeDaisy.bat again.
    start "" "https://adoptium.net/temurin/releases/?os=windows&arch=x64&package=jre&version=21"
    pause
    exit /b 1
)
if not exist "%HERE%userdata" mkdir "%HERE%userdata"
rem The app reads its fonts from the working folder, as Android reads its assets.
cd /d "%HERE%assets"
start "" "%JAVAW%" -Xmx1g -Dsun.java2d.uiScale.enabled=true -Djava.library.path="%HERE%native" -Dscratch="%HERE%userdata" -Dlogfile="%HERE%userdata\pokedaisy.log" -cp "%HERE%lib\*" desktop.WindowsKt %*
