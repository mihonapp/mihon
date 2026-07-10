# Issue Number Normalization

Issue matching must not depend only on Mihon's floating-point `chapter_number`.

## Structured form

Where possible, parse:

- Main numeric component
- Decimal component
- Alphabetic suffix
- Issue type, such as annual, special, one-shot, or FCBD
- Associated year
- Original unmodified value

## Examples

```text
001       -> main 1
0         -> main 0
-1        -> main -1
1.1       -> main 1, decimal 1
1A        -> main 1, suffix A
Annual 1  -> type annual, main 1
FCBD 2012 -> type fcbd, year 2012
```

Normalization produces comparison data only. The original CBL number and source chapter name remain the displayed and persisted evidence.
