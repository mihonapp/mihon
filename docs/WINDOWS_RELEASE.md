# Mihon W - Windows Desktop Release & Distribution Guide

Version: 0.1.0  
Target Architecture: Windows 10/11 x64  
Runtime: OpenJDK 21 (Bundled via Compose Multiplatform runtime image)  
License: Apache License, Version 2.0  

---

## 1. Overview & Distribution Formats

Mihon W for Windows provides two official distribution channels:

### A. Windows Installer (`MihonW-0.1.0.exe`)
- **Type**: Native per-user Windows executable setup powered by WiX Toolset.
- **Install Directory**: `%LOCALAPPDATA%\Programs\MihonW\`
- **Data Directory**: `%APPDATA%\MihonW\`
- **Features**:
  - Automatically creates Start Menu shortcuts and Desktop shortcuts.
  - Per-user installation (does not require administrator privileges).
  - Uninstaller accessible via Windows Settings > Installed Apps.

### B. Portable Distribution (`MihonW-0.1.0-windows-x64-portable.zip`)
- **Type**: Self-contained, zero-install portable archive with embedded JRE.
- **Data Directory**: `./data/` relative to `MihonW.exe`
- **Features**:
  - Contains `.portable` marker file in the root folder.
  - 100% host isolation: does not write to the Windows Registry or `%APPDATA%`.
  - Can be run directly from USB drives or portable disks without leaving traces on the host machine.
  - Includes `MihonUpdater.ps1` for in-place atomic updates with automatic safety rollbacks.

---

## 2. System Requirements

- **Operating System**: Windows 10 (version 1809 or higher) or Windows 11 (64-bit).
- **Architecture**: x86_64 / x64.
- **Memory**: 4 GB RAM minimum (8 GB recommended for large image/comic decoding).
- **Disk Space**: ~250 MB for installation/portable bundle, plus additional storage for cached images and downloaded manga.
- **Java**: **No external Java runtime required**. An optimized, stripped OpenJDK 21 JRE is bundled inside the distribution.

---

## 3. Building Release Artifacts

To compile and package both the installer and portable ZIP from source:

```powershell
# Ensure Java 21 is configured
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 1. Build unpacked application image
.\gradlew.bat :desktop-app:createDistributable

# 2. Build standalone Windows installer (.exe)
.\gradlew.bat :desktop-app:packageExe

# 3. Build portable ZIP package (.zip)
.\gradlew.bat :desktop-app:packagePortableZip
```

### Build Outputs
- **Installer**: `desktop-app/build/compose/binaries/main/exe/MihonW-0.1.0.exe` (~86 MB)
- **Portable ZIP**: `desktop-app/build/compose/binaries/main/portable/MihonW-0.1.0-windows-x64-portable.zip` (~80 MB)

---

## 4. File Associations

Mihon W supports file associations with:
- `.tachibk`: Android / Tachiyomi / Mihon compressed protobuf backup archive.
- `.cbz` / `.zip`: Comic book zip archives.

### Shell Registration Scripts
- **Register**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\register-file-associations.ps1
  ```
- **Unregister**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\unregister-file-associations.ps1
  ```

---

## 5. Command-Line Interface (CLI)

`MihonW.exe` provides a comprehensive headless CLI interface for script automation, backup manipulation, and verification:

```text
Mihon W - Manga Reader for Windows
Usage: MihonW.exe [options] [file]

Options:
  --help, -h                  Show help message and exit
  --version, -v               Show application version and exit
  --portable                  Run in portable mode using ./data directory
  --data-dir=<path>           Specify custom application data directory
  --import-backup=<path>      Import Android .tachibk backup file headlessly
  --export-backup=<path>      Export library to Android .tachibk backup file headlessly
  --import-local=<path>       Import local manga directory or CBZ/ZIP archive headlessly
  --list-library-json         Output library contents as JSON to stdout
  --smoke-test                Verify database initialization and exit

Positional Arguments:
  Passing a .tachibk file directly imports the backup.
  Passing a .cbz or .zip file directly imports the archive into the local library.
```

---

## 6. Update Flow & Safety Rollback

### GitHub Release Inspection
`DesktopAppUpdateService` inspects `https://api.github.com/repos/mihonapp/mihon-w/releases/latest`:
1. Compares the semantic version tag against the running application version.
2. Selects the appropriate asset format (`.exe` installer or `.zip` portable package).
3. Downloads the package and verifies SHA-256 integrity against the release manifest.

### Atomic Portable Updater (`MihonUpdater.ps1`)
When applying a portable update:
1. Waits for the running application PID to terminate.
2. Backs up the existing installation to `MihonW.backup`.
3. Extracts the new archive, preserving local `./data`.
4. Verifies the integrity of `MihonW.exe`.
5. **Atomic Rollback**: If validation fails for any reason, the updater automatically rolls back `MihonW.backup` and restores the working previous version.
6. Relaunches `MihonW.exe`.

---

## 7. Clean-Machine Sandbox Verification

A dedicated clean-machine validation script is provided in `scripts/verify-desktop-clean-machine.ps1`:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-desktop-clean-machine.ps1
```
This script validates:
- Building and extracting the portable ZIP to a clean temporary sandbox.
- Execution of `--version` and `--help`.
- Portable isolation (verifying database creation in `sandbox\MihonW\data` without `%APPDATA%` pollution).
- Headless backup export.
- Clean process exit.
