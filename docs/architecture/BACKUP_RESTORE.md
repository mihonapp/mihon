# Backup and Restore Expectations

Reading-list backups should retain original CBL metadata, authoritative order, source preferences, series mappings, entry overrides, rejected candidates, progress, and match states.

Restore must tolerate missing extensions and mark affected mappings unavailable rather than deleting them. Credentials, cookies, and extension binaries are not part of reading-list backups.
