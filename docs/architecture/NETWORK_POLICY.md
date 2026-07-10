# Matching Network Policy

CBL matching may query third-party sources only as part of a visible user-initiated import, manual search, or repair operation.

## Rules

- Query only installed extensions selected for the current reading list.
- Apply bounded concurrency separately per source.
- Cache repeated series searches and chapter lists during an operation.
- Surface source errors without treating them as a negative content match.
- Respect retry and rate-limit signals exposed by the source or network layer.
- Allow cancellation and preserve completed results.
- Do not continue matching as an undisclosed background crawler.
- Do not send the complete reading list to a central Yomori service.

Future optional remote metadata services require a separate privacy and architecture decision before implementation.
