@echo off
setlocal EnableDelayedExpansion
set ROOT=%~dp0..\..
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set BOOT_LOG=%~dp0boot-runtime.log
set JSTACK_LOG=%~dp0jstack-runtime.txt

cd /d "%ROOT%"
echo.>"%BOOT_LOG%"
echo.>"%JSTACK_LOG%"

echo Starting bootRun (profile local)...
start "reactive-study-boot" /B cmd /c "gradlew.bat --no-daemon bootRun --args=--spring.profiles.active=local >"%BOOT_LOG%" 2>&1"

set JAVA_PID=
set /a N=0
:wait_loop
set /a N+=1
findstr /C:"Started ReactiveStudyApplication" "%BOOT_LOG%" >nul 2>&1 && goto started
if !N! GEQ 90 goto timeout_fail
timeout /t 1 /nobreak >nul
goto wait_loop

:started
for /f "tokens=2 delims=," %%p in ('wmic process where "CommandLine like '%%ReactiveStudyApplication%%'" get ProcessId /format:csv ^| findstr /R "[0-9]"') do set JAVA_PID=%%p

echo PID: !JAVA_PID!
echo --- boot log (key lines) --- >>"%JSTACK_LOG%"
findstr /C:"Netty started" /C:"Started ReactiveStudyApplication" /C:"Java 21" "%BOOT_LOG%" >>"%JSTACK_LOG%"

if defined JAVA_PID (
  echo --- jstack --- >>"%JSTACK_LOG%"
  "%JDK%\bin\jstack.exe" !JAVA_PID! >>"%JSTACK_LOG%" 2>&1
)

curl -sf http://localhost:8083/actuator/health
echo.

echo --- grep Netty/Reactor frames ---
findstr /I "NettyWebServer ServerTransport TransportConnector DefaultLoopResources MultiThreadIoEventLoopGroup NioIoHandler NioEventLoopGroup ServerBootstrap AbstractNioChannel doBeginRead onServerSelect bindNow Mono.block" "%JSTACK_LOG%"

if defined JAVA_PID taskkill /PID !JAVA_PID! /F >nul 2>&1
for /f "tokens=2 delims=," %%p in ('wmic process where "CommandLine like '%%bootRun%%'" get ProcessId /format:csv ^| findstr /R "[0-9]"') do taskkill /PID %%p /F >nul 2>&1
exit /b 0

:timeout_fail
echo TIMEOUT
type "%BOOT_LOG%"
exit /b 1
