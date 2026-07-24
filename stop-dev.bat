@echo off
rem Thin launcher for stop-dev.ps1 - see docs/Development.md.
setlocal
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%stop-dev.ps1" %*
exit /b %ERRORLEVEL%
