# CBL Import Flow

The Reading Lists tab is Yomori's entry point for Comic Book Lover (`.cbl`) files.

## Implemented import sequence

1. The user chooses **Import .cbl**.
2. Android's system document picker grants temporary access to the selected file.
3. Yomori reads at most 16 MiB and detects UTF-8, UTF-16 little-endian, or UTF-16 big-endian input.
4. The CBL parser validates XML structure, rejects DTD/entity declarations, preserves book order, and returns typed warnings or failures.
5. Empty reading lists are rejected rather than stored as unusable records.
6. The user chooses at least one currently installed online source and may arrange the search-priority order.
7. The reading list, entries, database references, and source order are committed in one SQLDelight transaction.

No source is bundled, recommended, or selected automatically. Later resolution work may query only the user-selected sources stored for that reading list.

## Source editing

A reading list's source selection can be changed after import. Stored source IDs whose extensions are no longer installed remain visible as unavailable entries so the user can restore the extension or remove the stale choice. Saving still requires at least one installed source.

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
