# CBL Parser Safety

The CBL parser must treat imported XML as untrusted input.

## Requirements

- Disable external entities and external DTD loading.
- Reject or safely ignore constructs that require network or local-file resolution.
- Apply reasonable input-size and entry-count limits with actionable errors.
- Preserve book order.
- Preserve recognized values exactly alongside normalized comparison forms.
- Tolerate missing optional attributes.
- Report malformed required structure without partially committing a list.
- Keep unknown metadata in a forward-compatible representation where practical.

Parser tests must include entity-expansion attempts, malformed XML, duplicate attributes where the parser permits them, empty lists, missing fields, and large but valid lists.
