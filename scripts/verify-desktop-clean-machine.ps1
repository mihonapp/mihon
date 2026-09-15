param([string]$PortableZip, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'

Write-Host "=== Local isolated-directory verification; not a clean Windows machine ===" -ForegroundColor Cyan

$expectedVersion = (Get-Content -LiteralPath (Join-Path $PSScriptRoot '../desktop-version.txt') -Raw).Trim()

# 1. Build portable package
Write-Host "`n1. Building portable ZIP distribution..." -ForegroundColor Yellow
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot '../.superpowers/sdd/run-gradle.ps1') -GradleArguments @(':desktop-app:packagePortableZip')
    if ($LASTEXITCODE -ne 0) { throw 'packagePortableZip failed' }
}
if (-not $PortableZip) {
    $PortableZip = Join-Path $PSScriptRoot "../desktop-app/build/compose/binaries/main/portable/mihondesk-$expectedVersion-windows-x64-portable.zip"
}
$portableZip = (Resolve-Path -LiteralPath $PortableZip).Path
Write-Host "Found portable zip: $portableZip ($((Get-Item $portableZip).Length / 1MB) MB)" -ForegroundColor Green

# 2. Setup isolated sandbox
$sandboxId = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)
$sandboxDir = Join-Path $env:TEMP "mihon-sandbox-$sandboxId"
New-Item -ItemType Directory -Path $sandboxDir -Force | Out-Null
Write-Host "`n2. Extracting to clean-machine sandbox: $sandboxDir" -ForegroundColor Yellow

try {
    Expand-Archive -Path $portableZip -DestinationPath $sandboxDir -Force

$appDir = Join-Path $sandboxDir "mihondesk"
$exePath = Join-Path $appDir "mihondesk.exe"
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
    throw "mihondesk --version exited with non-zero code: $($versionProcess.ExitCode)"
}
$versionStr = Get-Content -LiteralPath $versionOutput -Raw
Write-Host "Output: $versionStr" -ForegroundColor DarkGray
if ($versionStr -notmatch [regex]::Escape("mihondesk $expectedVersion")) {
    throw "Output did not match expected version string!"
}
Write-Host "--version verified successfully." -ForegroundColor Green

# 4. Test --help
Write-Host "`n4. Testing execution: --help..." -ForegroundColor Yellow
$helpOutput = Join-Path $sandboxDir "help.stdout"
$helpError = Join-Path $sandboxDir "help.stderr"
$helpProcess = Start-Process -FilePath $exePath -ArgumentList "--help" -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $helpOutput -RedirectStandardError $helpError
if ($helpProcess.ExitCode -ne 0) {
    throw "mihondesk --help exited with non-zero code: $($helpProcess.ExitCode)"
}
$helpStr = Get-Content -LiteralPath $helpOutput -Raw
if ($helpStr -notmatch "mihondesk - Manga Reader for Windows") {
    throw "Output did not match expected help text!"
}
Write-Host "--help verified successfully." -ForegroundColor Green

# 5. Test Portable Mode Isolation & Backup Export
Write-Host "`n5. Testing portable isolation & headless export..." -ForegroundColor Yellow
$targetBackup = Join-Path $sandboxDir "exported-sandbox-backup.tachibk"
$exportOutput = Join-Path $sandboxDir "export.stdout"
$exportError = Join-Path $sandboxDir "export.stderr"
$exportProcess = Start-Process -FilePath $exePath -ArgumentList ('"--export-backup={0}"' -f $targetBackup) -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $exportOutput -RedirectStandardError $exportError
if ($exportProcess.ExitCode -ne 0) {
    throw "mihondesk --export-backup exited with non-zero code: $($exportProcess.ExitCode)"
}

# Verify data was placed inside sandbox\mihondesk\data and NOT polluting APPDATA
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

    Write-Host "`n=== Local isolated-directory checks passed; Win10/Win11 clean-machine acceptance remains separate ===" -ForegroundColor Green
} finally {
    Write-Host "`n6. Cleaning up sandbox..." -ForegroundColor Yellow
    $resolvedSandbox = [IO.Path]::GetFullPath($sandboxDir)
    $tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    if (-not $resolvedSandbox.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unsafe sandbox cleanup path'
    }
    Remove-Item -LiteralPath $resolvedSandbox -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Sandbox cleanly removed." -ForegroundColor Green
}
