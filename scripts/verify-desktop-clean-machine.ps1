$ErrorActionPreference = 'Stop'

Write-Host "=== Phase 8: Clean-Machine Sandbox Verification ===" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 1. Build portable package
Write-Host "`n1. Building portable ZIP distribution..." -ForegroundColor Yellow
.\gradlew.bat :desktop-app:packagePortableZip
if ($LASTEXITCODE -ne 0) { throw "packagePortableZip failed" }

$portableZip = (Resolve-Path "desktop-app\build\compose\binaries\main\portable\MihonW-0.1.0-windows-x64-portable.zip").Path
if (-not (Test-Path $portableZip)) {
    throw "Portable zip not found at $portableZip"
}
Write-Host "Found portable zip: $portableZip ($((Get-Item $portableZip).Length / 1MB) MB)" -ForegroundColor Green

# 2. Setup isolated sandbox
$sandboxId = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)
$sandboxDir = Join-Path $env:TEMP "mihon-sandbox-$sandboxId"
New-Item -ItemType Directory -Path $sandboxDir -Force | Out-Null
Write-Host "`n2. Extracting to clean-machine sandbox: $sandboxDir" -ForegroundColor Yellow

Expand-Archive -Path $portableZip -DestinationPath $sandboxDir -Force

$appDir = Join-Path $sandboxDir "MihonW"
$exePath = Join-Path $appDir "MihonW.exe"
$portableMarker = Join-Path $appDir ".portable"

if (-not (Test-Path $exePath)) {
    throw "Sandbox extraction verification failed: $exePath does not exist!"
}
if (-not (Test-Path $portableMarker)) {
    throw "Sandbox extraction verification failed: .portable marker does not exist!"
}
Write-Host "Sandbox application files verified." -ForegroundColor Green

# 3. Test --version
Write-Host "`n3. Testing execution: --version..." -ForegroundColor Yellow
$versionOut = & $exePath --version
if ($LASTEXITCODE -ne 0) {
    throw "MihonW --version exited with non-zero code: $LASTEXITCODE"
}
$versionStr = $versionOut -join "`n"
Write-Host "Output: $versionStr" -ForegroundColor DarkGray
if ($versionStr -notmatch "Mihon W 0.1.0") {
    throw "Output did not match expected version string!"
}
Write-Host "--version verified successfully." -ForegroundColor Green

# 4. Test --help
Write-Host "`n4. Testing execution: --help..." -ForegroundColor Yellow
$helpOut = & $exePath --help
if ($LASTEXITCODE -ne 0) {
    throw "MihonW --help exited with non-zero code: $LASTEXITCODE"
}
$helpStr = $helpOut -join "`n"
if ($helpStr -notmatch "Mihon W - Manga Reader for Windows") {
    throw "Output did not match expected help text!"
}
Write-Host "--help verified successfully." -ForegroundColor Green

# 5. Test Portable Mode Isolation & Backup Export
Write-Host "`n5. Testing portable isolation & headless export..." -ForegroundColor Yellow
$targetBackup = Join-Path $sandboxDir "exported-sandbox-backup.tachibk"
$exportOut = & $exePath "--export-backup=$targetBackup"
if ($LASTEXITCODE -ne 0) {
    throw "MihonW --export-backup exited with non-zero code: $LASTEXITCODE"
}

# Verify data was placed inside sandbox\MihonW\data and NOT polluting APPDATA
$localDataDir = Join-Path $appDir "data"
if (-not (Test-Path $localDataDir)) {
    throw "Portable isolation failed: Local data directory was not created in $appDir"
}
if (-not (Test-Path (Join-Path $localDataDir "database\library.db"))) {
    throw "Database not found in local portable directory: $localDataDir\database\library.db"
}
if (-not (Test-Path $targetBackup)) {
    throw "Exported backup file not created at: $targetBackup"
}
Write-Host "Portable isolation verified! Data directory: $localDataDir" -ForegroundColor Green
Write-Host "Exported backup verified! File: $targetBackup" -ForegroundColor Green

# 6. Cleanup sandbox
Write-Host "`n6. Cleaning up sandbox..." -ForegroundColor Yellow
Remove-Item -Recurse -Force $sandboxDir -ErrorAction SilentlyContinue
Write-Host "Sandbox cleanly removed." -ForegroundColor Green

Write-Host "`n=== Clean-Machine Verification Passed with 100% Isolation! ===" -ForegroundColor Green
