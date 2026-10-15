# KuroToon

### Your manga universe. Your way. 🌌

KuroToon is an independent Android reader project for manga, manhwa, manhua, webtoons, comics, and other supported reading formats. Its goal is a polished cosmic-themed reading experience with source choice, personal customization, and a clear path for future updates.

> **Development status:** KuroToon is actively being developed. Items marked planned or in progress are not guaranteed to exist in the current build. No APK should be described as device-tested until it has actually been installed and smoke-tested on a real Android device.

[![License: Apache-2.0](https://img.shields.io/github/license/gtxnrg-ship-it/mihon?label=License&color=9461ff)](./LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/gtxnrg-ship-it/mihon/build.yml?label=Build)](./.github/workflows/build.yml)

---

## KuroToon at a glance

- **Android-first reader** with existing reading and library foundations preserved during development.
- **Five main destinations:** Home, Browse, Shop, Library, and Me.
- **Source choice:** users select third-party sources; KuroToon does not host reading content.
- **Cosmic visual identity:** deep-space surfaces, purple and blue light, restrained rose highlights, and gold reward accents.
- **Customization:** themes and cosmetics are planned as a distinct system, separate from core reading access.
- **Release-based updates:** a version checker and user-approved Android installation flow are planned.

## Main sections

| Section | Intended experience | Status |
|---|---|---|
| **Home** | Continue reading, recent activity, featured/trending rows, and helpful first-run guidance. | Planned/in progress |
| **Browse** | Search and discover titles across enabled sources, with sorting and genre/language filters where data supports them. | Planned/in progress |
| **Shop** | Cosmetic items such as stickers, avatars, frames, banners, and themes. No pay-to-read mechanics. | Planned |
| **Library** | Reading lists, categories, favourites, bookmarks, history, unread updates, and preserved reading progress. | Existing foundation; redesign planned |
| **Me** | Profile, reading statistics, goals, achievements, customization, privacy, backups, and settings. | Planned/in progress |

## Sources and repositories

Repository management belongs under **Me → Settings → Sources → Repositories**, not as a sixth bottom-navigation destination.

The intended source experience includes:
- Searchable and filterable repository catalogs, including named sources and categories such as manga, manhwa, manhua, webtoon, doujin, comics, and novels.
- Language and category filters, source enable/disable controls, manual repository URL entry, and supported repository-page/deep-link installation.
- Clear separation between repositories, installed extensions, and the actual sources used to browse titles.
- Honest repository health, sync status, last-checked time, update availability, and useful error messages.
- Deduplication and normalization across sources where reliable identifiers or metadata allow it, without hiding source provenance.
- A constrained extension architecture. Adding a repository must not automatically execute arbitrary third-party code with unrestricted access to the app or device.

MangaDex is intended to remain an independently controllable provider rather than being conflated with the extension catalog.

## Reader direction

Core reading reliability comes first: page rendering, gestures, chapter navigation, progress, history, bookmarks, and backups must remain stable.

Planned enhancements include:
- Optional atmospheric audio and haptics with user controls.
- Context-aware music discovery associated with a series or chapter, subject to source availability and applicable rights.
- Optional AI-assisted dialogue dubbing with language and voice settings, clear network/privacy controls, and no interference with ordinary reading.
- Broader series/season context navigation where supported by metadata.
- Reader appearance and layout customization without sacrificing panel visibility or performance.

These are roadmap items, not promises that the current build already contains them.

## Personalization and rewards

The Shop is intended for **cosmetics only** at first: themes, stickers, avatars, frames, and banners. A future reward/achievement system may recognize reading milestones and unlock cosmetic items. The UI must distinguish free, earned, and paid items honestly. No purchase flow, premium currency, inventory, or reward should be shown as operational until the backing system exists and has been tested.

Kuro-chan's approved original artwork is canonical. The official icon and character art must use those approved assets; they must not be replaced with invented or approximate mascot art.

## Download an APK

There is **no verified, device-tested APK linked from this README yet**.

When a successful GitHub Actions build is available:
1. Open [GitHub Actions](https://github.com/gtxnrg-ship-it/mihon/actions).
2. Select a successful **Build & Test** run for the commit you want.
3. Download the APK artifact (currently configured for `arm64-v8a`) and extract the APK from the downloaded archive.
4. Install it on your Android device. Android may ask you to allow installation from the app used to open the APK.

A green CI run is not the same as a real-device smoke test. Do not install artifacts from failed or untrusted runs. Public release downloads will be linked here once a release has actually been built and published.

## Updates

The intended update experience is:
1. KuroToon checks the official release feed for a newer compatible version.
2. It shows the version number and release notes, then offers an **Update** action.
3. The APK is downloaded and verified before Android's package installer is opened.
4. Android handles any required installation confirmation; after installation, the user can reopen the updated app.

Updates must keep the same application ID and signing identity and use increasing version codes. The app must never claim an update was installed before Android confirms it. Silent self-installation is not assumed.

## Build locally

Use the Java version declared in `.github/.java-version` and the checked-in Gradle wrapper.

The CI release command is:

```bash
./gradlew assembleRelease -Pdist=ci
```

The configured CI workflow also runs formatting checks, unit tests, and SQLDelight migration verification. See [Build & Test workflow](./.github/workflows/build.yml).

## Legal notices and open-source credits

KuroToon is an independent project and is not an official release of any upstream project. Portions of the codebase are derived from the **Mihon open-source project**. Applicable upstream copyright and attribution notices and the Apache License 2.0 are retained in this repository; see [LICENSE](./LICENSE). Third-party components may have separate terms. This credit is included for license compliance, not to present KuroToon as the upstream product.

## Content disclaimer

KuroToon does not host manga, comics, or other reading content. It does not claim affiliation with third-party content providers. Source availability and legality depend on the provider and the user's location; users are responsible for ensuring they have the rights to access content.

## Contributing

Please review [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) and [CONTRIBUTING.md](./CONTRIBUTING.md). Roadmap entries should only be marked complete after implementation and verification.

---

**KuroToon — find your next world.** ✨
