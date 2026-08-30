# Mihon W Windows Development

## Requirements

- Windows 10 22H2 or Windows 11 x64
- JDK 21 selected as the Gradle runtime; project output remains Java 17
- PowerShell 7 or Windows PowerShell 5.1

## Run from source

```powershell
.\gradlew.bat :desktop-app:run
```

Use an isolated portable data directory during development:

```powershell
$dataRoot = Join-Path $env:TEMP 'mihon-w-dev-data'
.\gradlew.bat :desktop-app:run --args="--portable --data-dir=$dataRoot"
```

## Verify the foundation slice

```powershell
.\scripts\verify-desktop-foundation.ps1
```

The verifier checks formatting, desktop unit tests, the self-contained application image, packaged startup, and explicit data-root initialization.
