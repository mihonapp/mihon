# Reading-List Reader Navigation

A Yomori reading session may cross series and source boundaries. It therefore cannot rely solely on Mihon's normal next-chapter ordering within one manga.

## Session state

- Reading-list ID
- Current entry position
- Resolved chapter ID
- Previous and next resolvable entries

## End-of-entry behavior

At the final page, `Next` opens the next reading-list entry rather than the next chapter in the current manga. The destination may belong to another manga or source.

## Missing entries

An unresolved or unavailable entry remains in the authoritative order. The reader offers explicit actions to resolve, skip for this session, or stop. Skipping does not delete the entry.

## Progress

Chapter read state remains shared with the normal library. Reading-list position, skipped state, and list completion are stored per reading list.
