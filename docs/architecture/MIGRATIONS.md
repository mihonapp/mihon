# Database Migration Expectations

Reading-list schema changes use SQLDelight migrations and must pass `verifySqlDelightMigration` in GitHub Actions.

Migrations must preserve authoritative CBL order, original metadata, user-confirmed mappings, overrides, rejected candidates, progress, and failure states. Destructive migration is not acceptable for released data without an explicit export-and-recovery path.
