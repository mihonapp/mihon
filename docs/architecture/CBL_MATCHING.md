# CBL Matching Architecture

## Purpose

This document defines the first implementation target for importing Comic Book Lover (`.cbl`) reading lists and resolving them through user-selected Mihon/Tachiyomi-compatible extensions.

The matcher must automate obvious results without hiding ambiguity or taking control away from the user.

## Import flow

1. The user adds extension repositories and installs compatible extensions.
2. The user imports a local CBL file or explicitly supplies a remote CBL URL.
3. Yomori parses and validates the list while preserving the original values.
4. The user chooses the installed extensions that this reading list may query and orders them by preference.
5. Yomori groups entries by normalized series, volume, and year.
6. Each distinct series group is resolved once against the selected extensions.
7. The matched remote series chapter list is fetched once.
8. Individual CBL issues are matched against the retrieved chapters.
9. Results that satisfy both the confidence threshold and ambiguity margin are accepted automatically.
10. Uncertain entries are shown in a candidate-review dialog.
11. Confirmed mappings and rejected candidates are stored for reuse.
12. The reading list remains editable and repairable after import.

## Required CBL fields

The parser must preserve, when present:

- Reading-list name
- Reading-list description or notes
- Entry position
- Series
- Number
- Volume
- Year
- Database name
- External series identifier
- External issue identifier
- Any unrecognized attributes or elements needed for lossless future migration

The order of `<Book>` elements is authoritative.

## Matching stages

### Stage 1: series resolution

Resolve each distinct CBL series tuple before matching issues:

```text
series + volume + year
```

Candidate evidence may include:

- Normalized title similarity
- Exact title aliases confirmed by the user
- Start year or volume agreement
- Author or publisher metadata when a source exposes it
- Source preference rank
- Prior confirmed mappings
- Prior rejected candidates

One confirmed series mapping should resolve all compatible entries in that group without repeating remote searches.

### Stage 2: issue resolution

After a remote series is selected, fetch its chapter list once and match every corresponding CBL entry.

Issue parsing must support at least:

- Integer numbers such as `1` and `001`
- Zero and negative issues
- Decimal issues such as `1.1`
- Suffixes such as `1A`
- Annuals
- Specials
- One-shots
- Free Comic Book Day issues
- Raw names that cannot be represented safely as a floating-point chapter number

The raw CBL number and raw source chapter name must always be retained.

## Confidence calculation

The matcher returns an integer score from 0 through 100 and a score breakdown.

Initial weighting target:

| Evidence | Weight |
| --- | ---: |
| Normalized series-title similarity | 40 |
| Issue-number and issue-type match | 25 |
| Volume or start-year agreement | 15 |
| Publisher or author agreement | 5 |
| Release-date proximity | 5 |
| Source-specific metadata | 5 |
| Confirmed user history | 5 |

Unavailable evidence must not be treated as contradictory evidence. The implementation should normalize the available weights rather than penalizing a source solely because it does not expose optional metadata.

### Default decision rules

```text
auto_accept_threshold = 88
review_threshold = 65
minimum_lead = 10
```

A result may be auto-accepted only when:

```text
best_score >= auto_accept_threshold
and
best_score - second_best_score >= minimum_lead
```

When only one candidate exists, ambiguity handling must still consider weak or incomplete evidence; a sole candidate is not automatically a correct candidate.

All thresholds must be configurable. Advanced settings may expose weight adjustment later, but the first interface should expose certainty and ambiguity controls only.

## Title normalization

Normalization is used to generate comparison forms, never to overwrite displayed metadata.

Initial normalization should account for:

- Unicode normalization
- Case folding
- Repeated whitespace
- Punctuation variants
- Leading articles
- Bracketed publication years
- `Vol`, `Volume`, and equivalent edition markers
- Common publisher or imprint prefixes
- Roman and Arabic volume-number variants where unambiguous

