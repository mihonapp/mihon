# Phase 7 Evidence: Settings, Backup Export, Diagnostics Bundle, and Accessibility
Date: 2026-09-06
Branch: `feat/windows-settings-backup-diagnostics`

## Overview
Phase 7 delivers a comprehensive desktop Settings hub, full Android-compatible ProtoBuf backup export (`.tachibk` gzipped) with contract-tested round-trip fidelity, headless backup export CLI commands, a diagnostics bundle generator (sanitized zip packaging database integrity checks, redacted logs, and host environment metrics), and complete Compose UI integration for Settings and About destinations.

## Implemented Deliverables

### 1. Database & Repository Export Queries
- **File**: `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq`
- **Queries Added**:
  - `selectAllMangaForExport`
  - `selectAllChaptersForExport`
  - `selectAllCategoriesForExport`
  - `selectAllMangaCategoriesForExport`
  - `selectAllHistoryForExport`
  - `selectAllTrackingForExport`
  - `selectAllSourcesForExport`
  - `selectAllPreferenceSnapshotsForExport`
  - `selectAllSourcePreferenceSnapshotsForExport`
- **Integrity Check**:
  - `LibraryRepository.checkIntegrity(): List<String>` executes SQLite `PRAGMA integrity_check`.
- **Verification**: `SqlDelightLibraryRepositoryTest` verified export query snapshots and `checkIntegrity()` returning `["ok"]`.

### 2. AndroidBackupExporter & Round-Trip Semantic Fidelity
- **Files**:
  - `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupCodec.kt` (added gzip ProtoBuf encoder)
  - `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupExporter.kt`
- **Round-Trip Test**: `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupRoundTripTest.kt`
  - Decodes original Android backup fixture.
  - Imports records into real SQLite desktop database.
  - Exports database state via `AndroidBackupExporter` into a new `.tachibk` archive.
  - Re-imports into a second clean database.
  - Verifies 100% preservation of manga titles, chapters, categories, reading history, tracking records, and preference snapshots.

### 3. Headless CLI Backup Export
- **Files**:
  - `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt`: Added `DesktopCommand.ExportBackup(path: Path)`
  - `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommandRunner.kt`: Executes export headless without launching GUI.
- **Verification**: `DesktopCommandTest` verifies `--export-backup=<path>` parsing and execution.

### 4. Diagnostic Bundle Service
- **Files**:
  - `desktop-app/src/main/kotlin/mihon/desktop/diagnostics/DiagnosticBundleService.kt`
  - `desktop-app/src/test/kotlin/mihon/desktop/diagnostics/DiagnosticBundleServiceTest.kt`
- **Features**:
  - Runs SQLite `PRAGMA integrity_check`.
  - Captures OS details, Java runtime vendor/version, Mihon version, and log statistics.
  - Packages recent logs into zip archive while redacting bearer tokens, cookies, authorization headers, and passwords.
  - Tested and verified in `DiagnosticBundleServiceTest`.

### 5. Compose Multiplatform Settings & About UI
- **Files**:
  - `desktop-app/src/main/kotlin/mihon/desktop/ui/settings/SettingsScreen.kt`: Material 3 navigation sidebar with General, Appearance, Reader, Downloads, Tracking, Backup & Restore, and Advanced/Diagnostics panes.
  - `desktop-app/src/main/kotlin/mihon/desktop/ui/settings/AboutScreen.kt`: Product metadata, architecture summary, and Apache 2.0 license.
  - `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt`: Wired `DesktopDestination.Settings` and `DesktopDestination.About`.
  - `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`: Export file dialog and state notification dialogs.
- **Verification**: `SettingsScreenTest` verifies section switching, theme persistence, and backup export/import button triggers.

## Verification Log
Executed via `scripts/verify-desktop-settings-backup.ps1`:
1. `spotlessCheck`: PASSED (0 lint violations)
2. `SqlDelightLibraryRepositoryTest` & `AndroidBackupRoundTripTest`: PASSED
3. `DiagnosticBundleServiceTest` & `DesktopCommandTest`: PASSED
4. `SettingsScreenTest`: PASSED
5. `:app:testDebugUnitTest :app:assembleDebug`: PASSED (clean build and all unit tests passed)
