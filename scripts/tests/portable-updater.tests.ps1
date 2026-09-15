$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot ('updater-test-' + [Guid]::NewGuid().ToString('N'))
$target = Join-Path $root 'installed'
$payload = Join-Path $root 'payload/MihonW'
New-Item -ItemType Directory -Path $target,$payload,(Join-Path $target 'data') -Force | Out-Null
Set-Content -LiteralPath (Join-Path $target '.portable') -Value ''
Set-Content -LiteralPath (Join-Path $target 'MihonW.exe') -Value 'old version'
Set-Content -LiteralPath (Join-Path $target 'data/keep.txt') -Value 'user data'
Set-Content -LiteralPath (Join-Path $payload 'MihonW.exe') -Value 'invalid executable triggers rollback'
$zip = Join-Path $root 'update.zip'
Compress-Archive -LiteralPath $payload -DestinationPath $zip
$updater = Join-Path $PSScriptRoot '../MihonUpdater.ps1'
try {
    $rejected = $false
    try { & $updater -ZipPath $zip -TargetDir $target -ExpectedSha256 ('0' * 64) -NoRestart } catch { $rejected = $_.Exception.Message -match 'SHA-256' }
    if (-not $rejected) { throw 'Bad checksum was not rejected' }
    $failed = $false
    try { & $updater -ZipPath $zip -TargetDir $target -ExpectedSha256 (Get-FileHash -LiteralPath $zip).Hash -NoRestart } catch { $failed = $true }
    if (-not $failed) { throw 'Invalid executable was accepted' }
    if ((Get-Content -LiteralPath (Join-Path $target 'MihonW.exe') -Raw).Trim() -ne 'old version') { throw 'Previous application was not restored' }
    if ((Get-Content -LiteralPath (Join-Path $target 'data/keep.txt') -Raw).Trim() -ne 'user data') { throw 'User data changed' }
    Write-Output 'PASS: checksum rejection, executable failure rollback, user data preservation'
} finally {
    $resolved = [IO.Path]::GetFullPath($root)
    if ([IO.Directory]::GetParent($resolved).FullName -ne [IO.Path]::GetFullPath($PSScriptRoot)) { throw 'Unsafe test cleanup' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

