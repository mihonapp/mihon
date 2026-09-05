$ErrorActionPreference = 'Stop'

Write-Host "=== Phase 7: Settings, Backup Export, Diagnostics, and Accessibility Verification ===" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

Write-Host "`n1. Running Spotless Check..." -ForegroundColor Yellow
.\gradlew.bat spotlessCheck
if ($LASTEXITCODE -ne 0) { throw "spotlessCheck failed" }

Write-Host "`n2. Running Repository & Backup Round-Trip Tests..." -ForegroundColor Yellow
.\gradlew.bat :desktop-library-data:test --tests "mihon.desktop.library.db.SqlDelightLibraryRepositoryTest" --tests "mihon.desktop.library.backup.AndroidBackupRoundTripTest"
if ($LASTEXITCODE -ne 0) { throw "desktop-library-data tests failed" }

Write-Host "`n3. Running Diagnostics & CLI Tests..." -ForegroundColor Yellow
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.diagnostics.DiagnosticBundleServiceTest" --tests "mihon.desktop.cli.DesktopCommandTest"
if ($LASTEXITCODE -ne 0) { throw "desktop-app diagnostics & CLI tests failed" }

Write-Host "`n4. Running Settings UI Tests..." -ForegroundColor Yellow
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.ui.settings.SettingsScreenTest"
if ($LASTEXITCODE -ne 0) { throw "desktop-app settings UI tests failed" }

Write-Host "`n5. Running Android Non-Regression Gates..." -ForegroundColor Yellow
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Android gates failed" }

Write-Host "`n=== Phase 7 Verification Succeeded Cleanly! ===" -ForegroundColor Green
