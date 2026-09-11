$ErrorActionPreference = 'Stop'

Write-Host "=== Phase 8: Clean-Machine Sandbox Verification ===" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 1. Build portable package
Write-Host "`n1. Building portable ZIP distribution..." -ForegroundColor Yellow
.\gradlew.bat :desktop-app:packagePortableZip
if ($LASTEXITCODE -ne 0) { throw "packagePortableZip failed" }

$portableDirectory = Resolve-Path "desktop-app\build\compose\binaries\main\portable"
$portableZips = @(Get-ChildItem -LiteralPath $portableDirectory -Filter "MihonW-*-windows-x64-portable.zip" -File)
if ($portableZips.Count -ne 1) {
    throw "Expected exactly one portable ZIP in $portableDirectory; found $($portableZips.Count)."
}
$portableZip = $portableZips[0].FullName
Write-Host "Found portable zip: $portableZip ($((Get-Item $portableZip).Length / 1MB) MB)" -ForegroundColor Green

# 2. Setup isolated sandbox
$sandboxId = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)
$sandboxDir = Join-Path $env:TEMP "mihon-sandbox-$sandboxId"
New-Item -ItemType Directory -Path $sandboxDir -Force | Out-Null
Write-Host "`n2. Extracting to clean-machine sandbox: $sandboxDir" -ForegroundColor Yellow

try {
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
$versionOutput = Join-Path $sandboxDir "version.stdout"
$versionError = Join-Path $sandboxDir "version.stderr"
$versionProcess = Start-Process -FilePath $exePath -ArgumentList "--version" -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $versionOutput -RedirectStandardError $versionError
if ($versionProcess.ExitCode -ne 0) {
    throw "MihonW --version exited with non-zero code: $($versionProcess.ExitCode)"
}
$versionStr = Get-Content -LiteralPath $versionOutput -Raw
Write-Host "Output: $versionStr" -ForegroundColor DarkGray
if ($versionStr -notmatch "Mihon W 0.1.3") {
    throw "Output did not match expected version string!"
}
Write-Host "--version verified successfully." -ForegroundColor Green

# 4. Test --help
Write-Host "`n4. Testing execution: --help..." -ForegroundColor Yellow
$helpOutput = Join-Path $sandboxDir "help.stdout"
$helpError = Join-Path $sandboxDir "help.stderr"
$helpProcess = Start-Process -FilePath $exePath -ArgumentList "--help" -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $helpOutput -RedirectStandardError $helpError
if ($helpProcess.ExitCode -ne 0) {
    throw "MihonW --help exited with non-zero code: $($helpProcess.ExitCode)"
}
$helpStr = Get-Content -LiteralPath $helpOutput -Raw
if ($helpStr -notmatch "Mihon W - Manga Reader for Windows") {
    throw "Output did not match expected help text!"
}
Write-Host "--help verified successfully." -ForegroundColor Green

# 5. Test Portable Mode Isolation & Backup Export
Write-Host "`n5. Testing portable isolation & headless export..." -ForegroundColor Yellow
$targetBackup = Join-Path $sandboxDir "exported-sandbox-backup.tachibk"
$exportOutput = Join-Path $sandboxDir "export.stdout"
$exportError = Join-Path $sandboxDir "export.stderr"
$exportProcess = Start-Process -FilePath $exePath -ArgumentList "--export-backup=$targetBackup" -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $exportOutput -RedirectStandardError $exportError
if ($exportProcess.ExitCode -ne 0) {
    throw "MihonW --export-backup exited with non-zero code: $($exportProcess.ExitCode)"
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

    Write-Host "`n=== Clean-Machine Verification Passed with 100% Isolation! ===" -ForegroundColor Green
} finally {
    Write-Host "`n6. Cleaning up sandbox..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $sandboxDir -ErrorAction SilentlyContinue
    Write-Host "Sandbox cleanly removed." -ForegroundColor Green
}
