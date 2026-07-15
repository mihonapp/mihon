# Reading-List Manual Review

## Status and purpose

The manual-review interface is implemented as the next Yomori product stage after candidate-search orchestration. It presents persisted reading-list candidates for explicit user decisions while preserving original CBL metadata, confirmed mappings, rejection history, selected-source boundaries, and extension compatibility.

Opening the review interface does not trigger candidate searches or any other extension network request. Candidate refresh remains a separate explicit action on the reading-list row.

## Entry point and ordering

Each reading list exposes a dedicated review/details action while retaining separate source-edit, candidate-search, and delete actions.

The review screen:

- loads the authoritative reading list and persisted resolution data through repositories only;
- displays entries in original CBL `<Book>` order;
- filters between entries needing attention, all entries, and completed entries;
- keeps completed and protected entries visible for context;
- makes `AMBIGUOUS`, `UNRESOLVED`, `SOURCE_UNAVAILABLE`, `CHAPTER_REMOVED`, and `NEEDS_REMATCH` entries easy to find;
- never hides unresolved entries or silently drops unavailable sources or candidates.

Original series, issue number, volume, year, ordered database references, warnings, unknown attributes, and unknown elements remain authoritative and unchanged.

## Candidate presentation

Candidates use the existing deterministic persisted ordering. Equal high-scoring candidates remain visibly ambiguous; reading-list source order only stabilizes display ordering and is not presented as an automatic tie-break.

Each candidate presentation includes:

- total confidence score;
- lead over the runner-up;
- decision reason;
- source name and language;
- remote series and issue identity;
- volume and year evidence;
- title similarity;
- issue-number equivalence;
- external-identifier evidence when available;
- source-preference level;
- confirmed-history evidence;
- complete score-component breakdown;
- conflicts and missing evidence;
- rejected and current-match status.

The original issue text and persisted candidate issue identity remain visible, preserving annual, special, Free Comic Book Day, one-shot, zero or negative, decimal, fraction, suffix, and opaque distinctions.

## Explicit actions

The review interface uses the existing `ReadingListResolutionRepository` contracts. Successful actions are followed by a complete repository reload rather than relying only on transient UI state.

### Confirm entry candidate

Confirmation:

- calls `confirmResolution` with the exact persisted candidate snapshot;
- records the entry as user-confirmed;
- clears only the matching candidate rejection and existing skipped state as defined by the repository;
- never modifies other entries or unrelated rejection history;
- becomes authoritative and cannot be silently replaced by later automatic search.

### Reject candidate

Rejection:

- calls `rejectCandidate` with the exact persisted snapshot;
- remains stored independently from candidate refreshes;
- keeps the candidate visible for review;
- excludes the candidate from later automatic decisions.

### Restore rejected candidate

Restoration:

- calls `clearCandidateRejection` for the exact candidate identity;
- supports both a currently stored candidate and an orphaned rejection whose candidate is no longer returned;
- does not confirm or auto-select the candidate;
- does not clear unrelated rejections.

### Confirm series mapping

A candidate may be promoted to a list-local series mapping only through a separate explicit action. The screen explains the current series mapping and does not make entry confirmation implicitly change it.

Series confirmation:

- calls `confirmSeriesMapping` with the normalized series key plus candidate source and manga identity;
- never occurs implicitly as a side effect of entry confirmation;
- cannot later be replaced by an automatic series mapping;
- remains visible and removable through an explicit action.

### Existing overrides and mappings

Existing entry overrides and series mappings are shown clearly. The review UI does not silently replace, bypass, or reinterpret them. If their source or remote target is unavailable, the entry remains visibly unresolved or unavailable rather than falling back to a lower-priority source.

Advanced override editing remains deferred. Any later implementation must remain explicit and must not broaden source access beyond the reading list's user-selected sources.

## Persistence and resumability

Every successful confirmation, rejection, restoration, or series-mapping action is persisted immediately. The visible state is then rebuilt from the reading-list and resolution repositories.

Completed decisions survive:

- navigation away from the screen;
- app restart;
- cancellation of later work;
- a later source failure;
- a later candidate refresh.

A failed action does not discard earlier successful decisions. The screen surfaces the failure while retaining the last persisted review data.

## Source and library boundaries

- Opening or browsing review data performs no source search or network request.
- Only the existing explicit candidate-search action may query extensions.
- No source, extension repository, or remote result is bundled, recommended, preselected, auto-installed, or auto-trusted.
- Candidate records remain outside Mihon's normal library.
- Reviewing or confirming a candidate does not automatically add a series to the normal library.
- Missing extension IDs and unavailable sources remain visible for later repair.
- Existing extension signature and trust controls remain unchanged.

## Screen-model responsibilities

`ReadingListReviewScreenModel` exposes a deterministic presentation model joining:

- reading-list entries;
- persisted candidate snapshots;
- rejection state, including orphaned rejections;
- entry overrides;
- series mappings;
- original CBL metadata and warnings.

It provides explicit operations for:

- confirming an entry candidate;
- rejecting a candidate;
- restoring a rejected candidate;
- confirming or clearing a series mapping;
- reloading persisted state after each operation.

Loading, missing-list, action-in-progress, and action-failure states are represented without losing previously loaded review data unnecessarily.

## Scope boundaries

This stage does not include:

- cross-series reader navigation or progress behavior;
- repair and rematching workflows beyond the existing explicit candidate-search action;
- automatic source installation or recommendations;
- automatic library insertion;
- user-facing score-weight customization;
- unrelated upstream refactors or rebranding;
- production release, signing, or update-endpoint work.

## Test coverage

Focused tests cover:

- original CBL entry order;
- deterministic persisted candidate order;
- orphaned rejected candidates remaining visible;
- exact persisted candidate confirmation;
- rejection restoration without an active candidate snapshot;
- series confirmation as a separate explicit operation.

Repository and scorer tests continue to cover protected confirmed/skipped writes, rejection independence, series-mapping protection, score thresholds, evidence ordering, issue distinctions, and atomic resolution persistence.

## Validation and QA

Before merge, run focused tests plus `spotlessCheck`, `testDebugUnitTest`, `verifySqlDelightMigration`, development assembly, and `git diff --check`. Inspect the complete diff and changed-file scope, then require authoritative GitHub Actions success.

Device QA should use a development APK and representative imported lists covering ambiguous candidates, low confidence, no candidates, missing extensions, rejected-candidate restoration, entry confirmation, explicit series confirmation, app restart, later candidate refresh, and the supported issue-number distinctions.
