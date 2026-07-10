# Matching Examples

## Strong and unambiguous

```text
CBL: Batman (2011) #1
Candidate A: Batman (2011), Chapter 1 — 93%
Candidate B: Batman: The Dark Knight, Chapter 1 — 54%
Margin: 39
```

With default settings, Candidate A may be accepted automatically.

## Strong but ambiguous

```text
CBL: Batman (2011) #1
Candidate A: Batman, Chapter 1 — 92%
Candidate B: Batman (2011), Issue 1 — 88%
Margin: 4
```

The best score exceeds the automatic threshold, but the result requires review because the margin is below the default 10 points.

## Sole weak candidate

```text
CBL: 52 (2006) #1
Candidate A: Fifty-Two, Chapter 1 — 67%
No second candidate
```

A single candidate is not automatically correct. The score remains in the review range.

## User-confirmed mapping

After the user confirms that `Batman`, volume `2011`, maps to a specific remote series, later imports may reuse that mapping. Automatic rescans may report that the target is unavailable, but may not silently replace it.
