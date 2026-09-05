# Windows Settings, Backup Export, Diagnostics, and Accessibility Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver Phase 7 of the Mihon Windows Port: comprehensive Settings interface (General, Appearance, Library, Reader, Downloads, Tracking, Backup & Restore, Advanced/Diagnostics), full Android-compatible Backup Export (`.tachibk` gzip ProtoBuf encoder) with round-trip verification, diagnostic bundle generation (redacted logs, DB integrity check, OS/environment details), keyboard accessibility traversal, and CLI backup export support.

**Architecture:**
- `desktop-library-data`: 
  - `AndroidBackupExporter.kt`: Encodes library, categories, chapters, history, tracking, sources, and preferences into valid Android `Backup` protobuf format (`.tachibk` gzipped).
  - Snapshot export queries in `Library.sq` and repository interfaces.
- `desktop-app/src/main/kotlin/mihon/desktop/diagnostics`:
  - `DiagnosticBundleService.kt`: Gathers database integrity check (`PRAGMA integrity_check`), environment/OS info, installed extensions metadata, redacted logs, and packages into a ZIP bundle.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/settings`:
  - `SettingsScreen.kt`: Navigation rail / master-detail settings sections:
    - **General**: Locale, startup destination.
    - **Appearance**: Theme mode (Light, Dark, System), color scheme.
    - **Library**: Default category, display/grid preferences, update frequency.
    - **Reader**: Default reading mode, scale mode, click zones, wheel behavior.
    - **Downloads**: Download location, concurrency limits.
    - **Tracking**: Logged-in tracker accounts and status.
    - **Backup & Restore**: Import backup, Export backup, and Export path selector.
    - **Advanced & Diagnostics**: Database integrity check, export diagnostic bundle, clear caches.
  - `AboutScreen.kt`: Version, commit hash, platform runtime info, licenses.
- `desktop-app/src/main/kotlin/mihon/desktop/cli`:
  - `DesktopCommand.ExportBackup(val path: Path)` CLI command and runner integration.

**Tech Stack:** Kotlin/JVM 2.4.10, Java 17/21, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, Compose Multiplatform Desktop 1.12.0, SQLDelight 2.0.2, JUnit 5/6, Kotest.

---

## Fixed scope and invariants

- [x] `AndroidBackupExporter` encodes all current database library state into valid Android ProtoBuf `.tachibk` file (gzip compressed).
- [x] Round-trip verification: Android backup -> Import into Desktop -> Export from Desktop -> Re-import into Android model matches semantically without data loss.
- [x] Headless CLI command `--export-backup=<path>` exports backup cleanly with exit code 0.
- [x] `DiagnosticBundleService` generates a sanitized zip bundle with database integrity checks, redacted logs, and system specs.
- [x] `SettingsScreen` provides comprehensive Material 3 UI for all sections (General, Appearance, Library, Reader, Downloads, Tracking, Backup, Advanced).
- [x] `AboutScreen` displays accurate version, build details, and environment info.
- [x] `DesktopShell` wires `DesktopDestination.Settings` and `DesktopDestination.About` navigation.
- [x] Automated test suite covering backup export, round-trip contract, diagnostics bundle generation, and settings UI.
- [x] Clean code gates: `spotlessCheck` and `:app:testDebugUnitTest :app:assembleDebug` pass.

---

## Execution Tasks

### Task 1: Backup Export Database Queries & Repository Extension
- In `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq`:
  - Add queries for exporting all manga (`selectAllMangaForExport`), chapters (`selectAllChaptersForExport`), categories (`selectAllCategoriesForExport`), history (`selectAllHistoryForExport`), tracking (`selectAllTrackingForExport`), source metadata (`selectAllSourcesForExport`), preference snapshots (`selectAllPreferenceSnapshotsForExport`), source preference snapshots (`selectAllSourcePreferenceSnapshotsForExport`).
- Update `LibraryRepository.kt`, `LibraryMutationPort.kt`, and `SqlDelightLibraryRepository.kt` with snapshot methods.
- Write unit tests in `SqlDelightLibraryRepositoryTest.kt`.

### Task 2: AndroidBackupExporter & Round-Trip Contract Tests
- Implement `AndroidBackupExporter.kt` in `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/`:
  - Transform database records to `AndroidBackupManga`, `AndroidBackupChapter`, `AndroidBackupCategory`, `AndroidBackupHistory`, `AndroidBackupTracking`, etc.
  - Encode through `ProtoBuf.encodeToByteArray` and compress via Okio GZIP sink.
- Implement comprehensive round-trip tests in `AndroidBackupRoundTripTest.kt` verifying Android <-> Desktop semantic fidelity.
- Add `--export-backup=<path>` to `DesktopCommand.kt` and `DesktopCommandRunner.kt`.

### Task 3: Diagnostic Bundle Service & System Information
- Implement `DiagnosticBundleService.kt` in `desktop-app/src/main/kotlin/mihon/desktop/diagnostics/`:
  - Execute database `PRAGMA integrity_check` on SQLite database.
  - Collect sanitized system information (OS version, Java vendor/version, Mihon version, distribution mode).
  - Collect extension metadata and recent log entries (with credential/token redaction).
  - Package into a timestamped `.zip` archive.
- Write unit tests in `DiagnosticBundleServiceTest.kt`.

### Task 4: Settings & About Compose Screens
- Implement `SettingsScreen.kt` in `desktop-app/src/main/kotlin/mihon/desktop/ui/settings/`:
  - Master-detail or tabbed categories: General, Appearance, Library, Reader, Downloads, Tracking, Backup & Restore, Advanced.
  - Interactive toggles and controls connected to `DesktopPreferenceStore` and `DesktopReaderSettingsStore`.
  - Backup actions: Trigger file chooser for backup export and import with feedback dialogs.
  - Diagnostics actions: Button to run integrity check and generate diagnostic zip.
- Implement `AboutScreen.kt` for `DesktopDestination.About`.
- Wire `SettingsScreen` and `AboutScreen` into `DesktopShell.kt` and `MihonDesktopApp.kt`.
- Write Compose UI test in `SettingsScreenTest.kt`.

### Task 5: Verification Script, Full Regression & Evidence
- Create `scripts/verify-desktop-settings-backup.ps1`.
- Run full verification suite including Android build gates.
- Create evidence document `docs/superpowers/evidence/windows-settings-backup-diagnostics.md`.
- Commit changes.
