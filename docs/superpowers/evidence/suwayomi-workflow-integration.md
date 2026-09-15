# Suwayomi workflow integration — local fixture evidence

Scope: T9. `desktop-app/src/test/kotlin/mihon/desktop/extension/SuwayomiWorkflowIntegrationTest.kt` creates a synthetic `.mext` containing the compiled `WorkflowHttpSource` class and installs it with explicit test trust. It uses a local HTTP server, real AppContainer host/IPC/brokered OkHttp, temporary SQLite databases, actual download files, the production reader factory and production Android-compatible backup codec/importer/exporter. No user profile, library or downloads are used. It is not a real external source or a sanitized user backup.

The passing assertion sequence is installation → HTTP search/details → preview preserves non-favorite state → explicit add preserves manga identity → source chapter/page/image methods → completed download → close extension hosts → offline image decode → reader progress/history → new isolated host fetches newly added HTTP chapter → `.tachibk` export → fresh SQLite import twice preserves source identity, chapters, progress and history without duplicates.

The test uses a real local server, not an HTTP mock interceptor. It deliberately counts HTTP requests around offline reader operations and checks none occur. The server remains available for the subsequent update stage; offline reading is demonstrated by no source manager/network helper supplied to the reader, stopped extension hosts, actual downloaded assets and an unchanged request count. External production manga pages, actual installed EXE UI and Android/Suwayomi application opening the exported backup remain separate verification tasks.

Run in the Suwayomi worktree:

```powershell
.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','mihon.desktop.extension.SuwayomiWorkflowIntegrationTest','--tests','mihon.desktop.extension.WindowsExtensionIsolationTest')
```

## Initial execution and fixes

The first real execution passed installation, brokered search/details, preview and explicit library-add semantics, then reported a real download queue ERROR. The test was updated to include the queue's diagnostic in its assertion before the next run. The same execution exposed a process shutdown race in `SandboxProcess`: the watcher queried an already-closed native process handle (Win32 6). Native exit status is now cached before close and shared with the waiter. No AppContainer, IPC or network stage was replaced with a mock to bypass this failure.

Final result: **PASS**, 1/1 real workflow test in 33.811 s, zero failures/errors/skips and empty stderr. The full combined Gradle run succeeded in 2 min 1 s.

The image failure was narrowed to the host's `File.createTempFile` after HTTP succeeded. A temporary source-side diagnostic reproduced `page-images.mkdirs() == true`, directory exists, and `WinNTFileSystem.getNameMax0` throws access denied during `File.createTempFile`. This JDK 17 path queries volume information. The coordinator owns the Engine/preferences files and replaced both legacy temporary-file calls with NIO `Files.createTempFile`, which generates a unique name and atomically creates the file inside the allowed directory. No volume/root ACL grant was added. The diagnostic fixture code was removed after identifying the cause.

Reference: [OpenJDK 17 TempFileHelper](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/nio/file/TempFileHelper.java). The offline session also iterates all six ReadingMode values and waits for each state transition; this is session-level evidence, not a substitute for visual mode/scrollbar/wheel verification.

## Complementary reader checks

These existing tests cover the non-workflow interaction/format dimensions and are not counted as newly executed in this report. The root coordinator owns their current run and packaged visual evidence:

```powershell
.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','mihon.desktop.reader.input.ReaderInputMapperTest','--tests','mihon.desktop.ui.reader.ReaderGestureTest','--tests','mihon.desktop.ui.reader.ReaderOverlayVisibilityTest','--tests','mihon.desktop.ui.reader.ReaderLayoutTest','--tests','mihon.desktop.ui.reader.TiledReaderPageTest')
.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':reader-core:test','--tests','mihon.reader.image.ApngPageDecoderTest','--tests','mihon.reader.image.TilePlannerTest','--tests','mihon.reader.memory.BoundedReaderMemoryBudgetTest')
```

## Final joint execution

```powershell
.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','mihon.desktop.extension.SuwayomiWorkflowIntegrationTest','--tests','mihon.desktop.extension.WindowsExtensionIsolationTest',':extension-host:test')
```

Executed on Windows, 2026-09-15: BUILD SUCCESSFUL in 2m 1s. Workflow 1/1 (33.811 s), native isolation 6/6 (45.534 s), both with empty stderr. Extension-host baseline: 14 suites, 45 tests, 0 failures/errors, 2 explicit environment skips. Test XML is under each module's `build/test-results/test/`; subsequent test runs can replace those files. The restored database contains one favorite with source 99001, two chapters, saved page index 1 and exactly one history row after two imports. Backup transfers metadata/progress; downloaded image files themselves remain in the original temporary download directory and are not embedded in the backup.
