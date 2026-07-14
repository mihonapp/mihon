# Database Migration Expectations

Reading-list schema changes use SQLDelight migrations and must pass `verifySqlDelightMigration` in GitHub Actions.

Migrations must preserve authoritative CBL order, original metadata, user-confirmed mappings, overrides, rejected candidates, progress, and failure states. Destructive migration is not acceptable for released data without an explicit export-and-recovery path.

Migration `16.sqm` adds candidate snapshots, rejection history, entry overrides, and series mappings. Its foreign keys cascade only records owned by a reading list or entry; it does not alter normal Mihon library membership or extension-facing APIs.
