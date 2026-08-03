# Capture JVM thread stacks during reactive-study startup (Block 0 runtime verification).
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

$Jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$Jstack = Join-Path $Jdk "bin\jstack.exe"
$Log = "$env:TEMP\reactive-study-boot.log"
$Stack = "$env:TEMP\reactive-study-init-stacks.txt"

"" | Set-Content $Log
"" | Set-Content $Stack

Write-Host "Starting bootRun (profile local)..."
$proc = Start-Process -FilePath "cmd.exe" `
    -ArgumentList @("/c", ".\gradlew.bat --no-daemon bootRun --args=--spring.profiles.active=local > `"$Log`" 2>&1") `
    -WorkingDirectory $Root `
    -PassThru `
    -WindowStyle Hidden

function Stop-App {
    if (-not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'reactivestudy|reactive-study' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

try {
    $javaPid = $null
    for ($i = 1; $i -le 120; $i++) {
        $java = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
            Where-Object { $_.CommandLine -match 'ReactiveStudyApplication' } |
            Select-Object -First 1
        if ($java) {
            $javaPid = $java.ProcessId
            Write-Host "Java PID: $javaPid (after ${i}x0.5s)"
            break
        }
        Start-Sleep -Milliseconds 500
    }

    if (-not $javaPid) {
        Write-Host "ERROR: Java process not found"
        Get-Content $Log -Tail 40
        exit 1
    }

    $patterns = @(
        'NettyWebServer',
        'NettyReactiveWebServerFactory',
        'ServerTransport',
        'TransportConnector',
        'DefaultLoopResources',
        'ServerBootstrap',
        'NioEventLoopGroup',
        'MultiThreadIoEventLoopGroup',
        'AbstractNioChannel',
        'doBeginRead',
        'onServerSelect',
        'bindNow'
    )

    $found = $false
    for ($i = 1; $i -le 100; $i++) {
        $dump = & $Jstack $javaPid 2>$null
        if ($dump -match ($patterns -join '|')) {
            $ts = Get-Date -Format "HH:mm:ss.fff"
            Add-Content $Stack "===== jstack @ $ts iteration $i ====="
            Add-Content $Stack ($dump -join "`n")
            Add-Content $Stack ""
            $found = $true
        }
        $logText = Get-Content $Log -Raw -ErrorAction SilentlyContinue
        if ($logText -match 'Started ReactiveStudyApplication') {
            Write-Host "Application started (iteration $i)"
            break
        }
        Start-Sleep -Milliseconds 250
    }

    $finalDump = & $Jstack $javaPid 2>$null
    Add-Content $Stack "===== jstack FINAL @ $(Get-Date -Format 'HH:mm:ss') ====="
    Add-Content $Stack ($finalDump -join "`n")

    Write-Host "--- boot log tail ---"
    Get-Content $Log -Tail 25

    Write-Host "--- matched frames in stacks ---"
    Select-String -Path $Stack -Pattern ($patterns -join '|') | ForEach-Object { $_.Line.Trim() }

    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -TimeoutSec 5
        Write-Host "Health: $($health.status)"
    } catch {
        Write-Host "Health check failed: $_"
    }

    Write-Host "Stack file: $Stack"
    Write-Host "Log file: $Log"

    if (-not $found) { exit 2 }
}
finally {
    Stop-App
}
