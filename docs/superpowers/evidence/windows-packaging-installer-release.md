# Phase 8 Evidence: Packaging, Installer, Portable ZIP, Update Flow, Rollback, and Release

Date: 2026-09-06
Branch: `feat/windows-packaging-installer-release`

## Overview
Phase 8 completes the final milestone of the Mihon Windows Desktop port in full conformance with `docs/superpowers/specs/2026-08-31-windows-port-design.md`:
1. **Packaging & Distributions**:
   - Built standalone Windows Installer (`MihonW-0.1.0.exe`) with WiX Toolset via `:desktop-app:packageExe` (~86 MB).
   - Built zero-install Portable ZIP package (`MihonW-0.1.0-windows-x64-portable.zip`) via `:desktop-app:packagePortableZip` (~80 MB) with bundled stripped OpenJDK 21 JRE, embedded `.portable` marker file, and updater script.
2. **Portable Mode Automatic Detection**:
   - `AppDirectoryResolver` and `DesktopRuntimeFactory` automatically detect `.portable` in the application directory or `--portable` on CLI. When present, user data is 100% isolated to `./data` with zero writes to the Windows Registry or `%APPDATA%`.
3. **File Associations & CLI Enhancements**:
   - Added support for `--version` / `-v`, `--help` / `-h`.
   - Added direct positional file association arguments:
     - Opening `.tachibk` directly executes `DesktopCommand.ImportBackup`.
     - Opening `.cbz` / `.zip` directly executes `DesktopCommand.ImportLocal`.
   - Added registry scripts `scripts/register-file-associations.ps1` and `scripts/unregister-file-associations.ps1`.
4. **GitHub Update Flow & Atomic Rollback**:
   - Implemented `DesktopAppUpdateService` checking GitHub Releases API, parsing semver versions, selecting target asset (installer vs portable zip), and verifying SHA-256 hashes against checksum manifests.
   - Implemented `scripts/MihonUpdater.ps1` providing process-safe replacement, integrity verification, and automatic rollback to `.backup` on validation failure.
   - Verified via `DesktopAppUpdateServiceTest` (5 unit tests passed).
5. **Clean-Machine Sandbox Verification**:
   - `scripts/verify-desktop-clean-machine.ps1` successfully extracted the portable ZIP into an isolated sandbox in `%TEMP%`.
   - Verified `--version`, `--help`, portable isolation in `./data`, headless backup export, and clean process exit with code 0.
6. **Release Documentation**:
   - Authored `docs/WINDOWS_RELEASE.md` covering system requirements, release artifact differences, build instructions, CLI usage, and update procedures.

## Verification Gates
1. `DesktopCommandTest`: PASSED (CLI help, version, and file association parsing).
2. `DesktopAppUpdateServiceTest`: PASSED (GitHub release parsing, semver comparison, SHA-256 verification, and asset matching).
3. `scripts/verify-desktop-clean-machine.ps1`: PASSED (Full end-to-end sandbox verification).
4. `spotlessCheck`: PASSED across 138 modules.
5. `:app:testDebugUnitTest :app:assembleDebug`: PASSED (Android native regression gates).
