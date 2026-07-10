# Matching Cache Behavior

During an import or repair operation, cache equivalent series searches and chapter-list fetches per source. Cache keys must include the source identity and normalized query inputs.

A cache entry is an optimization, not authority. User-confirmed mappings remain the authoritative reusable result. Source errors should have short-lived or operation-scoped caching so a temporary failure does not become a persistent negative match.
