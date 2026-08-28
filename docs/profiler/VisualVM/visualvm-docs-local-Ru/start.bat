@echo off
cd /d "%~dp0"
echo Starting local documentation at http://127.0.0.1:8765/documentation.html
start "" http://127.0.0.1:8765/documentation.html
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1"
pause
