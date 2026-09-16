param([string]$MagickPath = 'magick')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'desktop-app/src/main/resources/icon.svg'
$resources = Join-Path $root 'desktop-app/src/main/resources'
$assets = Join-Path $root '.github/assets'

function Invoke-Magick([string[]]$Arguments) {
    & $MagickPath @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'ImageMagick could not export the brand asset' }
}

# Rasterize from the editable source at high resolution, then downsample.
Invoke-Magick @('-background', 'none', '-density', '384', $source, '-resize', '512x512', (Join-Path $resources 'icon.png'))
Invoke-Magick @((Join-Path $resources 'icon.png'), '-define', 'icon:auto-resize=256,128,96,64,48,32,24,16', (Join-Path $resources 'icon.ico'))
Copy-Item -LiteralPath (Join-Path $resources 'icon.png') -Destination (Join-Path $assets 'logo.png') -Force
$bannerPath = Join-Path $assets 'banner.svg'
$nestedIcon = (Get-Content -LiteralPath $source -Raw) -replace '^<svg[^>]+>', '<svg x="840" y="44" width="340" height="340" viewBox="0 0 512 512" fill="none">'
$banner = Get-Content -LiteralPath $bannerPath -Raw
$iconPattern = '(?s)<svg x="840".*?</svg>'
if ($banner -notmatch $iconPattern) { throw 'The banner icon slot is missing' }
$banner = [regex]::Replace($banner, $iconPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $nestedIcon.TrimEnd() })
[IO.File]::WriteAllText($bannerPath, $banner, [Text.UTF8Encoding]::new($false))
Invoke-Magick @('-background', 'none', '-density', '96', $bannerPath, (Join-Path $assets 'banner.png'))
Invoke-Magick @((Join-Path $assets 'banner.png'), '-background', '#0C1632', '-gravity', 'center', '-extent', '1280x640', (Join-Path $assets 'social-preview.png'))
Write-Output 'Exported Windows PNG/ICO, GitHub logo, and repository banner.'
