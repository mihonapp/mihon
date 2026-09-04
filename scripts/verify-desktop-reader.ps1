$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$temporaryName = 'mihon-w-reader-' + [guid]::NewGuid().ToString('N')
$temporaryRoot = Join-Path $temporaryBase $temporaryName
$fixtureRoot = Join-Path $temporaryRoot 'fixture'
$dataRoot = Join-Path $temporaryRoot 'data'
$verificationParent = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'desktop-app\build\verification'))
$verificationLogRoot = [System.IO.Path]::GetFullPath((Join-Path $verificationParent 'reader'))
$originalJavaHome = $env:JAVA_HOME
$originalFixtureRoot = $env:MIHON_W_READER_FIXTURE_DIR
$originalVerifyGate = $env:MIHON_W_READER_VERIFY
$processRecords = [System.Collections.Generic.List[object]]::new()
$verificationPassed = $false

function Invoke-Gradle {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & .\gradlew.bat @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Quote-ProcessArgument {
    param([Parameter(Mandatory)][string]$Value)
    if ($Value.Contains('"')) {
        throw 'Process arguments containing quotes are not supported by this verifier.'
    }
    return '"' + $Value + '"'
}

function Invoke-PackagedReader {
    param(
        [Parameter(Mandatory)][string]$Launcher,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Name
    )

    $standardOutput = Join-Path $verificationLogRoot "$Name.stdout.log"
    $standardError = Join-Path $verificationLogRoot "$Name.stderr.log"
    $argumentsJson = ConvertTo-Json -Compress -InputObject @($Arguments)
    $argumentsBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($argumentsJson))
    $runner = Join-Path $temporaryRoot 'packaged-reader-runner.ps1'
    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    $wrapperArguments = @(
        '-NoProfile',
        '-File',
        (Quote-ProcessArgument $runner),
        '-Launcher',
        (Quote-ProcessArgument $Launcher),
        '-StandardOutput',
        (Quote-ProcessArgument $standardOutput),
        '-StandardError',
        (Quote-ProcessArgument $standardError),
        '-ArgumentsBase64',
        (Quote-ProcessArgument $argumentsBase64)
    )
    $wrapper = Start-Process -FilePath $pwsh -ArgumentList $wrapperArguments -WindowStyle Hidden -PassThru
    if (-not $wrapper.WaitForExit(90 * 1000)) {
        $processRecords.Add([pscustomobject]@{ name = $Name; exitCode = -1; timedOut = $true })
        & taskkill.exe /PID $wrapper.Id /T /F 2>&1 | Out-Null
        if (-not $wrapper.WaitForExit(5 * 1000)) {
            throw "Packaged reader '$Name' timed out after 90 seconds and its process tree retained handles."
        }
        throw "Packaged reader '$Name' timed out after 90 seconds and was terminated."
    }
    $processRecords.Add([pscustomobject]@{ name = $Name; exitCode = $wrapper.ExitCode; timedOut = $false })
    if (-not (Test-Path -LiteralPath $standardOutput)) {
        throw "Packaged reader '$Name' did not create stdout evidence."
    }
    $text = Get-Content -Raw -Encoding utf8 -LiteralPath $standardOutput
    if ($wrapper.ExitCode -ne 0) {
        $errorText = if (Test-Path -LiteralPath $standardError) {
            Get-Content -Raw -Encoding utf8 -LiteralPath $standardError
        } else {
            '<no stderr file>'
        }
        throw "Packaged reader '$Name' failed with exit code $($wrapper.ExitCode). stdout: $text stderr: $errorText"
    }
    return [pscustomobject]@{
        ExitCode = $wrapper.ExitCode
        StandardOutput = $standardOutput
        StandardError = $standardError
        Text = $text
    }
}

function Read-OneJsonLine {
    param(
        [Parameter(Mandatory)][pscustomobject]$Result,
        [Parameter(Mandatory)][string]$Name
    )

    $lines = @($Result.Text -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 })
    if ($lines.Count -ne 1) {
        throw "Packaged reader '$Name' must emit exactly one nonempty JSON line, but emitted $($lines.Count)."
    }
    try {
        return $lines[0] | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Packaged reader '$Name' emitted invalid JSON: $($lines[0])"
    }
}