Potentially meaningful subtitles must not be stripped indiscriminately. Normalization should produce multiple comparison tokens or aliases when uncertainty exists.

## Source controls

A reading list queries only explicitly selected installed extensions.

Source preference hierarchy:

1. Entry-level confirmed mapping or source override
2. Series-level confirmed mapping or source override
3. Reading-list source order
4. Global source preference

An entry-level override always wins. A series-level change must offer to update unresolved or automatically matched entries without replacing separately confirmed entry-level mappings.

## Candidate review

For an ambiguous or low-confidence result, display:

- Original CBL series, number, volume, and year
- Candidate title and chapter name
- Source and language
- Overall confidence
- Score breakdown
- Best-versus-second-best margin
- Any missing or conflicting evidence

Required actions:

- Confirm this entry only
- Confirm and apply to the series
- Prefer this source for the series
- Search manually
- Reject the candidate
- Leave unresolved

Rejected candidates must be persisted so they are not repeatedly proposed without new evidence.

## Persistence model

Names are illustrative; final SQLDelight conventions should follow the repository.

```text
reading_list
- id
- name
- description
- source_uri
- imported_at
- format_version
- raw_document_hash

reading_list_entry
- id
- reading_list_id
- position
- cbl_series
- cbl_number
- cbl_volume
- cbl_year
- external_database
- external_series_id
- external_issue_id
- resolved_source_id
- resolved_manga_id
- resolved_chapter_id
- match_state
- match_confidence
- match_margin
- user_confirmed

series_mapping
- normalized_series_key
- source_id
- manga_url
- confirmed_by_user
- updated_at

entry_mapping_override
- reading_list_entry_id
- source_id
- manga_url
- chapter_url
- updated_at

rejected_candidate
- normalized_series_key or reading_list_entry_id
- source_id
- manga_url
- chapter_url when applicable
- evidence_version
```

Store stable source identifiers and remote URLs rather than display names alone.

## Resolution states

```text
UNSEARCHED
SEARCHING
AUTO_MATCHED
USER_CONFIRMED
AMBIGUOUS
UNRESOLVED
SOURCE_UNAVAILABLE
CHAPTER_REMOVED
NEEDS_REMATCH
```

`USER_CONFIRMED` is protected from automatic replacement.

## Library behavior

Remote candidates should remain lightweight until selected. Importing a large CBL must not automatically flood the normal library with every search result.

After resolution, the user may choose whether matched series are added to the visible library and whether a category is created for the reading list.

Chapter read state remains shared with the normal library. Reading-list position and completion remain specific to each reading list.

## Reader integration

A reading-list reading session stores:

- Reading-list identifier
- Current entry position
- Current resolved chapter
- Previous and next resolved entries

At the end of an issue, the next action opens the next resolved reading-list entry, even when it belongs to a different series or source.

Unresolved entries must offer explicit skip, resolve, or stop behavior. They must not be silently omitted from the persisted reading order.

## Failure and repair behavior

When an extension, remote series, or chapter becomes unavailable:

- Keep all CBL metadata and previous mapping data.
- Mark only affected entries.
- Do not rebuild unaffected portions of the list.
- Allow replacement of a source for an entire series.
- Preserve entry-level exceptions.
- Provide a repair operation limited to broken, unresolved, or explicitly selected entries.

## Network behavior

- Bound concurrent requests per source.
- Respect source errors and rate limits.
- Cache series searches and chapter lists during an import session.
- Do not search every installed extension by default.
- Make network activity attributable to a visible import, match, or repair operation.

## Test requirements

At minimum, add tests for:

- CBL order preservation
- Missing and malformed optional fields
- XML parser safety
- Title normalization
- Annual, special, zero, decimal, and suffix issue numbers
- Confidence calculation with missing evidence
- Ambiguity margin behavior
- Threshold customization
- User-confirmed match protection
- Rejected-candidate filtering
- Source-preference hierarchy
- Repair behavior after source removal
