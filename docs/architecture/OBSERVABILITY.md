# Privacy-Safe Diagnostics

Diagnostics for CBL import and matching should expose enough information to explain failures without logging private content or source credentials.

Permitted diagnostic fields include parser error category, normalized metadata, source identifier, candidate scores, state transitions, request timing, and redacted exception classes.

Do not include cookies, authorization headers, account identifiers, complete downloaded pages, signing secrets, or private CBL URLs containing access tokens.
