# Yomori

[![Build](https://img.shields.io/github/actions/workflow/status/Kamui2040/Yomori/build.yml?branch=main&label=build)](https://github.com/Kamui2040/Yomori/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/Kamui2040/Yomori)](./LICENSE)

Yomori is a source-agnostic Android comic reader focused on imported Comic Book Lover (`.cbl`) reading orders. It is an independent fork of [Mihon](https://github.com/mihonapp/mihon).

Yomori is currently in early development. GitHub Actions builds are development artifacts and are not public releases.

## Planned workflow

1. Add user-selected Mihon/Tachiyomi-compatible extension repositories.
2. Install the extensions the user wants to use.
3. Import a CBL reading list.
4. Choose which installed extensions may be searched for that list.
5. Automatically match clear series and issue results.
6. Review ambiguous matches using visible confidence scores and candidate details.
7. Override the source for an entire series or any individual entry.
8. Read the resolved order continuously across series and sources.

## Core principles

- No bundled, hosted, operated, or recommended content sources.
- Existing compatible extensions remain user-installed and user-controlled.
- No telemetry in standard Yomori builds.
- Local-first reading-list metadata, mappings, and progress.
- User-confirmed matches are never silently replaced.
- Original CBL metadata is retained so mappings can be repaired.
- Extension signature verification and trust controls remain in place.

## Current status

Repository bootstrap is underway. The first implementation milestone covers:

- CBL parsing and validation
- Reading-list persistence
- Series-first and issue-level matching
- Configurable confidence and ambiguity thresholds
- Manual candidate review
- List, series, and entry source preferences
- Cross-series reader navigation

See [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md) for current decisions and [`docs/architecture/CBL_MATCHING.md`](./docs/architecture/CBL_MATCHING.md) for the matching design.

## Builds

Pull requests, pushes to `main`, and manual workflow runs execute formatting checks, unit tests, SQLDelight migration verification, and telemetry-free release APK builds. APKs are retained as GitHub Actions artifacts.

Yomori is not ready for a signed public release. The Android application identity and visual branding must be fully separated from Mihon first.

## Content disclaimer

Yomori hosts zero content and has no affiliation with third-party content providers or extension maintainers. Users are responsible for the repositories, extensions, and content services they choose to use and for complying with applicable law and service terms.

## Upstream and attribution

Yomori is derived from Mihon and the earlier Tachiyomi project. Yomori is independently maintained and is not endorsed by the Mihon project.

Upstream project: [mihonapp/mihon](https://github.com/mihonapp/mihon)

## License

```text
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2026 Yomori contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
