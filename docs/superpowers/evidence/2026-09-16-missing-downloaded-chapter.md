# Missing downloaded chapter recovery

Date: 2026-09-16. Branch: `codex/fix-download-host-recovery`.

## Observed profile and reproduction

The follow-up screenshot showed the reader's `SOURCE_UNAVAILABLE` message. The affected saved download (chapter 9, source 7698513740234984368) and its database asset both reported a complete directory chapter, but the registered `Gallery` directory was absent. The manga directory still existed. The investigation does not establish which action originally removed the files.

Using the installed 0.2.10 application's JARs, a consistent SQLite backup, and an offline-only reader catalog reproduced `SOURCE_UNAVAILABLE`, with the underlying cause `CreateFileW failed with Win32 error 2` when opening the missing directory. This isolates the offline failure from any online fallback or site availability.

## Profile repair

The previous live download verification had produced all 145 pages from an exact copy of this queue. Its saved input queue still matched the original profile's queue by SHA-256. The verified pages totaled 33,928,038 bytes.

The files were copied to a new sibling staging directory under the original download root. Every copied file's SHA-256 was compared with the verified source. Only after all 145 matched was the staging directory moved to the missing `Gallery` path. The destination was checked to be absent; no existing chapter was overwritten. The database and reading position were not modified.

The installed reader then opened the chapter completely offline:

```text
OFFLINE_READER state=Ready pages=145
OFFLINE_PAGE index=0 width=1283 height=1800
OFFLINE_PAGE index=72 width=1273 height=1800
OFFLINE_PAGE index=144 width=1273 height=1800
```

The harness uses the installed application's JARs and native Skiko/codec resources. Its entry point is `InstalledOfflineReaderSmoke`; it takes a copied database directory and chapter ID. No extension manager or online catalog is supplied.

## Code defect and change

The downloader trusted persisted `COMPLETED` entries even after their files disappeared. Its enqueue deduplication skipped an existing entry unconditionally, so requesting the chapter again could not recover it. Deleting a chapter's files also left its completed queue entry behind.

- On restoring a queue, missing directories or missing/empty expected image files now produce a persisted, retryable error. Surviving pages retain their ready state and are validated/reused by the existing download pipeline when retrying.
- Explicit enqueue detects completed entries whose files disappeared after startup and requeues them. It also requeues existing errors, including the error produced by startup recovery.
- Deleting a completed chapter through the downloader removes its completed queue entry. Single and batch deletion in the detail presenter now use that downloader operation.
- Intact completed chapters are retained without starting network requests. Startup recovery itself does not start downloads.

## Tests

Four added regression cases failed before the production change: missing completed directory, partially missing completed chapter, deletion retaining the completed queue entry, and re-enqueue after removal during the current session. A further regression failed for explicit enqueue after startup had already marked the missing chapter as an error. All now pass. An intact completed chapter is also covered.

A broader download, workflow, library UI, and task-screen run passed 127 tests, with one opt-in live test skipped. After the final enqueue correction, the affected download, presenter, batch-action, and task-screen suites passed 56 tests with zero failures/errors and one opt-in test skipped. The live download and offline reader harnesses were executed separately.

Final targeted command:

