# Extension Security Model

Compatible extensions are executable third-party code and must be treated as untrusted.

## Requirements

- Preserve extension signature validation.
- Preserve explicit trust decisions and display the relevant package and signing identity.
- Do not auto-install, auto-enable, or auto-trust extensions.
- Limit matching to extensions selected by the user.
- Do not store source credentials in reading-list mappings or exported diagnostics.
- Redact tokens, cookies, and account identifiers from logs intended for issue reports.
- Treat extension updates that change signing identity as a new trust decision.

CBL metadata is data, not executable configuration. XML parsing must disable external entity expansion and avoid resolving remote entities.
