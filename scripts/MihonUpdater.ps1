param(
    [Parameter(Mandatory = $false)]
    [int]$CallerPid = 0,

    [Parameter(Mandatory = $true)]
    [string]$ZipPath,

    [Parameter(Mandatory = $true)]
    [string]$TargetDir,

    [Parameter(Mandatory = $false)]
    [string]$ExecutableName = "MihonW.exe"
)

$ErrorActionPreference = 'Stop'

Write-Host "=== Mihon W Portable Updater ===" -ForegroundColor Cyan

# 1. Wait for caller process to exit
if ($CallerPid -gt 0) {
    Write-Host "Waiting for process $CallerPid to exit..." -ForegroundColor Yellow
    try {
        Wait-Process -Id $CallerPid -Timeout 30 -ErrorAction SilentlyContinue
    } catch {
        Write-Host "Caller process $CallerPid finished or not found." -ForegroundColor DarkGray
    }
}

# 2. Check input archive
if (-not (Test-Path $ZipPath)) {
    Write-Error "Update package not found at: $ZipPath"
    exit 1
}

$targetResolved = (Resolve-Path $TargetDir).Path
$backupDir = "$targetResolved.backup"

Write-Host "Creating safety backup at: $backupDir" -ForegroundColor Yellow
if (Test-Path $backupDir) {
    Remove-Item -Recurse -Force $backupDir
}
Copy-Item -Path $targetResolved -Destination $backupDir -Recurse -Force

# 3. Extract update
$tempExtract = Join-Path $env:TEMP "mihon-update-$([System.Guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null

try {
    Write-Host "Extracting update package..." -ForegroundColor Yellow
    Expand-Archive -Path $ZipPath -DestinationPath $tempExtract -Force

    # Find the app folder inside archive (either root or top-level MihonW folder)
    $sourceAppDir = $tempExtract
    $nestedMihon = Join-Path $tempExtract "MihonW"
    if ((Test-Path (Join-Path $nestedMihon $ExecutableName))) {
        $sourceAppDir = $nestedMihon
    }

    Write-Host "Updating application files in $targetResolved..." -ForegroundColor Yellow
    # Overwrite files while preserving data folder
    Get-ChildItem -Path $sourceAppDir | ForEach-Object {
        if ($_.Name -ne "data" -and $_.Name -ne ".portable") {
            $destItem = Join-Path $targetResolved $_.Name
            if (Test-Path $destItem) {
                Remove-Item -Recurse -Force $destItem
            }
            Copy-Item -Path $_.FullName -Destination $targetResolved -Recurse -Force
        }
    }

    # Ensure .portable marker remains
    $portableMarker = Join-Path $targetResolved ".portable"
    if (-not (Test-Path $portableMarker)) {
        New-Item -ItemType File -Path $portableMarker -Force | Out-Null
    }

    # 4. Validate integrity
    $exePath = Join-Path $targetResolved $ExecutableName
    if (-not (Test-Path $exePath)) {
        throw "Validation error: $ExecutableName not found in target directory after extraction!"
    }

    Write-Host "Integrity verification passed." -ForegroundColor Green
    # Remove backup on success
    Remove-Item -Recurse -Force $backupDir -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $tempExtract -ErrorAction SilentlyContinue

    Write-Host "Update completed successfully! Restarting $ExecutableName..." -ForegroundColor Green
    Start-Process -FilePath $exePath
    exit 0

} catch {
    Write-Warning "Update failed: $_"
    Write-Host "Performing automatic rollback from $backupDir..." -ForegroundColor Red

    try {
        Get-ChildItem -Path $targetResolved | Where-Object { $_.Name -ne "data" } | Remove-Item -Recurse -Force
        Get-ChildItem -Path $backupDir | Where-Object { $_.Name -ne "data" } | Copy-Item -Destination $targetResolved -Recurse -Force
        Write-Host "Rollback successful. Previous version restored." -ForegroundColor Green
    } catch {
        Write-Error "Critical: Automatic rollback failed: $_"
    } finally {
        Remove-Item -Recurse -Force $tempExtract -ErrorAction SilentlyContinue
    }

    exit 1
}
