# Extension image-domain registration

Version: 0.2.6.

The shared page-download function prefers the extension's image pipeline, but runtime image-domain registration previously happened only in its direct HTTP fallback. Extensions returning CDN URLs from page parsing could therefore list chapters successfully while every image download failed with `DOMAIN_DENIED`. Saved download pages had the same problem after restarting the host.

The extension process manager now registers the page's image URL with the owning, loaded extension before invoking `GET_IMAGE`. Both reading and downloading use this entry point. No global domain wildcard is added, and other extensions cannot borrow the discovered host.

Regression coverage uses a real isolated extension and HTTP image fixture on a host absent from its manifest. The initial-image and restored-failed-queue cases failed before the change with the same domain denial. Validation also covers download completion, offline reading, backup roundtrip, session isolation, download recovery, and rejection of unrelated domains.

Download finalization now registers manga and chapter assets in one database transaction. If registration fails, the new pages return to the temporary directory and a previous offline chapter remains intact. Retrying a saved queue also validates and reuses ready images published by an interrupted earlier run, avoiding unnecessary network requests. Regression tests cover a real SQLite foreign-key failure and recovery with the image endpoint unavailable.
