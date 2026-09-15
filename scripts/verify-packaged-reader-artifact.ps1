param(
    [Parameter(Mandatory)][string]$Launcher,
    [Parameter(Mandatory)][string]$FixtureRoot,
    [Parameter(Mandatory)][string]$OutputRoot
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Launcher = (Resolve-Path -LiteralPath $Launcher).Path
$FixtureRoot = (Resolve-Path -LiteralPath $FixtureRoot).Path
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
$profile = Join-Path $OutputRoot 'profile'
if (Test-Path -LiteralPath $profile) { throw 'Verification requires a fresh independent profile; existing evidence is preserved.' }
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$manifest = Join-Path $FixtureRoot 'reader-fixture-manifest.json'
$manifestHash = (Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash.ToLowerInvariant()
$appDirectory = Join-Path (Split-Path -Parent $Launcher) 'app'
$appJar = @(Get-ChildItem -LiteralPath $appDirectory -Filter 'desktop-app-*.jar' -File)
if ($appJar.Count -ne 1) { throw 'Expected exactly one packaged desktop application JAR.' }
$exeHash = (Get-FileHash -LiteralPath $Launcher -Algorithm SHA256).Hash
$jarHash = (Get-FileHash -LiteralPath $appJar[0].FullName -Algorithm SHA256).Hash
$releaseFile = Join-Path (Split-Path -Parent $Launcher) 'runtime/release'
$runtimeRelease = Get-Content -Raw -LiteralPath $releaseFile
if ($runtimeRelease -notmatch '(?m)^JAVA_VERSION="17(?:\.|\")') { throw 'Packaged runtime must be Java 17.' }
$phases = @('initial-open','reopen-continue','final-completion')
$records = [Collections.Generic.List[object]]::new()
$priorGate = $env:MIHON_W_READER_VERIFY
try {
    $env:MIHON_W_READER_VERIFY = '1'
    foreach ($phase in $phases) {
        $stdout = Join-Path $OutputRoot "$phase.stdout.log"
        $stderr = Join-Path $OutputRoot "$phase.stderr.log"
        if ($FixtureRoot.Contains('"') -or $profile.Contains('"')) { throw 'Quotes in verification paths are unsupported.' }
        $arguments = @(('"--verify-reader={0}"' -f $FixtureRoot), ('"--data-dir={0}"' -f $profile))
        $process = Start-Process -FilePath $Launcher -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        if (-not $process.WaitForExit(60000)) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            throw "Own reader verification process $($process.Id) timed out in phase $phase."
        }
        $process.Refresh()
        $text = Get-Content -Raw -LiteralPath $stdout
        $errorText = Get-Content -Raw -LiteralPath $stderr
        $records.Add([pscustomobject]@{ phase=$phase; processId=$process.Id; exitCode=$process.ExitCode; stdout=$stdout; stderr=$stderr })
        if ($process.ExitCode -ne 0) { throw "EXE phase $phase exited $($process.ExitCode): $text $errorText" }
        if ($errorText -match 'Exception in thread') { throw "EXE phase $phase emitted an uncaught asynchronous exception: $errorText" }
        $lines = @($text -split "`r?`n" | Where-Object { $_.Trim() })
        if ($lines.Count -ne 1) { throw "EXE phase $phase must emit one JSON line." }
        $summary = $lines[0] | ConvertFrom-Json
        if ($summary.command -cne 'verify-reader' -or $summary.status -cne 'SUCCEEDED' -or $summary.phase -cne $phase) { throw "Invalid command/phase status: $text" }
        if ($summary.fixtureManifestSha256 -cne $manifestHash) { throw 'Fixture manifest hash changed.' }
        if (($summary.verifiedAssets -join ',') -cne 'standalone,directory,cbz,cbt,cb7,cbr,epub') { throw 'Incomplete asset matrix.' }
        if (($summary.verifiedModes -join ',') -cne 'SINGLE_LTR,SINGLE_RTL,DUAL_LTR,DUAL_RTL,VERTICAL,WEBTOON') { throw 'Incomplete reading modes.' }
        if ([long]$summary.decodedTileCount -le 0 -or @($summary.gifFrameHashes | Sort-Object -Unique).Count -ne 2) { throw 'Tile or animation decoding missing.' }
        foreach ($property in @('coreResidentAndInFlightHighWaterBytes','cacheResidentHighWaterBytes')) {
            $amount = [long]$summary.$property
            if ($amount -le 0 -or $amount -gt 256MB) { throw "Reader budget exceeded: $property=$amount" }
        }
        $target = @($summary.progressRows | Where-Object chapterName -CEQ '01-directory')
        if ($target.Count -ne 1) { throw 'Expected one directory progress row.' }
        $expectedPage = switch ($phase) { 'initial-open' {1L}; 'reopen-continue' {2L}; default {[long]$target[0].pageCount - 1L} }
        if ([long]$target[0].pageIndex -ne $expectedPage -or [bool]$target[0].completed -ne ($phase -ceq 'final-completion')) { throw "Incorrect persistent progress in phase $phase." }
        Write-Output "$phase PID=$($process.Id) exit=$($process.ExitCode) tiles=$($summary.decodedTileCount) coreBytes=$($summary.coreResidentAndInFlightHighWaterBytes) cacheBytes=$($summary.cacheResidentHighWaterBytes)"
    }
    if (@($records.processId | Sort-Object -Unique).Count -ne 3) { throw 'Verification did not use three distinct EXE processes.' }
    if ((Get-FileHash -LiteralPath $Launcher -Algorithm SHA256).Hash -cne $exeHash -or (Get-FileHash -LiteralPath $appJar[0].FullName -Algorithm SHA256).Hash -cne $jarHash) { throw 'Packaged artifact changed during verification.' }
    [pscustomobject]@{ launcher=$Launcher; launcherSha256=$exeHash; appJar=$appJar[0].FullName; appJarSha256=$jarHash; runtimeRelease=$runtimeRelease; fixtureRoot=$FixtureRoot; fixtureManifestSha256=$manifestHash; processes=$records; status='SUCCEEDED' } |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputRoot 'packaged-reader-evidence.json') -Encoding utf8
} finally {
    $env:MIHON_W_READER_VERIFY = $priorGate
    $records | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputRoot 'process-exit-codes.json') -Encoding utf8
}
