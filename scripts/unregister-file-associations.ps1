$ErrorActionPreference = 'SilentlyContinue'

Write-Host "Unregistering Mihon W file associations..." -ForegroundColor Cyan

# Remove ProgIDs
Remove-Item -Path "HKCU:\Software\Classes\MihonW.Backup" -Recurse -Force
Remove-Item -Path "HKCU:\Software\Classes\MihonW.Comic" -Recurse -Force

# Clean .tachibk
Remove-ItemProperty -Path "HKCU:\Software\Classes\.tachibk\OpenWithProgids" -Name "MihonW.Backup" -Force
$curr = (Get-ItemProperty -Path "HKCU:\Software\Classes\.tachibk" -Name "(default)")."(default)"
if ($curr -eq "MihonW.Backup") {
    Remove-Item -Path "HKCU:\Software\Classes\.tachibk" -Recurse -Force
}

# Clean .cbz
Remove-ItemProperty -Path "HKCU:\Software\Classes\.cbz\OpenWithProgids" -Name "MihonW.Comic" -Force
$curr = (Get-ItemProperty -Path "HKCU:\Software\Classes\.cbz" -Name "(default)")."(default)"
if ($curr -eq "MihonW.Comic") {
    Remove-Item -Path "HKCU:\Software\Classes\.cbz" -Recurse -Force
}

Write-Host "Mihon W file associations removed from HKCU." -ForegroundColor Green
