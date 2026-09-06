param(
    [Parameter(Mandatory = $false)]
    [string]$ExecutablePath
)

$ErrorActionPreference = 'Stop'

if (-not $ExecutablePath) {
    # Check default release locations
    $candidates = @(
        "$PSScriptRoot\..\desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe",
        "$env:LOCALAPPDATA\Programs\MihonW\MihonW.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $ExecutablePath = (Resolve-Path $candidate).Path
            break
        }
    }
}

if (-not $ExecutablePath -or -not (Test-Path $ExecutablePath)) {
    Write-Error "MihonW.exe not found. Please provide -ExecutablePath explicitly."
    exit 1
}

$exe = (Resolve-Path $ExecutablePath).Path
Write-Host "Registering file associations for: $exe" -ForegroundColor Cyan

# 1. Register .tachibk
$progIdBackup = "MihonW.Backup"
New-Item -Path "HKCU:\Software\Classes\$progIdBackup" -Value "Mihon Android Backup Archive" -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\$progIdBackup\DefaultIcon" -Value "$exe,0" -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\$progIdBackup\shell\open\command" -Value "`"$exe`" `"%1`"" -Force | Out-Null

New-Item -Path "HKCU:\Software\Classes\.tachibk" -Value $progIdBackup -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\.tachibk\OpenWithProgids" -Force | Out-Null
Set-ItemProperty -Path "HKCU:\Software\Classes\.tachibk\OpenWithProgids" -Name $progIdBackup -Value ([byte[]]@())

Write-Host "Registered .tachibk association." -ForegroundColor Green

# 2. Register .cbz
$progIdComic = "MihonW.Comic"
New-Item -Path "HKCU:\Software\Classes\$progIdComic" -Value "Comic Book Archive (CBZ)" -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\$progIdComic\DefaultIcon" -Value "$exe,0" -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\$progIdComic\shell\open\command" -Value "`"$exe`" `"%1`"" -Force | Out-Null

New-Item -Path "HKCU:\Software\Classes\.cbz" -Value $progIdComic -Force | Out-Null
New-Item -Path "HKCU:\Software\Classes\.cbz\OpenWithProgids" -Force | Out-Null
Set-ItemProperty -Path "HKCU:\Software\Classes\.cbz\OpenWithProgids" -Name $progIdComic -Value ([byte[]]@())

Write-Host "Registered .cbz association." -ForegroundColor Green

Write-Host "`nAll file associations successfully registered in HKCU." -ForegroundColor Green
