@echo off
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set OUT=%~dp0jstack-17212.txt
"%JDK%\bin\jstack.exe" 17212 > "%OUT%" 2>&1
echo exit=%ERRORLEVEL%
type "%OUT%" | findstr /I "NettyWebServer ServerTransport TransportConnector DefaultLoopResources MultiThreadIoEventLoopGroup NioIoHandler Mono.block bindNow doBeginRead onServerSelect reactor-http server"
