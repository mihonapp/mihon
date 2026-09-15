# Extension temporary files and website rejection, 2026-09-15

Version: 0.2.3. Base revision: 4d4f73939 (0.2.2).

## Findings and changes

- NHentai.xxx's filter-cache writer called `File.createTempFile`, which failed in the Windows AppContainer at `WinNTFileSystem.getNameMax0`. Creating the temporary directory alone did not fix that volume-metadata query. The extension class loader now adapts both static overloads to an NIO implementation within the same sandbox permissions. Original extension packages stay unchanged. Signed classes are not rewritten. Temporary JAR connections are closed so extension reloads do not leave archive handles open.
- EZmanga's API and homepage returned a Cloudflare block page, including in the local desktop browser: `Sorry, you have been blocked`. This was not a solvable challenge page. The application had reduced the result to `HTTP error 403`. The broker now distinguishes a site block from a verification challenge, carries typed status/host data through the extension HTTP helpers and process IPC, and displays a Chinese explanation in Browse. Website access is still blocked; this release does not claim to remove the website's restriction.
- Class-loader regression fixtures use relocated names outside the parent test classpath, ensuring tests exercise the real packaged-extension loading path.

## Verification

- Before the fix, the AppContainer temporary-file test failed with access denied, and the HTTP classification test misclassified the block as authentication required.
- Regression suites passed: desktop network (5), source manager (15), package domains (1), network policy (2), Windows isolation (9), Browse UI (4), host network compatibility (4), broker transport (5), temporary-file contract (1), IPC session (5).
- The archive-handle regression also passed, bringing the focused regression total to 52. The adapted extension temporary-file test passed again after the connection-lifetime fix.
- Real existing `.mext` packages, without reconversion: Everia.club returned 8 popular items; NHentai.xxx returned 25 and wrote `filters.json.zst` (5481 bytes); bilimanga returned 50. All source URLs and broker identities resolved.
- EZmanga returned HTTP 403 with `SITE_BLOCKED` and host `vapi.ezmanga.org`. The live test explicitly asserts this expected external block; it does not count it as successful catalogue access.
- Delivery evidence and profile backup: `desktop-app/build/deliveries/0.2.3/`. Packaged executable and installation evidence are recorded there after release assembly.
