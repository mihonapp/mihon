# CBL Import Flow

The Reading Lists tab is Yomori's entry point for Comic Book Lover (`.cbl`) files.

## Implemented import sequence

1. The user chooses **Import .cbl**.
2. Android's system document picker grants temporary access to the selected file.
3. Yomori reads at most 16 MiB and detects UTF-8, UTF-16 little-endian, or UTF-16 big-endian input.
4. The CBL parser validates XML structure, rejects DTD/entity declarations, preserves book order, and returns typed warnings or failures.
5. Empty reading lists are rejected rather than stored as unusable records.
6. Yomori waits for installed-extension discovery and groups online sources by extension package.
7. The user may search installed extensions and sources and apply a remembered default-language filter.
8. The user chooses at least one currently installed source variant and may arrange the global search-priority order.
9. The reading list, entries, database references, and source order are committed in one SQLDelight transaction.

No source is bundled, recommended, or selected automatically. Later resolution work may query only the user-selected sources stored for that reading list.

## Source editing

A reading list's source selection can be changed after import. Single-variant extensions appear as one compact selectable row. Extensions with more than one visible language variant are collapsed by default; their header shows matching languages and selected count, and expanding the group reveals individual source variants and priority controls.

The language filter is a global remembered preference used for later imports and source edits. A specific language displays matching variants plus language-neutral `all` variants. **All languages** removes the language restriction. Search matches extension names, package names, source names, and language codes. **Select visible** affects only the currently filtered results and preserves hidden selections.

Stored source IDs whose extensions are no longer installed remain visible as unavailable entries so the user can restore the extension or remove the stale choice. Saving still requires at least one installed source.

## List deletion

Each reading-list row exposes a delete action. Deletion requires confirmation and uses the existing database cascade to remove the list, ordered entries, database references, source choices, matching state, and list-specific progress together. It does not delete extensions, remote content, or Mihon's shared chapter read state.

## Import errors

Import failures are translated into user-facing categories:

- empty document;
- file or entry limit exceeded;
- unsafe XML declarations;
- malformed XML;
- invalid CBL structure;
- list without entries;
- inaccessible document;
- unexpected failure.

The original file is never modified, and original CBL metadata retained by the parser is persisted unchanged.

## Candidate search

Each reading-list row exposes an explicit match-search action. Yomori searches only the installed online sources selected for that list, preserves their priority order, groups entries by normalized title plus known year and volume so distinct runs are not merged, shares a three-request limit across simultaneous list searches, and applies a 30-second timeout to every extension request. Entry overrides constrain search before series mappings and reading-list source order; an unavailable override is surfaced instead of being bypassed.

Each source/series search reads only the first result page, retains at most ten series results, fetches issue lists for at most the three strongest series results per source, and stores at most twenty-four ranked issue candidates per entry. Search results remain outside the normal library. Rejected candidates remain persisted for review but are excluded from automatic decisions, confirmed and skipped entries are protected again at the transactional write boundary, unavailable confirmed series mappings are not bypassed, and one failing or timed-out source does not erase results from other selected sources.

## Manual review

Each reading-list row opens a dedicated review screen that loads only the stored reading list and persisted resolution records. Opening, filtering, expanding entries, and browsing candidate evidence perform no extension request or other network activity.

Entries remain in original CBL order and all resolution states stay visible. The screen presents confidence, runner-up lead, decision reason, source and language, remote series and issue identity, complete scoring evidence, conflicts, rejection status, entry overrides, and series mappings. Persisted rejection records remain visible even when a later source refresh no longer returns the corresponding candidate.

Candidate confirmation, rejection, restoration, and series-mapping confirmation or removal are separate explicit actions. Every successful action is committed immediately and the screen reloads repository data, so completed decisions survive navigation, restart, later searches, and unrelated source failures. Review does not add remote series to the normal library.

## Planned continuation

The next stage adds continuous cross-series reader navigation and list-specific progress while preserving unresolved-entry stop, resolve, and skip choices.
