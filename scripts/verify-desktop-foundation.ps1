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