function Assert-ReaderSummary {
    param(
        [Parameter(Mandatory)][pscustomobject]$Summary,
        [Parameter(Mandatory)][string]$Phase,
        [Parameter(Mandatory)][long]$ExpectedPage,
        [Parameter(Mandatory)][bool]$ExpectedCompleted,
        [Parameter(Mandatory)][string]$ExpectedManifestSha256
    )

    if ($Summary.command -cne 'verify-reader' -or $Summary.status -cne 'SUCCEEDED') {
        throw "Reader phase '$Phase' did not report a successful verify-reader command."
    }
    if ($Summary.phase -cne $Phase) {
        throw "Reader phase mismatch: expected '$Phase' but was '$($Summary.phase)'."
    }
    if ($Summary.fixtureManifestSha256 -cne $ExpectedManifestSha256) {
        throw "Reader phase '$Phase' used a different fixture manifest."
    }
    $expectedAssets = 'standalone,directory,cbz,cbt,cb7,cbr,epub'
    if ((@($Summary.verifiedAssets) -join ',') -cne $expectedAssets) {
        throw "Reader phase '$Phase' did not verify the complete asset matrix."
    }
    $expectedModes = 'SINGLE_LTR,SINGLE_RTL,DUAL_LTR,DUAL_RTL,VERTICAL,WEBTOON'
    if ((@($Summary.verifiedModes) -join ',') -cne $expectedModes) {
        throw "Reader phase '$Phase' did not verify all six reading modes."
    }
    if ([long]$Summary.decodedTileCount -le 0) {
        throw "Reader phase '$Phase' did not decode a visible tile."
    }
    if (@($Summary.gifFrameHashes | Sort-Object -Unique).Count -ne 2) {
        throw "Reader phase '$Phase' did not sample two distinct GIF frame hashes."
    }
    $coreHighWater = [long]$Summary.coreResidentAndInFlightHighWaterBytes
    if ($coreHighWater -le 0 -or $coreHighWater -gt (256L * 1024L * 1024L)) {
        throw "Reader phase '$Phase' core resident+in-flight high-water is outside 256 MiB."
    }
    $cacheHighWater = [long]$Summary.cacheResidentHighWaterBytes
    if ($cacheHighWater -le 0 -or $cacheHighWater -gt (256L * 1024L * 1024L)) {
        throw "Reader phase '$Phase' cache resident high-water is outside 256 MiB."
    }
    $target = @($Summary.progressRows | Where-Object { $_.chapterName -ceq '01-directory' })
    if ($target.Count -ne 1) {
        throw "Reader phase '$Phase' did not report exactly one progress row for 01-directory."
    }
    if ([long]$target[0].pageIndex -ne $ExpectedPage -or [bool]$target[0].completed -ne $ExpectedCompleted) {
        throw "Reader phase '$Phase' persisted unexpected progress for 01-directory."
    }
    if ($ExpectedCompleted -and [long]$target[0].pageIndex -ne ([long]$target[0].pageCount - 1L)) {
        throw "Reader final phase did not complete on the final page."
    }
}

function Get-TestCounts {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Directory
    )

    $counts = [ordered]@{ name = $Name; tests = 0; failures = 0; errors = 0; skipped = 0 }
    $files = @(Get-ChildItem -File -Filter 'TEST-*.xml' -LiteralPath $Directory -ErrorAction Stop)
    foreach ($file in $files) {
        [xml]$document = Get-Content -Raw -LiteralPath $file.FullName
        $counts.tests += [int]$document.testsuite.tests
        $counts.failures += [int]$document.testsuite.failures
        $counts.errors += [int]$document.testsuite.errors
        $counts.skipped += [int]$document.testsuite.skipped
    }
    if ($counts.failures -ne 0 -or $counts.errors -ne 0) {
        throw "Test results for '$Name' contain failures or errors."
    }
    return [pscustomobject]$counts
}

