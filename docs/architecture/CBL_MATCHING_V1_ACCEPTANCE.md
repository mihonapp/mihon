# CBL Matching V1 Acceptance Criteria

The first matching milestone is complete when:

- A valid local CBL file can be parsed without changing entry order.
- Original series, number, volume, year, and external database identifiers are retained.
- Malformed XML fails safely with a user-readable error.
- The user can select and rank installed compatible extensions for a reading list.
- Series groups are searched once per source during an import session.
- Issue candidates receive a deterministic 0–100 confidence score and visible breakdown.
- Automatic acceptance requires both the configured score threshold and ambiguity margin.
- Ambiguous results can be confirmed, rejected, manually searched, or left unresolved.
- Source choices can be set for a list, a series, or an individual entry.
- User-confirmed matches survive automatic rescans.
- Rejected candidates are not immediately offered again without changed evidence.
- Unit tests cover representative CBL and issue-number edge cases.
