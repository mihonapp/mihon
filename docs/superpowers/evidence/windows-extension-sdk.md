# Windows Extension SDK and Host Verification Evidence

## Scope
Verification of Phase 4 deliverables under `docs/superpowers/plans/2026-09-06-windows-extension-sdk.md`.

## Test Execution Results
The verification script `scripts/verify-desktop-extensions.ps1` executed all extension unit and integration tests across `:extension-sdk`, `:extension-host`, and `:desktop-app`:

1. **`:extension-sdk` Validation & Contracts:**
   - Manifest serialization and deserialization.
   - Package structure validation, entry limits, and zip slip rejection.
   - Domain pattern and IPv4 matching rules.
   - Status: PASSED.

2. **`:extension-host` Process Isolation & Brokered HTTP:**
   - Standalone CLI entrypoint (`MainKt`).
   - Brokered HTTP client and callback serialization.
   - Isolated classloader for `.mext` execution.
   - Status: PASSED.

3. **Desktop Process Management & Windows Job Object Sandbox:**
   - `WindowsJobObject` memory limit ceiling enforcement (1GB quota).
   - Dynamic JVM classpath resolution during process execution.
   - IPC session framing, query/response correlation, and graceful crash handling.
   - Status: PASSED.

4. **Extension Store Repository & Catalog Service:**
   - Multi-repo persistence in `DesktopPreferenceStore`.
   - Parsing index JSON and deduplicating latest versions.
   - Status: PASSED.

5. **Secure Extension Installer:**
   - SHA-256 verification against catalog hash.
   - Extraction of package assets and icon.
   - Uninstallation and clean directory removal.
   - Status: PASSED.

6. **Brokered HTTP Network Engine:**
   - Enforcing declared network domain whitelists and wildcard patterns.
   - Rejection of unauthorized domains and malicious URLs.
   - Status: PASSED.

7. **Compose UI Screens:**
   - `BrowseScreen`: Sources and Extensions tabs, security confirmation dialog, repository management dialog.
   - `BrowseSourceScreen`: Mode selector (Popular/Latest), search debounce, manga grid cards, pagination controls.
   - `OnlineMangaDetailScreen`: Cover/metadata display, chapter listing, Add to Library action.
   - Status: PASSED.

8. **Online Chapter Reading Integration:**
   - `OnlineChapterSource` adapting extension `PageList` to reader engine with local image disk caching and foreign key integrity in library DB.
   - Status: PASSED.