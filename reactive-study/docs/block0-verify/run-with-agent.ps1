# Build InitPathAgent and run reactive-study with runtime method tracing (Block 0).
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$AgentDir = Join-Path $PSScriptRoot "agent"
$Build = Join-Path $AgentDir "build"
$Log = Join-Path $AgentDir "block0-init-trace.log"
$BootLog = "$env:TEMP\reactive-study-agent-boot.log"
$Jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$Javac = Join-Path $Jdk "bin\javac.exe"
$Jar = Join-Path $Jdk "bin\jar.exe"
$AsmVersion = "9.7.1"
$Lib = Join-Path $Build "lib"
$Classes = Join-Path $Build "classes"
$AgentJar = Join-Path $Build "init-path-agent.jar"
$AsmJar = Join-Path $Lib "asm-$AsmVersion.jar"

New-Item -ItemType Directory -Force -Path $Lib, $Classes | Out-Null

if (-not (Test-Path $AsmJar)) {
    Write-Host "Downloading ASM $AsmVersion..."
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/ow2/asm/asm/$AsmVersion/asm-$AsmVersion.jar" -OutFile $AsmJar
}

Write-Host "Compiling agent..."
& $Javac -cp $AsmJar --release 17 -d $Classes (Join-Path $AgentDir "InitPathAgent.java")

Write-Host "Packaging agent jar (fat jar with ASM)..."
$Manifest = Join-Path $AgentDir "META-INF\MANIFEST.MF"
Push-Location $Classes
& $Jar xf $AsmJar
Pop-Location
& $Jar cfm $AgentJar $Manifest -C $Classes .

"" | Set-Content $Log
"" | Set-Content $BootLog

Set-Location $Root
$jvmArgs = "-javaagent:$AgentJar -Dblock0.agent.log=$Log"

Write-Host "Building bootJar..."
& ".\gradlew.bat" "--no-daemon" "bootJar" | Out-Null

$AppJar = Join-Path $Root "build\libs\reactive-study.jar"
if (-not (Test-Path $AppJar)) {
    throw "Jar not found: $AppJar"
}

$Java = Join-Path $Jdk "bin\java.exe"
Write-Host "Running jar with javaagent..."
$proc = Start-Process -FilePath "cmd.exe" `
    -ArgumentList @("/c", "`"$Java`" -javaagent:`"$AgentJar`" -Dblock0.agent.log=`"$Log`" -jar `"$AppJar`" --spring.profiles.active=local > `"$BootLog`" 2>&1") `
    -WorkingDirectory $Root `
    -PassThru `
    -WindowStyle Hidden

function Stop-All {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'reactive-study\.jar|ReactiveStudyApplication' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

try {
    for ($i = 1; $i -le 120; $i++) {
        $boot = Get-Content $BootLog -Raw -ErrorAction SilentlyContinue
        if ($boot -match 'Started ReactiveStudyApplication') { break }
        Start-Sleep -Milliseconds 500
    }

    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -TimeoutSec 5
        Write-Host "Health: $($health.status)"
    } catch {
        Write-Host "Health check failed: $_"
    }

    Write-Host "--- Block 0 trace (unique ENTER lines) ---"
    Select-String -Path $Log -Pattern '^>>> ENTER' | ForEach-Object { $_.Line } | Sort-Object -Unique

    Write-Host "--- Boot log tail ---"
    Get-Content $BootLog -Tail 15

    Write-Host "Full trace: $Log"
}
finally {
    Stop-All
}
