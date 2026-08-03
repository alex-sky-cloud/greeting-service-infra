@echo off
setlocal
set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set AGENT_DIR=%~dp0agent
set BUILD=%AGENT_DIR%\build
set LIB=%BUILD%\lib
set CLASSES=%BUILD%\classes
set ASM=%LIB%\asm-9.7.1.jar
set AGENT_JAR=%BUILD%\init-path-agent.jar

if not exist "%LIB%" mkdir "%LIB%"
if not exist "%CLASSES%" mkdir "%CLASSES%"
if exist "%CLASSES%\block0verify" rmdir /s /q "%CLASSES%\block0verify"

if not exist "%ASM%" (
  echo Downloading ASM...
  curl -fsSL -o "%ASM%" https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar
)

echo Compiling InitPathAgent...
"%JDK%\bin\javac.exe" -classpath "%ASM%" -source 11 -target 11 -d "%CLASSES%" "%AGENT_DIR%\InitPathAgent.java"
if errorlevel 1 exit /b 1

echo Packaging fat agent jar...
set STAGE=%BUILD%\stage
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
pushd "%STAGE%"
"%JDK%\bin\jar.exe" xf "%ASM%"
xcopy /E /I /Y "%CLASSES%\block0verify" "%STAGE%\block0verify\" >nul
popd
"%JDK%\bin\jar.exe" cfm "%AGENT_JAR%" "%AGENT_DIR%\META-INF\MANIFEST.MF" -C "%STAGE%" .
echo Agent: %AGENT_JAR%
