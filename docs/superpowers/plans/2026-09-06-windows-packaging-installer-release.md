# Windows Packaging, Installer, Portable ZIP, Update Flow, and Release Plan (Phase 8)

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver Phase 8 (the final phase of the Mihon Windows Port): per-user Windows Installer (`.exe`), zero-install Portable ZIP (`.zip`) with automatic `.portable` marker detection, Windows file associations for `.tachibk` and `.cbz`, CLI `--help` / `--version` and positional path support, GitHub release update checker with SHA-256 verification, atomic portable updater with rollback, clean-machine sandbox verification, and release documentation.

---

## Fixed scope and invariants

- [x] Gradle task `packagePortableZip` generates a standalone portable `.zip` containing all binaries, embedded JRE, and `.portable` marker.
- [x] Windows Installer task `packageExe` produces a clean installer `MihonW-0.1.0.exe` via Compose Desktop WiX.
- [x] `AppDirectoryResolver` and `DesktopRuntimeFactory` automatically detect portable mode when `.portable` marker file exists in the application root or `--portable` is supplied.
- [x] `DesktopCommandParser` supports `--help` / `-h`, `--version` / `-v`, and positional file arguments (`.tachibk` -> `ImportBackup`, `.cbz` / `.zip` -> `ImportLocal`).
- [x] `DesktopAppUpdateService` inspects GitHub release metadata, parses semver version, matches target assets, and validates SHA-256 hash.
- [x] `MihonUpdater.ps1` handles atomic replacement with automatic rollback on validation error.
- [x] `scripts/register-file-associations.ps1` and `unregister-file-associations.ps1` manage `.tachibk` and `.cbz` shell associations.
- [x] `scripts/verify-desktop-clean-machine.ps1` verifies portable isolation and CLI functionality in an isolated sandbox.
- [x] Comprehensive release documentation in `docs/WINDOWS_RELEASE.md`.
- [x] Spotless check and Android build gates (`:app:testDebugUnitTest :app:assembleDebug`) pass cleanly.

---

## Execution Tasks

### Task 1: CLI Enhancements & Portable Marker Detection
- In `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt`:
  - Add `DesktopCommand.Help` and `DesktopCommand.Version`.
  - Update `DesktopCommandParser` to recognize `--help`, `-h`, `--version`, `-v`, and positional `.tachibk` / `.cbz` / `.zip` paths.
- In `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommandRunner.kt`:
  - Implement execution handlers for `Help` and `Version`.
- In `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`:
  - Check `Files.exists(executableDirectory.resolve(".portable"))` in addition to `"--portable" in args`.
- Update `DesktopCommandTest.kt`.

### Task 2: Portable ZIP Packaging Task & File Association Scripts
- In `desktop-app/build.gradle.kts`:
  - Register `packagePortableZip` task depending on `createDistributable`.
  - Place `.portable` marker file and `MihonUpdater.ps1` in the distribution.
- Create `scripts/register-file-associations.ps1` and `scripts/unregister-file-associations.ps1`.

### Task 3: GitHub App Update Checker & Atomic Rollback
- In `desktop-app/src/main/kotlin/mihon/desktop/updates/DesktopAppUpdateService.kt`:
  - Model `AppReleaseInfo`, `AppReleaseAsset`, `UpdateCheckResult`.
  - Query GitHub Releases API, compare semver, verify SHA-256 checksums.
- Create `scripts/MihonUpdater.ps1` for process-safe replacement and rollback.
- Unit tests in `desktop-app/src/test/kotlin/mihon/desktop/updates/DesktopAppUpdateServiceTest.kt`.

### Task 4: Clean-Machine Sandbox Verification Script
- Create `scripts/verify-desktop-clean-machine.ps1`:
  - Builds portable distribution.
  - Unpacks into a temporary directory.
  - Verifies executable launches, runs `--version`, `--help`, `--export-backup`.
  - Verifies data is placed in `data/` and not `%APPDATA%`.
  - Ensures clean exit.

### Task 5: Documentation, Verification, and Final Wrap-up
- Create `docs/WINDOWS_RELEASE.md`.
- Run full verification suite including Android build gates.
- Create evidence document `docs/superpowers/evidence/windows-packaging-installer-release.md`.
- Commit changes.
