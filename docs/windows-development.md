# mihondesk Windows Development

## Requirements

- Windows 10 22H2 or Windows 11 x64
- JDK 21 or 23 selected as the Gradle runtime; the main application uses the Java 17 toolchain
- Java 21 toolchain for the independent browser helper
- PowerShell 7 or Windows PowerShell 5.1

## Run from source

```powershell
.\gradlew.bat :desktop-app:run
```

Use an isolated portable data directory during development:

```powershell
$dataRoot = Join-Path $env:TEMP 'mihondesk-dev-data'
.\gradlew.bat :desktop-app:run --args="--portable --data-dir=$dataRoot"
```

## Verify the foundation slice

```powershell
.\scripts\verify-desktop-foundation.ps1
```

The verifier checks formatting, desktop unit tests, the self-contained application image, packaged startup, and explicit data-root initialization.

## Build Windows distributions

```powershell
.\gradlew.bat :desktop-app:spotlessCheck :desktop-app:assembleWindowsRelease --max-workers=1
```

The release version comes from `desktop-version.txt`. EXE, MSI and portable ZIP files are written to `desktop-app/build/releases/<version>/`. See [Windows release and upgrade notes](WINDOWS_RELEASE.md) for packaging checks and data compatibility.

## Update application artwork

The icon source is `desktop-app/src/main/resources/icon.svg`. Export the PNG, multi-size Windows ICO and GitHub artwork with `scripts/export-brand-assets.ps1`. See [brand assets](BRANDING.md) for sizes, colors and export requirements.
