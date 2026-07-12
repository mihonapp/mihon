# Yomori Project Context

## Status

Yomori is in early implementation. It is based on the current `main` branch of `mihonapp/mihon` and will remain compatible with the existing user-installed Mihon/Tachiyomi extension ecosystem.

GitHub Actions was enabled for the fork on 2026-07-10. Pull requests and `main` builds use GitHub Actions as the authoritative validation and APK artifact environment.

The Android application identity is `io.github.kamui2040.yomori`, with Yomori version line `0.1.0-alpha01`. The Kotlin namespace and extension-facing API packages remain unchanged for compatibility.

Device-test artifacts use the dedicated `io.github.kamui2040.yomori.debug` package and a reproducible public development certificate. This permits direct in-place updates between development APKs downloaded on a phone without requiring local signing setup.

The first product milestone is CBL reading-list import and deterministic, user-correctable matching. The safe parser, transactional persistence, normalization, confidence scoring, visible import interface, and per-list source-selection flow are implemented and covered by focused tests. Candidate resolution and manual review are the next product stages. No public Yomori release is ready yet.

## Product goal

Yomori is a source-agnostic Android comic reader that imports Comic Book Lover (`.cbl`) reading lists, resolves their entries through user-selected compatible extensions, and reads the resulting cross-series order as one continuous list.

Yomori does not provide, bundle, host, operate, or recommend content sources.

## Confirmed decisions

- App and repository name: **Yomori**.
- Canonical repository: `Kamui2040/Yomori`.
- Android application ID: `io.github.kamui2040.yomori`.
- Development application ID: `io.github.kamui2040.yomori.debug`.
- Base project: Mihon under Apache-2.0.
- Extensions remain separate user-installed APKs.
- Users add extension repositories and choose which installed extensions a CBL may search.
- Matching is series-first, then issue/chapter matching.
- Matching uses a configurable confidence percentage plus an ambiguity margin between the best and second-best candidates.
- Low-confidence or ambiguous results require user review.
- Users may set source preferences globally, per reading list, per series, and per individual entry.
- User-confirmed mappings override automatic matches and are never silently replaced.
- Original CBL data is retained even after successful matching.
- CBL parsing preserves `<Book>` order and rejects DTD/entity declarations, oversized documents, excessive entry counts, malformed structure, and entries without required `Series` or `Number` attributes.
- Imported lists, entries, ordered database references, unknown metadata, warnings, matching state, and reading-list progress are stored in SQLDelight.
- SQLDelight migration 15 adds ordered per-list source selections without changing extension-facing APIs.
- Reading-list insertion is transactional, deletion cascades to owned records, and progress cannot point to a missing entry.
- The primary Reading Lists tab imports local `.cbl` documents through Android's system document picker.
- Imported files are read with a 16 MiB boundary and support UTF-8, UTF-16 little-endian, and UTF-16 big-endian XML.
- At least one currently installed online source must be selected before a reading list can be saved.
- Selected source order is persisted as the list's search-priority order and can be edited later.
- Missing extension IDs remain visible as unavailable source choices for later repair rather than being silently discarded.
- No source is bundled, recommended, or selected automatically.
- Title normalization produces locale-independent full and edition-free comparison keys while retaining extracted year and volume as separate scoring evidence.
- Issue-number normalization preserves annual, special, Free Comic Book Day, one-shot, suffix, decimal, fraction, and opaque identifier distinctions.
- Normalization never replaces the original CBL metadata stored for repair and rematching.
- Confidence scoring exposes a complete component breakdown for title, issue, year, volume, external identifiers, source preferences, and confirmed history.
- Automatic matching requires at least 88%, an equivalent issue number, at least 85% title similarity, and a lead of at least 10 percentage points over the runner-up.
- Scores from 65% through 87.99% require review; scores below 65% remain unresolved.
- Missing optional metadata is neutral, conflicting metadata is penalized, and supporting evidence cannot bypass title or issue safety gates.
- Equal high-scoring candidates remain ambiguous rather than being silently selected by source order.
- Standard Yomori builds do not include telemetry.
- GitHub Actions is the authoritative APK build environment.
- Development APK filenames include the Yomori version, workflow build number, short commit SHA, and ABI.
- Development APKs use a public test certificate that is never used for production releases.
- Null-pointer failures returned by HTTP source extensions are shown as an actionable update-or-change-source message instead of a raw exception.
- Inherited public release automation remains disabled until Yomori production signing and release readiness are established.

## Matching defaults

Initial defaults, subject to testing with real imported lists:

- Automatic acceptance: score at least 88%.
- Review range: 65% through 87.99%.
- Unresolved: below 65%.
- Required lead over the second candidate: 10 percentage points.
- Minimum title similarity for automatic acceptance: 85%.

The basic score combines normalized series-title similarity, issue-number equivalence, volume/year agreement, external identifiers, source preference, and confirmed user history. The complete score breakdown is retained for the manual-review interface.

## Source preference hierarchy

From highest to lowest priority:

1. Entry-specific confirmed match or source override.
2. Series-specific confirmed mapping or source preference.
3. Reading-list source order.
4. Global source preference.

Only user-selected installed extensions may be queried for a reading list.

## Resolution states

Persisted states:

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

1. Repository governance and CI adaptation. **Complete.**
2. Independent application identity and temporary Yomori branding. **Complete.**
3. CBL domain model and parser with fixtures and unit tests. **Complete.**
4. Reading-list persistence and migrations. **Complete.**
5. Title and issue normalization. **Complete.**
6. Confidence scoring and ambiguity rules. **Complete.**
7. Import and source-selection flow. **Complete.**
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

- Finalize original Yomori visual branding beyond the temporary launcher mark.
- Remove or replace inherited Mihon-specific update, support, and download links.
- Establish protected Yomori production signing and document key custody.
- Validate extension loading against representative compatible extensions.
- Add required attribution and modified-file notices.

## Upstream baseline

Fork baseline at project creation:

- Upstream commit: `b4635c41a8dd5e30edf480b0c9bdc80d0fda0520`
- Upstream release line: Mihon `0.20.1`
- Baseline date: 2026-07-10
