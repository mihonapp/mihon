param([Parameter(Mandatory)][string]$ImagePath)
$ErrorActionPreference = 'Stop'
$imageRoot = (Resolve-Path -LiteralPath $ImagePath).Path
foreach ($required in @('MihonW.exe', 'app', 'runtime')) {
    if (-not (Test-Path -LiteralPath (Join-Path $imageRoot $required))) {
        throw "Incomplete application image: missing $required"
    }
}
$unexpected = @(Get-ChildItem -LiteralPath $imageRoot -Force | Where-Object {
    $_.Name -notin @('MihonW.exe', 'app', 'runtime')
})
if ($unexpected.Count -gt 0) {
    throw "Release image contains unexpected root entries: $($unexpected.Name -join ', ')"
}
$profilePaths = '(^|/)(data|database|extensions|extension-host|logs|backups|covers|cache|cookies|media|trackers)(/|$)'
$profileFiles = '(^|/)(preferences\.properties|installed.*\.json|cookies.*\.json|.*\.(mext|apk|db|sqlite)(-wal|-shm)?)$'
$forbidden = @(Get-ChildItem -LiteralPath $imageRoot -Force -Recurse | Where-Object {
    $relative = $_.FullName.Substring($imageRoot.Length + 1).Replace('\', '/')
    $relative -match $profilePaths -or $relative -match $profileFiles
})
if ($forbidden.Count -gt 0) {
    throw "Release image contains profile or extension data: $($forbidden.FullName -join ', ')"
}
Write-Output 'PASS: application image contains no profile directories, installed extension packages or saved user configuration.'
