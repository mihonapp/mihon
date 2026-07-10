# Matching Cancellation

Import and repair operations are cancellable. Cancellation stops queued network work, allows in-flight calls to finish or cancel safely, and retains completed parser results and explicit user confirmations.

Cancellation must not commit a partially initialized reading list as if matching completed successfully. The user may resume from a clearly identified incomplete state.