Push-Location $repoRoot
try {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    $javaVersionOutput = if (Get-Command java -ErrorAction SilentlyContinue) { (& java -version 2>&1) -join "`n" } else { '' }
    $javaMajorMatch = [regex]::Match($javaVersionOutput, 'version "(?<major>\d+)')
    if (-not $javaMajorMatch.Success -or [int]$javaMajorMatch.Groups['major'].Value -lt 21) {
        if (-not (Test-Path -LiteralPath (Join-Path $jbr 'bin\java.exe'))) {
            throw 'Gradle requires JDK 21 or newer, and Android Studio JBR was not found.'
        }
        $env:JAVA_HOME = $jbr
        Write-Host "Using Gradle JDK at $jbr"
    }

    if (Test-Path -LiteralPath $verificationLogRoot) {
        $resolvedLogRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $verificationLogRoot).Path)
        if ((Split-Path -Parent $resolvedLogRoot) -cne $verificationParent -or
            (Split-Path -Leaf $resolvedLogRoot) -cne 'reader') {
            throw "Refusing to replace unexpected verification log path: $resolvedLogRoot"
        }
        Remove-Item -LiteralPath $resolvedLogRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $verificationLogRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $temporaryRoot -ErrorAction Stop | Out-Null

    $runnerSource = @'
