<div align="center">

# KuroToon

### Your manga universe. Your way. 🌌

An independent Android manga, webtoon, and comic reader based on the open-source Mihon project.

[![License: Apache-2.0](https://img.shields.io/github/license/gtxnrg-ship-it/mihon?label=License&color=9461ff)](./LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/gtxnrg-ship-it/mihon/build.yml?label=Build)](./.github/workflows/build.yml)

</div>

---

## About

KuroToon aims to pair a dependable reader foundation with a distinctive cosmic visual identity, thoughtful customization, and a character-led experience. This is an active development fork: **features described as planned are not necessarily implemented in the current APK**.

## Current foundation

- Independent Android application identity: **KuroToon** (`app.kurotoon`).
- Existing Mihon reader foundation retained while KuroToon changes are developed.
- Existing source, library, and reader functionality is the baseline—not a claim that every planned KuroToon feature is already implemented.
- CI is configured to run formatting checks, unit tests, migration verification, and a release APK build for pull requests.

## Product direction

KuroToon's visual language uses deep-space backgrounds, cosmic purple and blue, restrained rose highlights, and gold accents. Kuro-chan's previously approved artwork is the source of truth; her official icon and character art should be integrated from those original assets, not replaced with an invented mascot.

- **Home:** continue reading, recent activity, and featured/trending series.
- **Browse:** search, sorting, genre and language filters, and source-aware discovery.
- **Shop:** cosmetic stickers, avatars, frames, banners, and themes; no pay-to-read mechanics.
- **Library:** reading lists, categories, bookmarks, history, and unread updates.
- **Me:** profile, achievements, customization, settings, and privacy.
- **Repositories:** Me → Settings → Sources → Repositories—not a separate bottom-navigation tab.
- **Reader:** preserve core reading reliability while developing optional atmospheric audio, haptics, and AI-assisted features with explicit controls.

See [KuroToon's product blueprint](https://github.com/gtxnrg-ship-it/Kurotoon/blob/main/docs/PRODUCT_BLUEPRINT.md) and the in-repository [UI system](./docs/KUROTOON_UI_SYSTEM.md). Roadmap items remain planned until implemented and verified.

## Download an APK

**There is not yet a verified, device-tested KuroToon APK linked here.** When a CI run succeeds, open [GitHub Actions](https://github.com/gtxnrg-ship-it/mihon/actions), select the successful **Build & Test** run for the desired branch or pull request, and download the APK artifact named similar to `arm64-v8a-<commit-sha>`. Extract the APK from the ZIP before installing.

- The configured artifact targets **arm64-v8a**, not every Android processor.
- A green CI build is useful evidence, but does not replace installing and testing on a real device.
- Do not install artifacts from failed or untrusted workflow runs.

## Build locally

Use the Java version declared in `.github/.java-version` and the checked-in Gradle wrapper. The CI release command is:

    ./gradlew assembleRelease -Pdist=ci

The CI workflow also runs formatting checks, unit tests, and SQLDelight migration verification.

## Open source and attribution

KuroToon is based on the [Mihon open-source project](https://github.com/mihonapp/mihon). This fork retains the upstream license, copyright notices, and applicable attributions. KuroToon is an independent community project and is not an official Mihon release.

The code is licensed under the Apache License, Version 2.0; see [LICENSE](./LICENSE). Third-party components may have separate terms.

## Content disclaimer

KuroToon does not host manga, comics, or other reading content. Availability and legality of third-party sources depend on the source and the user's location. Users are responsible for ensuring they have the rights to access content. No affiliation with third-party content providers is implied.

## Contributing

Please review [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) and [CONTRIBUTING.md](./CONTRIBUTING.md). Discuss substantial changes in an issue before opening a pull request.

---

<div align="center">

**KuroToon — find your next world.** ✨

</div>