```powershell
.\gradlew.bat :desktop-app:spotlessKotlinApply :desktop-app:test --tests 'mihon.desktop.download.*' --tests 'mihon.desktop.ui.library.LibraryPresenterTest' --tests 'mihon.desktop.ui.library.LibraryBatchActionsTest' --tests 'mihon.desktop.ui.tasks.DownloadsScreenTest' --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Raw local verification logs are under `build/download-host-evidence/` and are excluded from Git.

## Application image verification

`spotlessCheck` and `createDistributable` passed. The resulting application image is under `desktop-app/build/compose/binaries/main/app/mihondesk/`, with embedded build information `version=0.2.10`, `revision=ffbfd4aa4412ce74f1d4e717a243ca00d3210ee7`, `dirty=false`.

- Launcher SHA-256: `36590fc50b34e78471fd8af882250730478787ae77f2d0d84406055362b8a76f`.
- Desktop application JAR SHA-256: `f8ebcf4a50986d7fb0ecf7824919a5820b6ea38043338542e1fe168bf6bb1979`.
- Clean-distribution verification passed: no user profile, installed extensions, or saved user configuration in the application image.

Both the old installed EXE and the newly built EXE were launched with `--smoke-test` against separate temporary profiles containing the same completed queue and no downloaded files. Both exited 0. The old EXE left the entry `COMPLETED` with 145 ready pages. The fixed EXE persisted `ERROR`, zero ready pages, and the message `Downloaded files are missing. Retry to download them again.` This verifies the behavior in the actual packaged launchers and bundled runtimes.

The restored real chapter was then read using the new application image's classes and native resources on Java 17. It again opened 145 pages and decoded pages 0, 72, and 144 successfully. A Java 17 check of the installed version also passed after the file restoration.

Original queue SHA-256 remained unchanged, and original chapter 9 retained `last_page_read=95`, `read=0`. The installed application's binary files were not replaced. The preventive code change is available in the branch application image; the original profile's missing chapter files have already been restored.

## Queue, disk, and database reconciliation hardening

Commit `ad96d095e` extends recovery from the original missing-directory case to the other state mismatches found during the follow-up audit.

- Finalization writes an atomic `.mihon-download.json` manifest with page count, size, and modification metadata. Startup decodes legacy or changed pages once, writes or refreshes the manifest, and then uses cheap metadata checks on later launches. Nonempty corrupt images no longer retain `COMPLETED`.
- Startup repairs a valid chapter whose `local_manga_entry` or `local_chapter_asset` row is missing or stale. Missing or corrupt pages remove the stale database asset so the reader can use its online fallback and the UI does not keep a false offline registration.
- Chapter deletion uses the persisted queue identity and path, so later manga/chapter renames do not orphan the old directory. It removes the disk directory, offline database rows, and terminal queue item through one downloader operation.
- Settings cleanup for read chapters now calls the same coordinated deletion operation. Downloaded badges and download-ahead decisions consult the reconciled queue instead of trusting directory existence alone.
- Per-page byte counts are persisted after download and when migrating a legacy completion, removing the previous zero-byte metadata ambiguity.

The final affected-module run passed 61 desktop download/library/task tests (one opt-in live test skipped), all 115 desktop-library-data tests, and all 180 reader-core tests (one skipped): 356 tests, zero failures or errors. `spotlessCheck` passed for both changed modules. A full unfiltered desktop run also exposed two pre-existing baseline issues outside this change: `DiagnosticBundleServiceTest` still expects the old `Mihon W` product name while the product emits `mihondesk`, and `WindowsExtensionIsolationTest.real extension host runs with an AppContainer token` can block in host startup. The affected-module run excludes those two unrelated paths.

`createDistributable` and `verifyCleanDistribution` passed for the code commit. The application image is `desktop-app/build/compose/binaries/main/app/mihondesk/` and contains:

```text
version=0.2.10
revision=ad96d095efe3387a63bf458a65c610ad200c4063
dirty=false
```

- Launcher SHA-256: `36590fc50b34e78471fd8af882250730478787ae77f2d0d84406055362b8a76f`.
- Desktop application JAR SHA-256: `2119171dc651ef304982a912a02cd37d4718bf86c94c7ebdbdf21e84ba076ab3`.

Two isolated launches used the actual application-image `mihondesk.exe` and bundled Java 17 runtime. A saved `COMPLETED` chapter containing a nonempty invalid `001.jpg` exited 0 and persisted `ERROR`, page `ERROR`, and `Downloaded files are missing or corrupt. Retry to download them again.` A valid legacy completion with no manifest or offline asset rows exited 0, remained `COMPLETED`, wrote the manifest, changed `bytesWritten` from 0 to the real 297-byte size, and inserted the matching manga root plus `DIRECTORY` chapter asset in SQLite. The profiles and stdout/stderr are retained under `build/download-host-evidence/integrity-package-20260916-223821/` and `build/download-host-evidence/registration-package-20260916-223940/`.
