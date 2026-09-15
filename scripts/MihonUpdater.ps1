[CmdletBinding()]
param(
    [int]$CallerPid = 0,
    [Parameter(Mandatory)][string]$ZipPath,
    [Parameter(Mandatory)][string]$TargetDir,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedSha256,
    [string]$ExecutableName = 'MihonW.exe',
    [switch]$NoRestart
)
$ErrorActionPreference = 'Stop'
if ($ExecutableName -ne [IO.Path]::GetFileName($ExecutableName)) { throw 'ExecutableName must be a filename' }
$targetResolved = (Resolve-Path -LiteralPath $TargetDir).Path.TrimEnd('\')
$targetParent = [IO.Directory]::GetParent($targetResolved).FullName
if (-not $targetParent -or -not (Test-Path -LiteralPath (Join-Path $targetResolved '.portable'))) {
    throw 'Update target must be an existing portable application directory'
}
$archive = (Resolve-Path -LiteralPath $ZipPath).Path
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'Update SHA-256 mismatch' }
if ($CallerPid -gt 0) {
    $caller = Get-Process -Id $CallerPid -ErrorAction SilentlyContinue
    if ($caller -and -not $caller.WaitForExit(30000)) { throw 'Application is still running; update aborted' }
}
$operationId = [Guid]::NewGuid().ToString('N')
$stage = Join-Path $targetParent ".mihon-stage-$operationId"
$backup = Join-Path $targetParent ".mihon-rollback-$operationId"
$swapped = $false
try {
    New-Item -ItemType Directory -Path $stage | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $stage
    $candidate = Join-Path $stage 'MihonW'
    if (-not (Test-Path -LiteralPath (Join-Path $candidate $ExecutableName))) { throw 'Update executable missing' }
    if (Test-Path -LiteralPath (Join-Path $candidate 'data')) { throw 'Update archive must not supply user data' }
    $existingData = Join-Path $targetResolved 'data'
    if (Test-Path -LiteralPath $existingData) { Copy-Item -LiteralPath $existingData -Destination $candidate -Recurse }
    New-Item -ItemType File -Path (Join-Path $candidate '.portable') -Force | Out-Null
    # Same-volume directory renames keep the previous installation available for rollback.
    Move-Item -LiteralPath $targetResolved -Destination $backup
    try { Move-Item -LiteralPath $candidate -Destination $targetResolved; $swapped = $true }
    catch { Move-Item -LiteralPath $backup -Destination $targetResolved; throw }
    $probe = Start-Process -FilePath (Join-Path $targetResolved $ExecutableName) -ArgumentList '--version' -PassThru -WindowStyle Hidden
    if (-not $probe.WaitForExit(30000)) { $probe.Kill(); throw 'Updated executable validation timed out' }
    if ($probe.ExitCode -ne 0) { throw 'Updated executable failed validation' }
    Write-Output "Update installed. Previous version retained at $backup"
    if (-not $NoRestart) { Start-Process -FilePath (Join-Path $targetResolved $ExecutableName) -WindowStyle Hidden }
} catch {
    if ($swapped) {
        Move-Item -LiteralPath $targetResolved -Destination (Join-Path $stage 'failed-installation')
        Move-Item -LiteralPath $backup -Destination $targetResolved
    }
    throw
} finally {
    $resolvedStage = [IO.Path]::GetFullPath($stage)
    if ([IO.Directory]::GetParent($resolvedStage).FullName -ne $targetParent -or [IO.Path]::GetFileName($resolvedStage) -ne ".mihon-stage-$operationId") {
        throw 'Unsafe stage cleanup path'
    }
    if (Test-Path -LiteralPath $resolvedStage) { Remove-Item -LiteralPath $resolvedStage -Recurse -Force }
}
