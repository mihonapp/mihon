param(
    [switch]$AppOnly,
    [switch]$ExtensionsOnly,
    [switch]$All,
    [switch]$Force,
    [string]$DeviceId,
    [switch]$AllDevices
)

if (-not $AppOnly -and -not $ExtensionsOnly) {
    $All = $true
}

$ErrorActionPreference = "Continue"

function Get-ConnectedDevices {
    $devices = @()
    adb devices | Select-Object -Skip 1 | ForEach-Object {
        if ($_ -match '^(\S+)\s+device') {
            $devices += $matches[1]
        }
    }
    return $devices
}

function Install-ToDevice {
    param([string]$Device, [string]$ApkPath, [string]$Description = "APK")
    Write-Host "[$Device] Installing $Description..."
    adb -s $Device install -r $ApkPath
    return $LASTEXITCODE -eq 0
}

$devices = Get-ConnectedDevices
if ($devices.Count -eq 0) {
    if ($AppOnly) {
        Write-Host "No ADB devices connected; building app only..."
        $devices = @()
    } else {
        Write-Host "No ADB devices connected!"
        exit 1
    }
}

if ($devices.Count -gt 0) {
    if (-not [string]::IsNullOrWhiteSpace($DeviceId)) {
        if ($devices -notcontains $DeviceId) {
            Write-Host "Requested device not found: $DeviceId"
            Write-Host "Connected device(s): $($devices -join ', ')"
            exit 1
        }
        $devices = @($DeviceId)
    }
    elseif (-not $AllDevices -and $devices.Count -gt 1) {
        $selected = $devices[0]
        Write-Host "Found $($devices.Count) device(s): $($devices -join ', ')"
        Write-Host "Multiple devices detected; defaulting to first: $selected"
        Write-Host "Tip: pass -DeviceId <id> or -AllDevices"
        $devices = @($selected)
    }
    else {
        Write-Host "Found $($devices.Count) device(s): $($devices -join ', ')"
    }
}

if ($All -or $AppOnly) {
    Write-Host "Building Mihon app..."
    Push-Location $PSScriptRoot
    .\gradlew :app:assembleDebug
    $appApk = Get-ChildItem -Path "app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
    if ($appApk) {
        if ($devices.Count -gt 0) {
            Write-Host "Installing app to all devices: $($appApk.Name)"
            foreach ($device in $devices) {
                Install-ToDevice -Device $device -ApkPath $appApk.FullName -Description "app"
            }
        } else {
            Write-Host "App APK built: $($appApk.Name)"
            Write-Host "No devices connected for installation"
        }
    }
    else {
        Write-Host "App APK not found!"
    }
    Pop-Location
}

if ($All -or $ExtensionsOnly) {
    Write-Host "Building extensions..."
    $extensionsDir = Join-Path $PSScriptRoot "extensions-source"
    if (Test-Path $extensionsDir) {
        Push-Location $extensionsDir
        .\gradlew assembleDebug
        $extensionApks = Get-ChildItem -Path $extensionsDir -Recurse -Filter "*.apk" | Where-Object { $_.FullName -like "*\build\outputs\apk\*" }
        if ($extensionApks.Count -gt 0) {
            Write-Host "Found $($extensionApks.Count) extension APK(s)"
            foreach ($device in $devices) {
                Write-Host "Installing extensions to: $device"
                $deviceInstalled = 0
                foreach ($apk in $extensionApks) {
                    if (Install-ToDevice -Device $device -ApkPath $apk.FullName -Description $apk.Name) {
                        $deviceInstalled++
                    }
                }
                Write-Host "[$device] Installed: $deviceInstalled/$($extensionApks.Count) extensions"
            }
        }
        else {
            Write-Host "No extension APKs found!"
        }
        Pop-Location
    }
    else {
        Write-Host "Extensions directory not found: $extensionsDir"
    }
}

Write-Host "Installation complete for $($devices.Count) device(s)!"