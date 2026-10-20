<#
.SYNOPSIS
  Live preview of the Cleanser app on the Android emulator (or a connected phone).

.DESCRIPTION
  Starts the emulator if no device is connected, builds and installs the debug
  app, and launches it. With -Watch it keeps running and rebuilds, reinstalls
  and relaunches the app every time a file under app/src/main changes.

.EXAMPLE
  .\scripts\preview.ps1           # build, install, launch once
  .\scripts\preview.ps1 -Watch    # same, then reload on every save (Ctrl+C to stop)
  .\scripts\preview.ps1 -Avd Pixel_9   # use a different emulator
#>
param(
    [switch]$Watch,
    [string]$Avd = "Roomy_API36"
)

# Build tools write warnings to stderr; success is judged by exit codes instead
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$gradlew = Join-Path $root "gradlew.bat"
$appId = "com.sentianbeing.cleanser"
$component = "$appId/com.example.MainActivity"

function Get-DeviceSerials {
    & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] }
}

# 1. Make sure a device is available, starting the emulator if needed
$serials = @(Get-DeviceSerials)
if ($serials.Count -eq 0) {
    Write-Host "Starting emulator '$Avd'..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList "-avd", $Avd, "-no-boot-anim"
    & $adb wait-for-device
    do { Start-Sleep -Seconds 2; $booted = (& $adb shell getprop sys.boot_completed 2>$null) } while ("$booted".Trim() -ne "1")
    $serials = @(Get-DeviceSerials)
}
# Gradle and adb both target this device when several are connected
$env:ANDROID_SERIAL = $serials[0]
Write-Host "Using device $($env:ANDROID_SERIAL)" -ForegroundColor Cyan

# 2. Build, install and launch
function Install-AndLaunch {
    & $gradlew -p $root :app:installDebug --quiet 2>&1 | Tee-Object -Variable buildOutput
    if ($LASTEXITCODE -ne 0 -and ($buildOutput -join "`n") -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        # A build signed with a different key (e.g. a release build) is installed; Android won't update over it
        Write-Host "Replacing a differently signed copy of the app (its data on this device will be reset)..." -ForegroundColor Yellow
        & $adb uninstall $appId | Out-Null
        & $gradlew -p $root :app:installDebug --quiet
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed - fix the error above and save again." -ForegroundColor Red
        return
    }
    & $adb shell am start -S -n $component | Out-Null
    Write-Host "App reloaded at $(Get-Date -Format HH:mm:ss)" -ForegroundColor Green
}

Install-AndLaunch
if (-not $Watch) { return }

# 3. Reload on every change under app/src/main
$watcher = New-Object System.IO.FileSystemWatcher (Join-Path $root "app\src\main"), "*.*"
$watcher.IncludeSubdirectories = $true
Write-Host "Watching app/src/main for changes (Ctrl+C to stop)..." -ForegroundColor Cyan
while ($true) {
    $change = $watcher.WaitForChanged([System.IO.WatcherChangeTypes]::All, 1000)
    if ($change.TimedOut) { continue }
    # Let editors finish writing, and fold a burst of saves into one rebuild
    do { $more = $watcher.WaitForChanged([System.IO.WatcherChangeTypes]::All, 800) } while (-not $more.TimedOut)
    Write-Host "Change in $($change.Name), rebuilding..." -ForegroundColor Cyan
    Install-AndLaunch
}
