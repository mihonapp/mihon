**English** | [Bahasa Indonesia](README.id.md)

<div align="center">

<img src=".github/assets/logo.png" alt="KomiKita logo" width="112" />

# KomiKita

KomiKita is an open-source Android app for reading manga, comics, and webtoons. Organize your library, download chapters to read offline, and pick up where you left off.

[![Latest release](https://img.shields.io/github/v/release/BaghasWahyu/komikita?label=release&color=0f766e)](https://github.com/BaghasWahyu/komikita/releases/latest)
[![Android 8.0 or later](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#download-and-install)
[![Build and test](https://github.com/BaghasWahyu/komikita/actions/workflows/build.yml/badge.svg)](https://github.com/BaghasWahyu/komikita/actions/workflows/build.yml)
[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue)](LICENSE)

[**Download the latest release**](https://github.com/BaghasWahyu/komikita/releases/latest) · [User guide](docs/USER_GUIDE.md) · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/BaghasWahyu/komikita/issues)

</div>

<p align="center">
  <img src=".github/assets/screenshots/library.png" alt="KomiKita library with a Continue reading carousel" width="30%" />
  <img src=".github/assets/screenshots/reading-year.png" alt="Reading year recap, completed chapters, reading time, and streaks" width="30%" />
  <img src=".github/assets/screenshots/privacy.png" alt="Hidden categories protected by Android biometrics or device PIN" width="30%" />
</p>

Actual app screenshots with original demo titles. [Reproduce the demo](tools/demo/README.md).

[Mihon](https://github.com/mihonapp/mihon) carries on the Tachiyomi codebase. KomiKita is a Mihon fork that adds library visibility controls, a Continue reading card, and in-app updates. Its separate application ID lets you install it alongside Mihon.

## Contents

- [Features](#features)
- [Download and install](#download-and-install)
- [Getting started](#getting-started)
- [App updates](#app-updates)
- [Moving from Mihon](#moving-from-mihon)
- [Privacy and content sources](#privacy-and-content-sources)
- [Build from source](#build-from-source)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [Contributing and support](#contributing-and-support)
- [Credits and license](#credits-and-license)

## Features

| Feature | How it works |
| --- | --- |
| Reader | Choose page or webtoon mode. Set the reading direction, theme, and controls in the reader. |
| Library | Create categories, search and filter titles, and switch between grid and list views. |
| Continue reading | Swipe through up to five recommendations. Keep reading your latest title or pick another recent read or update. New chapter notifications also offer a Continue reading action. |
| Offline and local reading | Download chapters or open comics stored on your device. |
| Privacy | Unlock hidden categories using Android biometrics or your device PIN, pattern, or password. They lock again in the background. Hidden titles stay out of Continue reading, history, update notifications, and widgets. |
| Visibility | In Browse, hide extensions and their sources without uninstalling them. |
| Reading statistics | See completed chapters, reading time, current and best streaks, and yearly recaps with your most read titles and monthly reading time. |
| Diagnostic logs | Export recent app logs and device details from Advanced settings, with URLs, credentials, and hidden titles redacted. |
| Extension icons | Show extension icons on library covers. |
| Reading progress | Save your history and progress, schedule library updates, or connect a tracker such as AniList or MyAnimeList. |
| Backups | Save a copy of your library, progress, and settings to a location you choose so you can restore them later. |
| Battery saver | The reader prefers display refresh rates up to 60 Hz. You can turn this off. |
| App updates | Read release notes and download a compatible APK from the app. |

In version `1.0.7`, KomiKita can load covers from sources that supply relative image URLs, such as paths starting with `/images/covers/`.

The [user guide](docs/USER_GUIDE.md) covers visibility settings, extension icons, and battery saver. Battery use varies by device and reading conditions. See the [performance notes](docs/PERFORMANCE.md) for measurements and their limits.

## Download and install

KomiKita requires Android 8.0 (API 26) or later. Download the APK from [GitHub Releases](https://github.com/BaghasWahyu/komikita/releases/latest).

### Choose an APK

For a smaller download on an ARM64 device, choose `komikita-arm64-v8a-vX.Y.Z.apk`. If you are unsure which architecture your device uses, choose the universal APK. `X.Y.Z` in the filename is the version number.

| APK filename | Choose this for |
| --- | --- |
| `komikita-arm64-v8a-vX.Y.Z.apk` | ARM64 phones and tablets. |
| `komikita-armeabi-v7a-vX.Y.Z.apk` | Devices using 32-bit ARM. |
| `komikita-x86-vX.Y.Z.apk` | Devices or emulators using 32-bit x86. |
| `komikita-x86_64-vX.Y.Z.apk` | Devices or emulators using 64-bit x86. |
| `komikita-vX.Y.Z.apk` | Universal APK for all architectures listed above. |

The GitHub release workflow builds only the standard APK (`app.komikita`). The updater is enabled and Firebase is disabled by default. Android installs the standard APK separately from older FOSS installations (`app.komikita.foss`), which store their own data.

### Install the app

1. Download the APK you chose from the release page.
2. Open the APK in your browser or file manager.
3. When Android prompts you, allow installation from that app and confirm.
4. Open KomiKita and finish setup.

To update and keep your data, install a newer APK of the same variant over the existing app.

## Getting started

1. During setup, choose a dedicated folder for downloads, backups, and other app files.
2. Open local comics or set up a compatible extension repository. KomiKita comes without comics or built-in content sources.
3. Add titles to your library. Use categories, views, and filters to organize your collection.
4. Set the reader mode, direction, and controls. Download chapters before you need to read offline.
5. Create a backup in data and storage settings before switching devices or changing installations.

## App updates

GitHub release APKs check for updates when you open the app and every six hours while you keep using it. To check manually, open **More → About → Check for updates**. Since version `1.0.6`, a manual check requests fresh release data from GitHub.

KomiKita offers an update when a published stable release has a higher version and a compatible APK. The app shows the release notes and offers the download. Android asks for confirmation during installation.

If the app shows **No new updates**, compare the version in **About** with [the latest GitHub release](https://github.com/BaghasWahyu/komikita/releases/latest). If a newer version is available, you can install an APK of the same variant manually. Local and CI builds require you to enable the updater at build time.

See [Checking for app updates](docs/USER_GUIDE.md#checking-for-app-updates).

## Moving from Mihon

1. Create a backup in Mihon.
2. Install KomiKita and choose its storage folder.
3. Open KomiKita's data and storage settings and restore the backup.
4. Check your library and reading progress. Reinstall extensions and sign in to trackers if needed.

See the [migration guide](docs/USER_GUIDE.md#moving-from-mihon) and the [project identity and compatibility notes](docs/BRANDING.md).

## Privacy and content sources

KomiKita stores your library, reading progress, preferences, and tracker credentials on your device. You choose where to save backups.

Default builds omit Firebase Analytics and Crashlytics. The app sends requests to content sources, extensions, trackers, and DNS providers as needed for the features you use. Release APKs also contact GitHub to check for and download updates.

The [privacy policy](docs/PRIVACY.md) covers stored data, network requests, and optional telemetry.

## Build from source

KomiKita uses Kotlin, Jetpack Compose, SQLDelight, and WorkManager. An optional Rust module processes chapter data through JNI. Builds without it use the Kotlin implementation.

### Requirements

| Tool | Project configuration |
| --- | --- |
| Android Studio | Use an installation compatible with the project's Android Gradle Plugin. |
| JDK | CI starts with JDK 21 from [`.github/.java-version`](.github/.java-version). The [Gradle daemon criteria](gradle/gradle-daemon-jvm.properties) select JetBrains JDK 25; Gradle can provision the matching daemon JVM. Java bytecode targets version 17. |
| Android SDK | Compile SDK **37.2**, target SDK **36**, minimum SDK **26**. |
| Android NDK | **29.0.14206865** for native builds. |
| Gradle | Use the included wrapper; no separate Gradle installation is required. |
| Rust, optional | Stable Rust, Android targets, and `cargo-ndk` for builds that include the native module. |

The [version catalog](gradle/mihon.versions.toml), [Gradle wrapper configuration](gradle/wrapper/gradle-wrapper.properties), and [CI workflow](.github/workflows/build.yml) are the sources of truth for build requirements.

### Build a debug APK without the native module

```sh
git clone https://github.com/BaghasWahyu/komikita.git
cd komikita
```

Configure the Android SDK using `ANDROID_HOME`, or create an untracked `local.properties` file at the repository root.

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Run the debug build.

```sh
./gradlew assembleDebug -Penable-rust=false
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. Debug APKs are written to `app/build/outputs/apk/debug/` and use the separate application ID `app.komikita.dev`.

### Build with native processing

Install Rust, the Android targets, and `cargo-ndk` as described in the [native module guide](docs/NATIVE.md). Then run the following command.

```sh
./gradlew assembleDebug
```

For another installed NDK, pass `-Prust-ndk-path=/path/to/ndk`.

### Run checks

```sh
./gradlew spotlessCheck testDebugUnitTest verifySqlDelightMigration -Penable-rust=false
```

Remove `-Penable-rust=false` to include the native build once the toolchain is ready. Set up APK signing before distributing a release. Android uses the application ID and signing key to match an update to an existing installation. See the [contributor guide](CONTRIBUTING.md) for signing, release variants, translations, and release notes.

## Project structure

| Path | Responsibility |
| --- | --- |
| [`app/`](app) | Compose UI, reader, downloads, tracking, backup, and application services. |
| [`domain/`](domain) | Models, use cases, and repository contracts. |
| [`data/`](data) | Database access, repository implementations, and release metadata. |
| [`core/`](core) | Shared utilities, networking, archives, dependency injection, and the JNI bridge. |
| [`native/`](native) | Rust chapter-processing implementation. |
| [`source-api/`](source-api), [`source-local/`](source-local) | Extension contracts and local content support. |
| [`presentation-core/`](presentation-core), [`presentation-widget/`](presentation-widget) | Shared presentation components and widgets. |
| [`i18n/`](i18n), [`icons/`](icons) | Translations and icon assets. |

See [project structure and data flow](docs/PROJECT_ANALYSIS.md) for the architecture overview.

## Documentation

| Guide | What it covers |
| --- | --- |
| [User guide](docs/USER_GUIDE.md) | Library visibility, extension icons, battery saver, updates, and migration. |
| [Changelog](CHANGELOG.md) | Version-specific features and fixes. |
| [Contributing](CONTRIBUTING.md) | Development setup, signing, translations, and release preparation. |
| [Privacy policy](docs/PRIVACY.md) | Local data, network requests, and telemetry. |
| [Native module](docs/NATIVE.md) | Rust toolchain, JNI integration, and Kotlin fallback. |
| [Performance notes](docs/PERFORMANCE.md) | Measurement methods, results, and their limits. |
| [Validation record](docs/VALIDATION.md) | Previously recorded build and test checks. |

The [documentation index](docs/README.md) links to all project guides in **English and Bahasa Indonesia**.

## Contributing and support

Use [GitHub Issues](https://github.com/BaghasWahyu/komikita/issues) to report bugs or suggest improvements. For a bug report, include your KomiKita version, Android version, APK variant, steps to reproduce, and relevant logs with personal information removed.

Code, documentation, and translation contributions are welcome in English or Bahasa Indonesia. Start with [CONTRIBUTING.md](CONTRIBUTING.md) and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Credits and license

KomiKita builds on [Mihon](https://github.com/mihonapp/mihon) and Tachiyomi. Credit belongs to their teams and contributors; upstream copyright and license notices are retained. See [Credits and acknowledgements](CREDITS.md) and the [upstream changelog](docs/UPSTREAM_CHANGELOG.md).

Copyright © 2015 Javier Tomás<br />
Copyright © 2024 Mihon Open Source Project

Licensed under the [Apache License 2.0](LICENSE). KomiKita does not host content and is not affiliated with content providers.
