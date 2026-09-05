$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$originalJavaHome = $env:JAVA_HOME

try {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    Write-Host "Running comprehensive extension integration and E2E verification..."

    & .\gradlew.bat :extension-sdk:test :extension-host:test :desktop-app:test --tests "mihon.desktop.extension.*" --tests "mihon.desktop.ui.browse.*"
    if ($LASTEXITCODE -ne 0) {
        throw "Extension test suite failed with exit code $LASTEXITCODE"
    }

    Write-Host "=========================================="
    Write-Host "Desktop Extensions Verification SUCCESSFUL"
    Write-Host "=========================================="
} finally {
    $env:JAVA_HOME = $originalJavaHome
}