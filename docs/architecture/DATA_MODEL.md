# Reading-List Data Model

This is a planning model. Exact SQLDelight table and column names will be finalized with the first persistence pull request.

## Reading list

Stores list-level identity, source information, import time, format version, and a hash of the imported document.

## Reading-list entry

Stores authoritative order and original CBL metadata alongside optional resolved source, manga, and chapter identifiers. Match state, confidence, margin, and user-confirmation status belong to the entry.

## Series mapping

Stores reusable, user-confirmed relationships between a normalized CBL series tuple and a remote source series.

## Entry override

Stores an explicit source and chapter selection that overrides list and series preferences.

## Rejected candidate

Stores candidates the user rejected so unchanged evidence does not repeatedly produce the same suggestion.

## Invariants

- Entry position is stable and follows CBL `<Book>` order.
- Original CBL values are retained after resolution.
- User-confirmed mappings are protected from automatic replacement.
- Deleting a reading list does not delete unrelated library chapters or global read state.
- Broken remote mappings remain inspectable and repairable.
