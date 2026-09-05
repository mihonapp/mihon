$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$originalJavaHome = $env:JAVA_HOME

try {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    Write-Host "Running comprehensive downloads, task center, updates, and offline reader verification..."

    & .\gradlew.bat :desktop-app:test --tests "mihon.desktop.download.*" --tests "mihon.desktop.updates.*" --tests "mihon.desktop.notification.*" --tests "mihon.desktop.ui.tasks.*" --tests "mihon.desktop.ui.updates.*"
    if ($LASTEXITCODE -ne 0) {
        throw "Downloads and updates test suite failed with exit code $LASTEXITCODE"
    }

    Write-Host "================================================================="
    Write-Host "Desktop Downloads, Updates, and Task Center Verification SUCCESSFUL"
    Write-Host "================================================================="
} finally {
    $env:JAVA_HOME = $originalJavaHome
}
