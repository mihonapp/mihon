# Reading-List Confidence Scoring

Yomori ranks source candidates using deterministic evidence. Scoring does not modify original CBL metadata and does not add candidates to the normal library.

## Default thresholds

- Automatic match: at least `88%`.
- Manual review: `65%` through `87.99%`.
- Unresolved: below `65%`.
- Required lead over the runner-up: `10` percentage points.
- Minimum title similarity for automatic matching: `85%`.

A candidate must also have an equivalent normalized issue number to be accepted automatically. User-confirmed mappings bypass automatic thresholds and remain authoritative.

## Score components

The initial score uses:

| Evidence | Default effect |
|---|---:|
| Normalized title similarity | up to +58 |
| Equivalent issue number | +30 |
| Matching year | +4 |
| Conflicting year | -6 |
| Matching volume | +4 |
| Conflicting volume | -6 |
| Matching external identifier | +4 |
| Conflicting external identifier | -8 |
| Global source preference | +0.5 |
| Reading-list source preference | +1 |
| Series source preference | +2 |
| Entry source preference | +3 |
| Confirmed source history | +1 |
| Confirmed series history | +3 |

The final score is clamped to `0–100`. Missing metadata is neutral rather than treated as a match or mismatch.

An exact normalized title and equivalent issue number produce the `88%` automatic threshold before optional metadata. Conflicting metadata can lower that result into review. Supporting evidence can improve a fuzzy title result, but it cannot override the issue-equivalence and minimum-title-similarity safety gates.

## Title similarity

Exact normalized title variants score `100%` title similarity. Otherwise Yomori combines:

- normalized Levenshtein character similarity: 55%;
- token Dice similarity: 45%.

The comparison uses the full, edition-free, and article-free keys produced by title normalization.

## Candidate ordering

Candidates are sorted deterministically by:

1. explicit user confirmation;
2. total score;
3. equivalent issue number;
4. title similarity;
5. source-preference level;
6. stable candidate identifier.

Equal high-scoring candidates remain ambiguous rather than being selected by source order alone.

## Decision reasons

The scorer records a reason suitable for manual review:

- no candidates;
- user-confirmed mapping;
- below review threshold;
- below automatic threshold;
- issue mismatch;
- title similarity too weak;
- insufficient lead;
- automatic acceptance.

The complete component breakdown is retained in the domain result so the later review UI can explain exactly why each candidate received its score.

Thresholds and weights are configurable in the domain scorer. User-facing weight customization remains deferred until behavior is validated against real imported lists and representative extension results.
