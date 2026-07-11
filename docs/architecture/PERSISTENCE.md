# Reading-List Persistence

Yomori stores imported CBL reading lists in the existing SQLDelight database.

## Core tables

- `reading_lists` stores list-level metadata, warnings, progress, and timestamps.
- `reading_list_entries` stores the authoritative cross-series order and matching state.
- `reading_list_database_references` stores ordered external database evidence from each CBL book.

Original CBL attributes, unknown child elements, parser warnings, series names, issue numbers, volume/year values, and database identifiers are retained. Extensible metadata is encoded as JSON text at the storage boundary and decoded into typed domain collections.

## Invariants

- Entry position is zero-based, contiguous, and matches the original CBL book order.
- A list and all of its entries and database references are inserted in one transaction.
- Newly imported entries start in `UNSEARCHED` state.
- Database-reference order is preserved per entry.
- Deleting a reading list cascades to its entries and database references.
- Progress may only point to a position that exists in the list.
- Source mappings are represented independently from Mihon's normal library membership so candidate searches do not add remote results to the library.

Future matching and repair migrations must preserve original metadata, confirmed mappings, overrides, rejections, progress, and failure states.
