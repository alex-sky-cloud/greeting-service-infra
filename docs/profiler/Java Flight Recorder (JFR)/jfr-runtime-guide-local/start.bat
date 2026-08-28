@echo off
cd /d "%~dp0"
echo Starting local documentation at http://127.0.0.1:8765/
start "" http://127.0.0.1:8765/
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1"
pause
