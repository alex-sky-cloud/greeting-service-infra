@echo off
setlocal EnableDelayedExpansion
set ROOT=%~dp0..\..
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set AGENT_JAR=%~dp0agent\build\init-path-agent.jar
set TRACE_LOG=%~dp0agent\block0-init-trace.log
set BOOT_LOG=%~dp0agent\boot-with-agent.log

call "%~dp0build-agent.cmd"
if errorlevel 1 (
  python "%~dp0build_agent.py"
  if errorlevel 1 exit /b 1
)

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8083" ^| findstr LISTENING') do taskkill /PID %%p /F >nul 2>&1

cd /d "%ROOT%"
echo Building bootJar...
call gradlew.bat --no-daemon bootJar
if errorlevel 1 exit /b 1

echo.>"%TRACE_LOG%"
echo.>"%BOOT_LOG%"

echo Starting reactive-study with javaagent...
start "reactive-study-agent" /B cmd /c ""%JDK%\bin\java.exe" -javaagent:"%AGENT_JAR%" -Dblock0.agent.log="%TRACE_LOG%" -jar "%ROOT%\build\libs\reactive-study.jar" --spring.profiles.active=local >"%BOOT_LOG%" 2>&1"

set /a N=0
:wait_loop
set /a N+=1
findstr /C:"Started ReactiveStudyApplication" "%BOOT_LOG%" >nul 2>&1 && goto started
if !N! GEQ 120 goto timeout_fail
ping -n 2 127.0.0.1 >nul
goto wait_loop

:started
echo Application started.
curl -sf http://localhost:8083/actuator/health
echo.

echo --- ENTER methods (unique) ---
findstr /R /C:"^>>> ENTER" "%TRACE_LOG%"

echo.
echo --- Boot log (Netty line) ---
findstr /C:"Netty started" "%BOOT_LOG%"

echo.
echo Trace file: %TRACE_LOG%

for /f "tokens=2 delims=," %%p in ('wmic process where "CommandLine like '%%reactive-study.jar%%'" get ProcessId /format:csv ^| findstr /R "[0-9]"') do (
  taskkill /PID %%p /F >nul 2>&1
)
exit /b 0

:timeout_fail
echo TIMEOUT waiting for startup
type "%BOOT_LOG%"
exit /b 1