param(
    [Parameter(Mandatory)][string]$Launcher,
    [Parameter(Mandatory)][string]$StandardOutput,
    [Parameter(Mandatory)][string]$StandardError,
    [Parameter(Mandatory)][string]$ArgumentsBase64
)
$ErrorActionPreference = 'Stop'
$json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ArgumentsBase64))
$commandArguments = @($json | ConvertFrom-Json)
$quotedArguments = @($commandArguments | ForEach-Object { '"' + ([string]$_).Replace('"', '\"') + '"' })
$process = Start-Process -FilePath $Launcher -ArgumentList $quotedArguments -WindowStyle Hidden -Wait -PassThru `
    -RedirectStandardOutput $StandardOutput -RedirectStandardError $StandardError
exit $process.ExitCode
'@
    Set-Content -LiteralPath (Join-Path $temporaryRoot 'packaged-reader-runner.ps1') -Value $runnerSource -Encoding utf8

    & .\scripts\verify-desktop-foundation.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "Desktop foundation verifier failed with exit code $LASTEXITCODE"
    }

    $env:MIHON_W_READER_FIXTURE_DIR = $fixtureRoot
    $env:MIHON_W_READER_VERIFY = '1'

    Invoke-Gradle @(
        ':reader-core:test',
        ':reader-core:extremeImageTest',
        ':desktop-library-data:test',
        ':desktop-app:test',
        'verifySqlDelightMigration',
        'spotlessCheck',
        ':desktop-app:packageDistributionForCurrentOS',
        '--console=plain',
        '--rerun-tasks'
    )

    $testCounts = @(
        Get-TestCounts 'reader-core:test' (Join-Path $repoRoot 'reader-core\build\test-results\test')
        Get-TestCounts 'reader-core:extremeImageTest' (Join-Path $repoRoot 'reader-core\build\test-results\extremeImageTest')
        Get-TestCounts 'desktop-library-data:test' (Join-Path $repoRoot 'desktop-library-data\build\test-results\test')
        Get-TestCounts 'desktop-app:test' (Join-Path $repoRoot 'desktop-app\build\test-results\test')
    )
    $testCounts | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $verificationLogRoot 'test-counts.json') -Encoding utf8

    $extremeResults = Get-ChildItem -File -Filter 'TEST-*.xml' `
        -LiteralPath (Join-Path $repoRoot 'reader-core\build\test-results\extremeImageTest')
    $heapAssertion = $extremeResults | Select-String -SimpleMatch 'extreme image tests run on a heap constrained to 384 mib'
    if (@($heapAssertion).Count -ne 1) {
        throw 'Extreme-image results did not contain the exact 384 MiB maxMemory assertion.'
    }
    [pscustomobject]@{
        maxMemoryBytes = 384L * 1024L * 1024L
        sourceAssertion = 'Runtime.getRuntime().maxMemory() == 384 MiB'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $verificationLogRoot 'extreme-worker-memory.json') -Encoding utf8

    $manifestPath = Join-Path $fixtureRoot 'reader-fixture-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw "Reader fixture writer did not create $manifestPath"
    }
    $manifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
    if ($manifestSha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw 'Reader fixture manifest SHA-256 is invalid.'
    }
    Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $verificationLogRoot 'reader-fixture-manifest.json')

    $launcher = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaged launcher was not created at $launcher"
    }
    $runtimeRelease = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\runtime\release'
    $runtimeReleaseContents = Get-Content -Raw -LiteralPath $runtimeRelease
    $javaVersionMatch = [regex]::Match($runtimeReleaseContents, '(?m)^JAVA_VERSION="(?<version>[^"]+)"\r?$')
    if (-not $javaVersionMatch.Success -or -not [regex]::IsMatch($javaVersionMatch.Groups['version'].Value, '^17(?:\.|$)')) {
        throw 'Packaged reader runtime is not Java 17.'
    }
    $launcherSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $launcher).Hash.ToLowerInvariant()
    [pscustomobject]@{
        path = $launcher
        sha256 = $launcherSha256
        javaVersion = $javaVersionMatch.Groups['version'].Value
        fixtureManifestSha256 = $manifestSha256
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $verificationLogRoot 'packaged-artifact.json') -Encoding utf8

    $initialResult = Invoke-PackagedReader $launcher @("--verify-reader=$fixtureRoot", "--data-dir=$dataRoot") '01-initial-open'
    $initial = Read-OneJsonLine $initialResult '01-initial-open'
    Assert-ReaderSummary $initial 'initial-open' 1 $false $manifestSha256
    $database = Join-Path $dataRoot 'database\library.db'
    if (-not (Test-Path -LiteralPath $database) -or (Get-Item -LiteralPath $database).Length -le 0) {
        throw 'Initial packaged process did not create a nonempty reader database.'
    }

    $continueResult = Invoke-PackagedReader $launcher @("--verify-reader=$fixtureRoot", "--data-dir=$dataRoot") '02-reopen-continue'
    $continued = Read-OneJsonLine $continueResult '02-reopen-continue'
    Assert-ReaderSummary $continued 'reopen-continue' 2 $false $manifestSha256

    $finalResult = Invoke-PackagedReader $launcher @("--verify-reader=$fixtureRoot", "--data-dir=$dataRoot") '03-final-completion'
    $completed = Read-OneJsonLine $finalResult '03-final-completion'
    $finalTarget = @($completed.progressRows | Where-Object { $_.chapterName -ceq '01-directory' })
    if ($finalTarget.Count -ne 1) {
        throw 'Final packaged process did not expose the target database progress row.'
    }
    Assert-ReaderSummary $completed 'final-completion' ([long]$finalTarget[0].pageCount - 1L) $true $manifestSha256

    @(
        [pscustomobject]@{ phase = 'initial-open'; rows = $initial.progressRows },
        [pscustomobject]@{ phase = 'reopen-continue'; rows = $continued.progressRows },
        [pscustomobject]@{ phase = 'final-completion'; rows = $completed.progressRows }
    ) | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $verificationLogRoot 'progress-rows.json') -Encoding utf8
    $verificationPassed = $true
} finally {
    $processRecords | ConvertTo-Json -Depth 4 | Set-Content `
        -LiteralPath (Join-Path $verificationLogRoot 'process-exit-codes.json') -Encoding utf8 -ErrorAction SilentlyContinue
    Pop-Location
    if ($null -eq $originalJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    if ($null -eq $originalFixtureRoot) {
        Remove-Item Env:MIHON_W_READER_FIXTURE_DIR -ErrorAction SilentlyContinue
    } else {
        $env:MIHON_W_READER_FIXTURE_DIR = $originalFixtureRoot
    }
    if ($null -eq $originalVerifyGate) {
        Remove-Item Env:MIHON_W_READER_VERIFY -ErrorAction SilentlyContinue
    } else {
        $env:MIHON_W_READER_VERIFY = $originalVerifyGate
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $temporaryRoot).Path)
        $resolvedTemporaryBase = [System.IO.Path]::GetFullPath($temporaryBase)
        if (
            (Split-Path -Parent $resolvedTemporaryRoot).TrimEnd('\', '/') -ne $resolvedTemporaryBase.TrimEnd('\', '/') -or
            (Split-Path -Leaf $resolvedTemporaryRoot) -cnotmatch '^mihon-w-reader-[0-9a-f]{32}$'
        ) {
            throw "Refusing to delete unexpected reader verification path: $resolvedTemporaryRoot"
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}

if (-not $verificationPassed) {
    throw 'Mihon W desktop reader verification did not reach its completion gate.'
}
if (Test-Path -LiteralPath $temporaryRoot) {
    throw "Reader verifier root still exists after cleanup: $temporaryRoot"
}
Write-Host 'Mihon W desktop reader verification passed.'
