@echo off
rem Starts PokeDaisy in a console window that shows which Java it uses and any error.
set "PD_DEBUG=1"
call "%~dp0PokeDaisy.bat" %*
