[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  mihondesk Windows - Phase 6 Trackers & History Verify  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

Write-Host "[1/4] Running desktop-library-data tests (Categories, History, Tracking)..." -ForegroundColor Yellow
& "$repoRoot\gradlew.bat" :desktop-library-data:test
if ($LASTEXITCODE -ne 0) { throw "desktop-library-data tests failed" }

Write-Host "[2/4] Running desktop-app HistoryScreenTest..." -ForegroundColor Yellow
& "$repoRoot\gradlew.bat" :desktop-app:test --tests "mihon.desktop.ui.history.HistoryScreenTest"
if ($LASTEXITCODE -ne 0) { throw "HistoryScreenTest failed" }

Write-Host "[3/4] Running desktop-app LibraryCategoryFilterTest..." -ForegroundColor Yellow
& "$repoRoot\gradlew.bat" :desktop-app:test --tests "mihon.desktop.ui.library.LibraryCategoryFilterTest"
if ($LASTEXITCODE -ne 0) { throw "LibraryCategoryFilterTest failed" }

Write-Host "[4/4] Running desktop-app TrackerAndQueueTest..." -ForegroundColor Yellow
& "$repoRoot\gradlew.bat" :desktop-app:test --tests "mihon.desktop.track.TrackerAndQueueTest"
if ($LASTEXITCODE -ne 0) { throw "TrackerAndQueueTest failed" }

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  Phase 6 Trackers, Categories & History Verified Clean!  " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
