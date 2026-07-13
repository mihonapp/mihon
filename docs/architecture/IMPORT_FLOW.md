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

## Planned continuation

The next stages resolve distinct series groups through the selected sources, rank issue candidates, present ambiguous or low-confidence matches for review, and save user-confirmed mappings. Resolution must remain resumable after cancellation or source failure without discarding completed confirmations.
