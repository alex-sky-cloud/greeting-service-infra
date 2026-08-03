@echo off
setlocal EnableDelayedExpansion
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set OUT=%~dp0javap-verified.txt
set SB=D:\.gradle\caches\modules-2\files-2.1\org.springframework.boot\spring-boot-reactor-netty\4.0.5\91cfce8fac2858fe0c519479b302509790fbc25a\spring-boot-reactor-netty-4.0.5.jar
set RN=D:\.gradle\caches\modules-2\files-2.1\io.projectreactor.netty\reactor-netty-core\1.3.4\6695d33dc22b8f7523aa6f952e78cdb62223916f\reactor-netty-core-1.3.4.jar
set RNH=D:\.gradle\caches\modules-2\files-2.1\io.projectreactor.netty\reactor-netty-http\1.3.4\%RNH_HASH%\reactor-netty-http-1.3.4.jar
for /f "delims=" %%f in ('dir /s /b D:\.gradle\caches\modules-2\files-2.1\io.projectreactor.netty\reactor-netty-http\1.3.4\*.jar 2^>nul ^| findstr /V sources') do set RNH=%%f
set NT=D:\.gradle\caches\modules-2\files-2.1\io.netty\netty-transport\4.2.12.Final\e9d42074c3d96cf31ce57cc58f6de6f31959b7a8\netty-transport-4.2.12.Final.jar

echo === javap verification %DATE% %TIME% === > "%OUT%"

call :dump "%SB%" org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory getWebServer
call :dump "%SB%" org.springframework.boot.reactor.netty.NettyWebServer start
call :dump "%SB%" org.springframework.boot.reactor.netty.NettyWebServer startHttpServer
call :dump "%RNH%" reactor.netty.http.server.HttpServer bindNow
call :dump "%RNH%" reactor.netty.transport.ServerTransport bind
call :dump "%RN%" reactor.netty.transport.TransportConnector bind
call :dump "%RN%" reactor.netty.transport.TransportConnector doInitAndRegister
call :dump "%RN%" reactor.netty.resources.DefaultLoopResources onServerSelect
call :dump "%RN%" reactor.netty.resources.DefaultLoopResources onServer
call :dump "%RNH%" reactor.netty.http.server.HttpResources get
call :dump "%RN%" reactor.netty.transport.ServerTransportConfig eventLoopGroup
call :dump "%RN%" reactor.netty.transport.ServerTransportConfig childEventLoopGroup
call :dump "%NT%" io.netty.channel.nio.AbstractNioChannel doBeginRead
call :dump "%NT%" io.netty.channel.nio.NioIoHandler run
call :dump "%NT%" io.netty.channel.nio.NioIoHandler select
call :dump "%RN%" reactor.netty.transport.ServerTransport$Acceptor channelRead
call :dump "%NT%" io.netty.bootstrap.ServerBootstrap doBind
call :dump "%NT%" io.netty.bootstrap.AbstractBootstrap doBind

type "%OUT%"
exit /b 0

:dump
set JAR=%~1
set CLS=%~2
set METHOD=%~3
echo.>> "%OUT%"
echo --- %CLS%#%METHOD% --- >> "%OUT%"
"%JDK%\bin\javap.exe" -classpath "%JAR%" -public -protected -private -p %CLS% 2>> "%OUT%" | findstr /I /C:"%METHOD%(" >> "%OUT%"
if errorlevel 1 echo NOT FOUND >> "%OUT%"
exit /b 0
