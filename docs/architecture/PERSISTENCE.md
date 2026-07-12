# Reading-List Persistence

Yomori stores imported CBL reading lists in the existing SQLDelight database.

## Core tables

- `reading_lists` stores list-level metadata, warnings, progress, and timestamps.
- `reading_list_entries` stores the authoritative cross-series order and matching state.
- `reading_list_database_references` stores ordered external database evidence from each CBL book.
- `reading_list_sources` stores the ordered user-selected source IDs that a reading list may search.

Original CBL attributes, unknown child elements, parser warnings, series names, issue numbers, volume/year values, and database identifiers are retained. Extensible metadata is encoded as JSON text at the storage boundary and decoded into typed domain collections.

## Invariants

- Entry position is zero-based, contiguous, and matches the original CBL book order.
- A list, its entries, database references, and initial source selection are inserted in one transaction.
- Newly imported entries start in `UNSEARCHED` state.
- Database-reference order is preserved per entry.
- Source IDs are unique per list and their stored position is the search-priority order.
- At least one currently installed online source is required when importing or saving source choices.
- Unavailable source IDs may remain stored so a missing extension can be identified and repaired later.
- Deleting a reading list cascades to its entries, database references, and source selection.
- Progress may only point to a position that exists in the list.
- Source mappings are represented independently from Mihon's normal library membership so candidate searches do not add remote results to the library.

Future matching and repair migrations must preserve original metadata, selected source order, confirmed mappings, overrides, rejections, progress, and failure states.
