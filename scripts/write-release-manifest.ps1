param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $Directory).Path
$manifestPath = Join-Path $releaseRoot 'SHA256SUMS.txt'
$lines = Get-ChildItem -LiteralPath $releaseRoot -Recurse -File | Where-Object { $_.FullName -ne $manifestPath } | Sort-Object FullName | ForEach-Object {
    $relative = [IO.Path]::GetRelativePath($releaseRoot, $_.FullName).Replace('\', '/')
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
}
[IO.File]::WriteAllLines($manifestPath, $lines, [Text.UTF8Encoding]::new($false))
Write-Output $manifestPath
