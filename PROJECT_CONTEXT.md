# Yomori Project Context

## Status

Yomori is in initial repository bootstrap. It is based on the current `main` branch of `mihonapp/mihon` and will remain compatible with the existing user-installed Mihon/Tachiyomi extension ecosystem.

GitHub Actions was enabled for the fork on 2026-07-10. Pull requests and `main` builds use GitHub Actions as the authoritative validation and APK artifact environment.

The first product milestone is CBL reading-list import and deterministic, user-correctable matching. No public Yomori release is ready yet.

## Product goal

Yomori is a source-agnostic Android comic reader that imports Comic Book Lover (`.cbl`) reading lists, resolves their entries through user-selected compatible extensions, and reads the resulting cross-series order as one continuous list.

Yomori does not provide, bundle, host, operate, or recommend content sources.

## Confirmed decisions

- App and repository name: **Yomori**.
- Canonical repository: `Kamui2040/Yomori`.
- Base project: Mihon under Apache-2.0.
- Extensions remain separate user-installed APKs.
- Users add extension repositories and choose which installed extensions a CBL may search.
- Matching is series-first, then issue/chapter matching.
- Matching uses a configurable confidence percentage plus an ambiguity margin between the best and second-best candidates.
- Low-confidence or ambiguous results require user review.
- Users may set source preferences globally, per reading list, per series, and per individual entry.
- User-confirmed mappings override automatic matches and are never silently replaced.
- Original CBL data is retained even after successful matching.
- Standard Yomori builds do not include telemetry.
- GitHub Actions is the authoritative APK build environment.

## Matching defaults

Initial defaults, subject to testing:

- Automatic acceptance: score at least 88%.
- Review range: 65% through 87%.
- Unresolved: below 65%.
- Required lead over the second candidate: 10 percentage points.

The basic score combines normalized series-title similarity, issue-number match, volume/year agreement, available metadata, source-specific evidence, and confirmed user history. The score breakdown must be visible in manual review.

## Source preference hierarchy

From highest to lowest priority:

1. Entry-specific confirmed match or source override.
2. Series-specific confirmed mapping or source preference.
3. Reading-list source order.
4. Global source preference.

Only user-selected installed extensions may be queried for a reading list.

## Resolution states

Planned states:

- `UNSEARCHED`
- `SEARCHING`
- `AUTO_MATCHED`
- `USER_CONFIRMED`
- `AMBIGUOUS`
- `UNRESOLVED`
- `SOURCE_UNAVAILABLE`
- `CHAPTER_REMOVED`
- `NEEDS_REMATCH`

## Initial implementation sequence

1. Repository governance and CI adaptation.
2. Independent application identity and temporary Yomori branding.
3. CBL domain model and parser with fixtures and unit tests.
4. Reading-list persistence and migrations.
5. Title and issue normalization.
6. Confidence scoring and ambiguity rules.
7. Import and source-selection flow.
8. Candidate review and manual overrides.
9. Cross-series reader navigation and progress.
10. Repair and rematching tools.

## Compatibility invariants

- Keep extension-facing APIs binary compatible, particularly the `eu.kanade.tachiyomi.source` contracts.
- Do not rename those API packages as part of product rebranding.
- Keep extension signature verification and trust handling.
- Track upstream source-API versions and extension-loader changes.

## Release blockers

Before the first public APK release:

- Change the Android application ID so Yomori can coexist with Mihon.
- Replace Mihon trademarks and visual branding with Yomori assets.
- Remove or replace Mihon-specific update, support, and download links.
- Establish Yomori release signing and document key custody.
- Validate extension loading against representative compatible extensions.
- Add required attribution and modified-file notices.

## Upstream baseline

Fork baseline at project creation:

- Upstream commit: `b4635c41a8dd5e30edf480b0c9bdc80d0fda0520`
- Upstream release line: Mihon `0.20.1`
- Baseline date: 2026-07-10
