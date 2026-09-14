# Mihon W Unified Manga Detail Design

**Date:** 2026-09-14
**Status:** Approved for planning
**Scope:** Replace the separate online-source and library manga detail experiences with one complete shared detail surface and context-specific data/action adapters.

## Objective

The same manga currently opens two materially different detail pages. The online-source page exposes back navigation, explicit library membership, source refresh, browser opening, per-chapter download, and reading. The library page exposes rich metadata editing, categories, tracking, cover management, notes, chapter search/filter/sort/settings, per-chapter and batch actions, selection, and resume reading. Both entry points must render the same complete detail experience so capabilities do not diverge again.

The unified page must preserve the existing Mihon-aligned membership rule: opening, refreshing, reading, or downloading online content does not mark it as a library favorite and does not assign a category. Library membership remains an explicit and reversible user action.

## Approaches Considered

1. Continue maintaining two screens and copy missing controls between them. This is initially direct but guarantees future drift and duplicates every test and interaction fix.
2. Keep one shared detail screen and provide library/online adapters that produce the same state and action contract. **Selected.** This makes visual and functional parity structural rather than conventional.
3. Share only a controller while retaining two screen implementations. This reduces some business-logic duplication but preserves the visible feature drift reported here.

## Shared Feature Contract

Both online-source and library entry points expose the following complete surface for an online-capable manga:

- context-aware back navigation;
- manga title, source, status, author, artist, description, genres, categories, and notes;
- edit information, edit categories, tracking/progress, change/reset/save cover, and edit notes;
- explicit add-to-library or remove-from-library action with an in-progress state;
- refresh manga details and chapter list from the source;
- open the canonical HTTP(S) manga URL in the browser when available;
- chapter count, unread/downloaded/bookmarked filters, source-order/chapter-number/upload-date sorting, and direction switching;
- chapter search and chapter-display settings;
- single-chapter read, bookmark, read/unread, mark-previous-read, download, delete-download, and overflow actions;
- batch chapter selection, bookmark, read/unread, download, delete-download, select-all, and clear-selection actions;
- next/unread/all batch download shortcuts;
- start/resume-reading action based on persisted progress and the filtered chapter set;
- consistent loading, empty, error, retry, and action-failure feedback.

Source-inapplicable actions remain semantically consistent instead of becoming dead buttons. For example, browser opening is absent when there is no HTTP(S) URL, and remote refresh for a purely local manga maps to the existing local rescan/reload behavior.

## Presentation Architecture

`MangaDetailScreen` becomes the only full manga-detail renderer. `OnlineMangaDetailScreen` is removed or reduced to a compatibility wrapper that delegates to the shared renderer; it no longer owns an independent header or chapter list.

The shared renderer receives:

- `MangaDetailUiState` for persisted manga metadata, chapter rows, availability, filters, sort, settings, selection-related inputs, and loading/error state;
- a small `MangaDetailContextUiState` for source name/language, membership state, membership-operation state, refresh state, and optional browser URL;
- explicit callbacks for membership toggle and refresh in addition to the existing edit, category, tracking, cover, notes, chapter, batch, and reading callbacks.

The renderer contains no database, network, downloader, or navigation logic. It renders the same controls for both entry points and delegates every action through callbacks.

## Online Detail Coordinator

A focused online-detail coordinator owns the online-source page lifecycle. It depends on the source manager, online sync service, library repository, downloader, preferences, and the same metadata/category/tracking services used by the library route.

### Successful load

1. Fetch updated manga details from the selected source.
2. Fetch the complete chapter list.
3. Only after both network calls succeed, synchronize a non-favorite local backing record and chapters keyed by `(sourceId, mangaUrl)`.
4. Preserve existing favorite state, category links, notes, custom cover, chapter read/bookmark/download state, history, and reader progress.
5. Load the resulting persisted manga and chapters into the same `MangaDetailUiState` used by the library route.

The backing record is an implementation detail required for stable chapter IDs and full actions. It must not appear in library queries unless `favorite == true`.

### Failed load

If details or chapters fail, no new record, favorite, category link, or empty successful manga is written. An already existing record is left intact, and the shared screen exposes retry using the source request.

### Membership

