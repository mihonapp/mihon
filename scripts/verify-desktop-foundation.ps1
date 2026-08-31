$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & .\gradlew.bat spotlessCheck :desktop-app:test :desktop-app:createDistributable
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle desktop foundation verification failed with exit code $LASTEXITCODE"
    }

    $launcher = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaged launcher was not created at $launcher"
    }

    $runtimeRelease = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\runtime\release'
    if (-not (Test-Path -LiteralPath $runtimeRelease)) {
        throw "Packaged Java runtime metadata was not created at $runtimeRelease"
    }
    $runtimeReleaseContents = Get-Content -Raw -LiteralPath $runtimeRelease
    $javaVersionMatch = [regex]::Match($runtimeReleaseContents, '(?m)^JAVA_VERSION="(?<version>[^"]+)"\r?$')
    if (-not $javaVersionMatch.Success) {
        throw "Packaged Java runtime metadata did not contain JAVA_VERSION"
    }
    $javaVersion = $javaVersionMatch.Groups['version'].Value
    if (-not [regex]::IsMatch($javaVersion, '^17(?:\.|$)')) {
        throw "Packaged Java runtime must be Java 17, but JAVA_VERSION was $javaVersion"
    }
    Write-Host "Packaged runtime JAVA_VERSION=$javaVersion"

    $smokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('mihon-w-smoke-' + [guid]::NewGuid().ToString('N'))
    $process = Start-Process -FilePath $launcher -ArgumentList @('--smoke-test', "--data-dir=$smokeRoot") -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Packaged launcher smoke test failed with exit code $($process.ExitCode)"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $smokeRoot 'cache'))) {
        throw "Packaged launcher did not initialize the explicit smoke-test data root"
    }

    Write-Host 'Mihon W desktop foundation verification passed.'
} finally {
    Pop-Location
}
