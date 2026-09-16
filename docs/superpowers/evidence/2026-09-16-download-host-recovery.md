# Saved download / isolated host recovery verification

Date: 2026-09-16. Branch: `codex/fix-download-host-recovery`.
Baseline: `3e947006fa6d176a0716b1b439f0ed539aa53181` (0.2.10).

## Finding

The reported message was `145 page(s) failed; page 1: No isolated host registered for source 7698513740234984368`.

The current source already restores the owning extension before image requests and retries an explicit missing-route response once. The missing-route recognition was added by `ba4436e3474b65c1b952f70d503f326f8a7a1e9f`. `DesktopRuntimeFactory` supplies that source manager to the downloader, including when a saved queue already contains its page list.

The installed app's embedded build information is `version=0.2.10`, `revision=3e947006fa6d176a0716b1b439f0ed539aa53181`, `dirty=false`. Its desktop app JAR SHA-256 is `87e92f2bb2378a8c80058376d87fb0a09c94ff7ae84f20acc590324658a68801`.

The user's saved entry was already `COMPLETED` when first inspected. The screenshot's originating session/version remains unconfirmed. This investigation did not reproduce the reported error with the current installation, so it does not introduce an additional production change or claim a new fix for an unobserved cause.

## Added coverage

- A real isolated extension download loses its route immediately before `GET_IMAGE`, after source validation. The downloader must reload the extension, make exactly two image attempts, and finish with one HTTP request. Unrelated extensions and unrelated domains remain denied.
- An opt-in saved-queue smoke test copies preferences and the queue into a fresh directory. It resets only the copy to failed, starts with no loaded extension, retries every page, and verifies completed on-disk output. Installed extension packages are read from their existing paths; downloaded images and host state belong to the temporary directory.

## Verification evidence

The initial source-manager and workflow baseline passed 17 tests with no failures or skips.

The new route-loss regression passed. Temporarily removing only the existing missing-route recognition produced this expected assertion failure:

```text
1 page(s) failed; page 1: No isolated host registered for source 99001
expected: <COMPLETED> but was: <ERROR>
```

The original production file was restored immediately after that mutation check.

Final validation passed 50 tests, with zero failures/errors and one opt-in live test skipped in the default Gradle run (51 tests discovered). That live test was executed separately against the installed application as described below. Coverage included all desktop download tests, source-manager tests, workflow integration, and extension network-session contracts. Command:

```powershell
.\gradlew.bat :desktop-app:spotlessKotlinApply :desktop-app:test --tests 'mihon.desktop.download.*' --tests 'mihon.desktop.extension.SuwayomiWorkflowIntegrationTest' --tests 'mihon.desktop.extension.DesktopSourceManagerTest' --tests 'mihon.desktop.extension.ExtensionNetworkSessionContractTest' --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The live smoke run put the installed application's JARs first on the classpath, used Corretto 23 for its parent test harness, and launched the actual installed `mihondesk.exe` with its bundled runtime as the isolated extension host. It did not substitute freshly compiled application code. The actual Everia Club package and all 145 saved image URLs were exercised without first browsing the source:

```text
DOWNLOAD_SMOKE owner=eu.kanade.tachiyomi.extension.all.everiaclub enabled=true pages=145
DOWNLOAD_SMOKE status=COMPLETED ready=145 bytes=33928038 error=null
```

Process exit was 0. SHA-256 comparison confirmed that the original user's queue remained unchanged. This is a shipped-code and executable-host download check; it does not establish which GUI/session sequence produced the screenshot.

To repeat the opt-in smoke test, set `MIHON_DOWNLOAD_SMOKE_PROFILE` to the source profile, `MIHON_DOWNLOAD_SMOKE_SOURCE` to the affected source ID, and `MIHON_PACKAGED_EXE` to the actual application executable. The normal JUnit entry point runs against checkout application classes. To check installed classes instead, compile the test, then run `mihon.desktop.download.InstalledDownloadRecoverySmokeTest` with installed `app/*` JARs before `desktop-app/build/classes/kotlin/test` on the classpath and a fresh absolute temporary output directory as its sole argument.
