# Reading-List Normalization

Yomori normalizes CBL metadata and source candidates before confidence scoring. Normalization is deterministic and does not modify the original imported values stored in SQLDelight.

## Titles

Title normalization:

- applies Unicode compatibility normalization;
- lowercases independently of the device locale;
- removes diacritics and normalizes common Latin ligatures;
- treats punctuation and dash variants as word separators;
- treats `&` as `and`;
- collapses repeated whitespace;
- extracts trailing parenthesized years and explicit volume markers such as `Vol. 3`, `Volume IV`, or `v2`;
- retains both the full canonical title and a base title without extracted edition metadata;
- provides an additional comparison key without a leading English article (`the`, `a`, or `an`).

A bare year is not removed because it may be part of the real title. Extracted year and volume values remain separate evidence for later scoring rather than being discarded.

## Issue numbers

Issue-number normalization:

- removes common labels such as `Issue`, `No.`, `Chapter`, and `#`;
- removes limited-series counts such as `(of 6)`;
- removes leading zeroes and insignificant decimal zeroes;
- accepts decimal commas;
- preserves alphabetic suffixes such as `1A`;
- normalizes common exact fractions such as `½`, `1/2`, and `1 1/2`;
- keeps annuals, specials, Free Comic Book Day issues, and one-shots as distinct kinds;
- retains unrecognized identifiers as deterministic opaque values rather than guessing.

An annual numbered `1` is therefore not equivalent to a regular issue `1`. Likewise, `1A` and `1B` remain distinct.

## Matching boundary

Normalization only establishes comparable keys. It does not automatically accept a candidate. Later confidence scoring must still consider title similarity, issue number, year, volume, source evidence, ambiguity margin, and user-confirmed history.
