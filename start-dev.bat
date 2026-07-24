@echo off
rem Thin launcher for start-dev.ps1 - see docs/Development.md.
setlocal
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%start-dev.ps1" %*
exit /b %ERRORLEVEL%