- Add sets favorite/date-added through the existing idempotent `addOrUpdateOnlineManga()` contract and refreshes shared state.
- Remove uses `removeFromLibrary()`, clearing membership while preserving manga identity, chapters, downloads, read/bookmark state, progress, and history.
- Reading, downloading, refreshing, editing metadata, editing notes, and changing a custom cover never set favorite by themselves.
- Category or tracking actions on a non-favorite manga first show an explicit confirmation explaining that the manga must be added to the library. Confirmation adds it, then continues the requested action; cancellation changes nothing.

## Library Detail Adapter

The existing `LibraryPresenter` remains the library-route state owner. It gains the context state and callbacks needed by the shared header:

- membership is derived from `MangaDetails.favorite`;
- remove/add uses the same online sync service for remote sources and the appropriate local repository mutation for local sources;
- refresh resolves the source, fetches/merges details and chapters, then reloads the selected manga without discarding local state;
- source name/language and browser URL are resolved for the shared metadata/header presentation.

All existing library detail callbacks continue to drive their current repository/downloader/dialog implementations.

## Refresh Merge Rules

Source refresh may replace source-owned fields: title when not locally overridden, author, artist, description, genres, status, source thumbnail URL, chapter names, scanlator, upload date, chapter number, and source order.

Refresh must preserve user-owned or historical fields: favorite/date-added, categories, notes, custom cover, chapter read/bookmark/last-page state, downloads, reader history/progress, viewer/chapter preferences, tracking records, and local edit overrides. Removed remote chapters follow the existing repository retention policy and are not silently deleted by this UI refactor.

## Unified Header and Desktop Layout

The shared page uses the second screenshot's richer structure as the canonical desktop layout:

- a responsive action strip above the hero area;
- cover and metadata in a desktop two-column hero that collapses vertically at narrow widths;
- membership and refresh grouped with the other primary actions rather than floating in a separate online-only toolbar;
- chapter controls and rows identical for both entry points;
- buttons wrap into additional rows before clipping or compressing labels.

The membership action uses a filled treatment because it changes library state. Refresh, browser, editing, categories, and tracking remain secondary actions. The screen keeps existing stable test tags where possible and introduces shared tags for membership and refresh.

## Action Concurrency and Feedback

- Membership and refresh operations have independent busy flags and cannot be double-submitted.
- Chapter actions remain available while unrelated metadata actions run.
- A successful refresh updates the current shared state without navigating away.
- Failures are surfaced in the shared screen with the failed operation identified and a retry path where safe.
- Navigation away cancels screen-owned network work; repository writes already committed remain valid.
- Batch actions operate on stable persisted chapter IDs and reject an empty selection.

## Testing Strategy

Implementation follows red-green-refactor:

1. Add a shared-screen contract test that renders both contexts and asserts the same feature tags exist.
2. Add online coordinator tests proving successful detail/chapter sync creates non-favorite backing data, failures write nothing, and membership remains explicit.
3. Add action-wiring tests for edit, categories, tracking, cover/notes, search/filter/sort/settings, single/batch chapter actions, refresh, browser, membership, and resume reading from the online route.
4. Add library-route tests for shared membership/refresh actions and state preservation after source refresh.
5. Retain regression tests for reversible membership, chapter IDs, progress/history, downloads, local manga behavior, and all existing `MangaDetailScreen` actions.
6. Run focused browse/library UI suites, online sync/downloader tests, all `desktop-app` tests, Spotless, and `git diff --check` with Corretto 23 and one Gradle worker.
7. Build and launch the current desktop distributable against a copied data root. Open the same online manga from Browse and Library, compare feature availability, exercise representative actions, and capture both pages at desktop and narrow widths.

## Acceptance Criteria

- Opening the same remote manga from Browse or Library renders the same shared detail component and feature set.
- No action exists only because one entry point was used; source-inapplicable actions follow the explicit capability rule.
- Online edit/category/tracking/notes/cover and all single/batch chapter actions execute real callbacks rather than placeholder handlers.
- Opening, refreshing, reading, or downloading does not add the manga to the library or assign a category.
- Add/remove membership is explicit, reversible, and preserves chapters, downloads, history, and progress.
- Source refresh preserves user-owned metadata and chapter state.
- Both entry points remain usable at narrow and wide desktop widths.
- Existing library, browse, reader, downloader, backup, and extension-host behavior remains intact.

## Out of Scope

This slice does not redesign the reader, change tracking-provider protocols, alter backup formats, modify extension-host IPC, or redefine local-source storage semantics.
