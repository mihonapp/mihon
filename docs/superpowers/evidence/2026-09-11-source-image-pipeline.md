# Source image pipeline repair — 2026-09-11

## Implementation

- Follow Mihon's HttpPageLoader/Downloader/HttpSource pipeline: resolve image URLs lazily and call the extension's own HttpSource.getImage, preserving its client, imageRequest override and response interceptors.
- Transfer image bytes through bounded temporary files between the packaged extension host and desktop; both online reading and offline downloads use the same pipeline.
- Serialize lazy extension loading to prevent concurrent requests temporarily removing registered sources.
- Add the JSON explicitNulls=false and ProtoBuf bindings needed by J-Novel, plus BitmapFactory/Bitmap compatibility for its image interceptor.
- Resolve the bundled codec from Compose's application resources property.
- Keep downloading remaining pages when one page fails; retain the original failure reason for reporting.

## Verification

Full Gradle regression on September 11: desktop-app 480 tests (6 skipped), extension-host 23 tests (2 skipped), extension-sdk 22 tests; zero failures/errors. Spotless passed for all three modules. Opt-in source tests are run separately against installed .mext packages and the newly built executable, with temporary databases and cache directories.

| Source | Live verification |
| --- | --- |
| NHentai.xxx | All four manifest IDs matched runtime registration; representative 199-page chapter, first/middle/last reader frames decoded at 1280×1850. |
| J-Novel | Search for the exact screenshot title using `Make It Stop`; 14 chapters returned. Selected accessible chapter has 31 pages; pages 1/16/31 decoded at 1441×2048. |
| Bilimanga | Representative 44-page chapter; pages 1/23/44 decoded at 1115×1600, including AVIF handling. |
| Everia.club | Representative 150-page chapter; first image fetched through the real extension pipeline (138684-byte WebP). |
| MangaDex | Representative 26-page chapter; pages 1/14/26 decoded at 2000×2823/2825. Installed GUI also displayed a previously failed page of the 27-page Spanish chapter after retry. Added regression coverage for source headers and the data-saver directory. The transient initial HTTP 404 recovered before those final two changes were installed. |
| EZmanga | Still returns HTTP 403/Cloudflare block from the site/API in this environment, independently reproduced outside the extension. Not claimed fixed. |

J-Novel locked chapters require the source account's normal entitlement. Sampling does not prove every chapter on every remote site is available. Signed image URLs and headers are deliberately omitted from this evidence.

## Upstream references

- https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt
- https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
- https://github.com/mihonapp/mihon/blob/main/source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt

## Delivery

MSI and portable ZIP were rebuilt successfully at `desktop-app/build/compose/binaries/main/msi/MihonW-0.1.3.msi` and `desktop-app/build/compose/binaries/main/portable/MihonW-0.1.3-windows-x64-portable.zip`.

The same-version MSI repair returned 1603 during SecureRepair/CreateShortcuts (elevation unavailable in silent mode). This was not reported as an installer success. With Mihon closed, changed application files were backed up and copied into `C:/Users/18734/AppData/Local/MihonW`; every file from the new distribution was verified against the installed counterpart by SHA-256. The final update completed at 22:48 on September 11. User database, extensions and preferences were not replaced. Backups: `C:/Users/18734/AppData/Local/MihonW-repair-backup-20260911-160855` and `C:/Users/18734/AppData/Local/MihonW-repair-backup-20260911-224845`.

The installed GUI was launched and its executable path verified during the MangaDex check. The final installed extension host was subsequently probed directly: J-Novel returned the screenshot title's 14 chapters, 31 pages for the selected accessible chapter, and a 427956-byte WebP image; EZmanga still returned HTTP 403.

## Follow-up: desktop session propagation

The subsequent investigation reproduced EZmanga's block in an ordinary in-app Chromium browser: `Sorry, you have been blocked / You are unable to access ezmanga.org`, Cloudflare Ray ID `a397973d9d37e376`. No interactive challenge was present. No claim is made that session propagation clears this server-side block.

Two further local defects were reproduced by failing tests and corrected:

- DesktopRuntime now shares the same DesktopCookieStore with the network helper and source manager, and explicitly supplies that store's path to the extension host. Previously the host's direct HTTP client never read desktop session settings.
- The host reads saved domain cookies/custom User-Agent on each network request, so edits and deletion take effect without restarting. Matching uses the most specific domain boundary, adds manual cookies only over HTTPS, and runs at the network-interceptor stage for each redirect. Cookie values are never logged. The host's response cookie jar now respects domain, path, Secure and expiry, and returns a synchronized snapshot.

SourceSessionTest verifies live session removal, unrelated-domain exclusion, HTTPS restriction, explicit cookie preservation, and response-cookie matching. Final follow-up regression: desktop-app 480 tests (6 skipped), extension-host 25 tests (2 skipped), zero failures; both module Spotless checks passed. MSI and portable ZIP rebuilt successfully. At 23:17 the installed application files were updated and all distribution-file hashes verified; changed files were backed up in `C:/Users/18734/AppData/Local/MihonW-repair-backup-20260911-231718`. The installed host then fetched J-Novel's 14 chapters and the selected chapter's 31-page list plus 427956-byte WebP again. No user session credentials were created or imported for this test.

The current upstream EZmanga source still uses `https://vapi.ezmanga.org/api/v1`: https://github.com/keiyoushi/extensions-source/blob/main/src/en/ezmanga/src/eu/kanade/tachiyomi/extension/en/ezmanga/EZmanga.kt . The observed website block remains unresolved.
