# Windows Shared Data Library — Plan 2 Completion Evidence

Recorded on 2026-09-01 (Asia/Shanghai). Paths below are the paths observed on the verification host; generated build outputs remain reproducible from a clean checkout with the recorded commands.

## Revision and CI scope

- Branch at every recorded run: `feat/windows-shared-data-library`.
- Implementation commit at the start of Task 11 verification: `9dd7ac12b70968034f5a191c12f1f5fc997fe60e` (`test: verify packaged desktop library imports`).
- The Task 11 change to `.github/workflows/build.yml` has exactly three replacements (`git diff --stat` before commit: 3 insertions, 3 deletions): Windows job display name `Build & Test Windows Library`, invocation `.\scripts\verify-desktop-library.ps1`, and artifact name `mihon-w-library-${{ github.sha }}`.
- The Android `build` job, `ubuntu-24.04` runner, all action SHAs, JDK file/distribution, Gradle setup, Android commands, and Android artifact names are unchanged from `9dd7ac12b70968034f5a191c12f1f5fc997fe60e`.
- Plan 2 implementation commits are auditable in Git from `aec9a0b61` through `9dd7ac12b`: schema/data (`aec9a0b61`), repository/lifecycle (`a0f0c1031`, `531e3e6b6`, `351f96df4`), codec (`915fe8411`), Android contract (`f796158fb`, `0eb5f5ebf`), backup import (`f571b9f42`, `1bebbcd2a`), local import and hardening (`7232da55e` through `6aed14dc4`), runtime/CLI (`29a3f52fa`), library UI (`fb3ce5695`), details/import UI (`d39d555a6`, `921b8ab6d`), and packaged verification (`9dd7ac12b`).

## Historical TDD RED evidence for checked plan steps

This section preserves the factual RED summaries from the real local execution records `.superpowers/sdd/task-1-report.md` through `task-10-report.md`. Those reports are ignored working-session records rather than clean-checkout artifacts, so this document does **not** claim that complete raw console logs are committed. Exact excerpts are reproduced only where a report recorded them; otherwise the entry is explicitly labelled as the focused command pattern and observed summary recorded by that report. Every resulting GREEN test named below is committed and is reproducible with the stated command.

### Task 1 — plan Steps 1–2, including line 142

- Failing test authored: `LibrarySchemaTest`, which referenced the not-yet-registered `:desktop-library-data` module and schema.
- Exact RED command actually recorded:

  ```powershell
  ./gradlew :desktop-library-data:test --tests mihon.desktop.library.db.LibrarySchemaTest
  ```

- Expected failure: Gradle could not locate project `:desktop-library-data`.
- Observed failure recorded by Task 1: `Cannot locate tasks that match ':desktop-library-data:test' as project 'desktop-library-data' not found in root project 'Mihon'.` The run ended `BUILD FAILED in 10s`.
- Resulting GREEN evidence: committed `LibrarySchemaTest`; `:desktop-library-data:generateSqlDelightInterface :desktop-library-data:test --tests mihon.desktop.library.db.LibrarySchemaTest` recorded `BUILD SUCCESSFUL`, and the current fresh data suite contains that test among 77 passing tests.

### Task 2 — plan Steps 1–2, including line 559

- Failing tests authored: persistence/reopen, deterministic ordering, unread counts, foreign keys, forced rollback, observation, mutations, tracking identity, reports, and close lifecycle in `SqlDelightLibraryRepositoryTest`.
- Exact RED command actually recorded (with the JDK required by root Gradle configuration):

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  .\gradlew.bat :desktop-library-data:test --tests mihon.desktop.library.db.SqlDelightLibraryRepositoryTest --console=plain
  ```

- Expected failure: repository models, ports, factory, and methods did not exist.
- Observed failure recorded by Task 2: `compileTestKotlin FAILED` with unresolved `model`, `DesktopLibraryDatabaseFactory`, `transaction`, `insertManga`, and `MangaRecord`; `BUILD FAILED in 3s`.
- Resulting GREEN evidence: committed `SqlDelightLibraryRepositoryTest`; the same focused command recorded `BUILD SUCCESSFUL in 5s`. Later review regressions also recorded one deliberate insert-ID RED (`expected false but was true`) followed by the same focused suite passing 8/8. The current fresh data suite passes all 77 tests.

### Task 3 — plan Steps 1–2, including line 661

- Failing tests authored: raw/gzip equivalence, truncated gzip, random ProtoBuf, three JSON signatures, compressed bound, and expanded bound in `AndroidBackupCodecTest`.
- Exact RED command actually recorded:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  .\gradlew.bat :desktop-library-data:test --tests mihon.desktop.library.backup.AndroidBackupCodecTest --console=plain
  ```

- Expected failure: codec, wire DTO graph, limits, and typed exception did not exist.
- Observed failure recorded by Task 3: `compileTestKotlin FAILED` with unresolved `AndroidBackupCodec`, `AndroidBackup`, `BackupLimits`, and `BackupDecodeException`; `BUILD FAILED in 3s`.
- Resulting GREEN evidence: committed `AndroidBackupCodecTest`; the same focused command recorded `BUILD SUCCESSFUL in 5s` with 8/8 tests passing. Those codec tests are included in the current fresh 77-test data suite.

### Task 4 — plan Steps 1–2, including line 825

- Failing contract authored: `DesktopBackupImportContractTest`, using existing Android DTOs/serializer and the desktop decoder.
- Exact focused command actually recorded:

  ```powershell
  ./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest
  ```

- Expected outcome in the plan: a semantic failure only if a desktop serial name, enum/default, or ProtoNumber differed from Android.
- Observed history recorded by Task 4: the shell's Java 17 was rejected before tests; with JBR Java 21, the first compiling attempt failed on an incorrect **test-only** `UpdateStrategy` import. After that import was corrected, both cases passed without a production wire-mirror change, so no invented DTO mismatch is claimed. A later real deliberate RED temporarily omitted source preferences from the comparison and the same command failed 1/2 on `sourcePreferences` (expected `真实来源/42`, received an empty list).
- Resulting GREEN evidence: committed `DesktopBackupImportContractTest`; restoring the source-preference projection made the focused contract pass 2/2. Both cases also passed in the current fresh Android suite and extended verifier.

### Task 5 — plan Steps 1–3, including line 884

- Failing tests authored: validator limits/duplicates/references/JSON bounds plus one-transaction importer, merge, category remapping, preference policy, secrecy, and checkpoint rollback tests.
- Focused RED command pattern actually recorded:

  ```powershell
  ./gradlew :desktop-library-data:test --tests 'mihon.desktop.library.backup.AndroidBackup*Test'
  ```

- Expected failure: validator, supported-preference policy, merge policy, and importer APIs were absent.
- Observed failure recorded by Task 5: Kotlin test compilation reached the new suite and failed on missing `AndroidBackupValidator`, `SupportedPreferencePolicy`, `BackupMergePolicy`, and `AndroidBackupImporter` APIs. A later review RED using the same focused pattern failed four targeted finite-float/atomic-category behaviors before their fix.
- Resulting GREEN evidence: committed `AndroidBackupValidatorTest` and `AndroidBackupImporterTest`; the initial focused suite reached 18/18 after correcting a generated-ID-only assertion, the review-focused suite passed 21/21, and the current fresh data suite passes all 77 tests. The Android real-import contract passes 2/2.

### Task 6 — plan Steps 1–3, including line 989

- Failing tests authored: nine archive formats, Unicode/long paths, symlink/junction/reparse/traversal/collision/unreadable rejection, staging/promotion/report failures, database rollback, orphan cleanup, and later ownership/race hardening regressions.
- Exact focused RED command pattern actually recorded:

  ```powershell
  ./gradlew :desktop-library-data:test --tests 'mihon.desktop.library.local.*Test'
  ```

- Expected failure: local scanner, stager, importer, and their contracts did not exist.
- Observed failure recorded by Task 6: initial test compilation failed because the local scanner/stager/importer APIs were absent. Subsequent real review RED runs are also preserved in the Task 6 report, including absent fault seams, exact-limit marker accounting, absent claim/crash APIs, six callback/hidden-entry failures, two exceptional-cleanup failures, and mixed-state ownership regressions; this document does not claim console excerpts beyond those recorded summaries.
- Resulting GREEN evidence: committed `LocalImportScannerTest` and `LocalMangaImporterTest`; the final Task 6 focused rerun passed 45 tests (9 scanner + 36 importer), 0 failures/errors/skips. The current fresh data suite passes 77/77, including the later integration tests.

### Task 7 — plan Steps 1–2, including line 1051

- Failing tests authored: command parsing, mutual exclusion, runtime ownership, cleanup order, exact JSONL, redaction, lifecycle closure, and packaged smoke behavior.
- Focused command pattern actually run and recorded by Task 7:

  ```powershell
  ./gradlew :desktop-app:test --tests 'mihon.desktop.*Runtime*Test' --tests 'mihon.desktop.cli.*Test'
  ```

- Expected failure: library commands and runtime ownership were not wired.
- Observed failure recorded by Task 7: focused test compilation failed on the absent command model, data dependency, runtime fields, command runner, and execution boundary. Later real REDs found the missing `java.sql` jlink module (packaged smoke exited 1/no database), unobservable GUI-subsystem CLI output, an echoed secret sentinel, and the absent cleanup seam.
- Resulting GREEN evidence: committed runtime/CLI tests; `:desktop-app:spotlessCheck :desktop-app:test :desktop-app:createDistributable --rerun-tasks` recorded 27 tests passing and all 26 actions executed. The current fresh desktop suite passes 47/47, and the extended packaged verifier passes.

### Task 8 — plan Steps 1–3, including line 1127

- Failing tests authored: controlled repository-flow presenter tests plus Compose Library screen/shell routing tests.
- Focused command pattern actually run and recorded by Task 8:

  ```powershell
  ./gradlew :desktop-app:test --tests 'mihon.desktop.ui.library.*Test' --tests mihon.desktop.ui.DesktopShellTest
  ```

- Expected failure: presenter, immutable state, real Library screen, and production route did not exist.
- Observed failure recorded by Task 8: after switching Gradle from Java 17 to the installed Java 23 runtime, compilation failed on missing `LibraryPresenter`, `LibraryUiState`, `LibraryScreen`, the Library shell parameter, and author projection. A later deliberate RED produced two actual failures for synchronous Flow-creation errors and restoring an absent selected ID.
- Resulting GREEN evidence: committed `LibraryPresenterTest`, `LibraryScreenTest`, and `DesktopShellTest`; the focused presenter/UI/shell suite passed 13 tests after edge cases, followed by a 39-test desktop GREEN. The current fresh desktop suite passes 47/47.

### Task 9 — plan Steps 1–3, including line 1201

- Failing tests authored: real detail/chapter Compose tests and chooser/import action tests with controlled fakes.
- Exact focused command pattern actually run and recorded by Task 9:

  ```powershell
  ./gradlew :desktop-app:test --tests 'mihon.desktop.ui.library.MangaDetailScreenTest' --tests 'mihon.desktop.ui.library.LibraryImportActionsTest'
  ```

- Expected failure: detail state/screen and native import action components did not exist.
- Observed failure recorded by Task 9: compilation failed on missing `MangaDetailUiState`, `MangaDetailScreen`, `LibraryImportActions`, `LibraryImportController`, and `ImportActionState`. Later real REDs covered absent detail retry, swallowed cancellation, and raw sensitive report-reason text entering the dialog.
- Resulting GREEN evidence: committed `MangaDetailScreenTest` and `LibraryImportActionsTest`; the focused suite passed after fixes, and `:desktop-app:test --rerun-tasks` recorded 47 tests passing. The current fresh desktop suite again passes 47/47, and the manual packaged run confirms the same real-data path.

### Task 10 — checked test-first/verifier steps

- Failing artifacts authored first: `LibraryImportIntegrationTest`, `DesktopBackupFixtureWriterTest`, and the extended verifier contract.
- Exact initial RED commands recorded by Task 10:

  ```powershell
  ./gradlew :desktop-library-data:test --tests mihon.desktop.library.integration.LibraryImportIntegrationTest
  ./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupFixtureWriterTest -PmihonPlan2FixtureDir=app/build/plan2-fixtures
  pwsh -NoProfile -File .\scripts\verify-desktop-library.ps1
  ```

- Expected/observed failures: both focused Gradle commands reported `No tests found` because the classes did not yet exist; PowerShell reported that the verifier script did not exist. The later checked plan Step 2 expected the newly added integration test to pass before scripting, and it did pass 2/2 before verifier completion.
- Resulting GREEN evidence: committed `LibraryImportIntegrationTest`, `DesktopBackupFixtureWriterTest`, and `scripts/verify-desktop-library.ps1`; integration passed 2/2, the explicit writer passed 1/1 and produced the recorded hash, normal Android tests skipped only the opt-in writer, and the extended verifier printed its final success line.

### Audit result for all checked RED-authoring steps

The checked “write failing tests” steps are paired above with their actual initial RED execution and committed GREEN class: Task 1 schema; Task 2 repository; Task 3 codec; Task 4 Android contract; Task 5 validator/importer; Task 6 scanner/importer; Task 7 runtime/CLI; Task 8 presenter/Compose Library; Task 9 details/import actions; and Task 10 integration/writer/verifier. No additional checked plan step instructs a deliberate RED run. Task 10 Step 2 is explicitly a pre-verifier **PASS** gate and is recorded as such. Therefore all 75 checkboxes remain supported; none is being kept checked solely by an unrecorded failure claim.

## Commands and fresh results

### Complete matrix required by Task 11

Executed from the repository root with Gradle on Android Studio JBR (JDK 21 or newer):

```powershell
.\gradlew.bat spotlessCheck `
  :desktop-library-data:test `
  :desktop-app:test `
  :app:testDebugUnitTest `
  verifySqlDelightMigration `
  :app:assembleDebug `
  :desktop-app:createDistributable
```

Observed result: `BUILD SUCCESSFUL in 1m 2s`; 463 actionable Gradle tasks (83 executed, 1 from cache, 379 up-to-date). `spotlessCheck`, both SQLDelight migration verifiers, Android unit tests, Android debug packaging, and desktop distributable creation all completed without a failure. The only Android output warnings were existing delicate-API/native-strip warnings.

To remove cache/up-to-date ambiguity from the three test gates, this additional fresh run was executed:

```powershell
.\gradlew.bat :desktop-library-data:test :desktop-app:test :app:testDebugUnitTest --rerun-tasks
```

Observed result: `BUILD SUCCESSFUL in 1m 7s`; all 264 actionable tasks executed. JUnit XML totals after that run:

- `desktop-library-data/build/test-results/test`: 8 suites, 77 tests, 0 failures, 0 errors, 0 skipped.
- `desktop-app/build/test-results/test`: 11 suites, 47 tests, 0 failures, 0 errors, 0 skipped.
- `app/build/test-results/testDebugUnitTest`: 3 suites, 9 tests, 0 failures, 0 errors, 1 expected skip. The skipped test is the opt-in fixture writer when its output property is absent; both `DesktopBackupImportContractTest` cases passed.

### Extended packaged verifier

Executed exactly:

```powershell
pwsh -NoProfile -File .\scripts\verify-desktop-library.ps1
```

Observed result: exit code 0. The output contained both required success lines:

- `Mihon W desktop foundation verification passed.`
- `Mihon W desktop library verification passed.`

It also printed `Packaged runtime JAVA_VERSION=17.0.18`, regenerated and verified the Android fixture, ran the Android-to-desktop contract, ran data/UI tests and migration verification, invoked packaged backup/local imports in separate processes, and used a third packaged process to prove both titles and nonempty chapter lists persisted after reopen.

The verifier's observed packaged-process root was `C:\Users\18734\AppData\Local\Temp\mihon-w-library-a36abc23a8cf45c5a2148079c0732795`; its containment check passed and the script removed it. The generated local source path was 288 characters:

```text
C:\Users\18734\AppData\Local\Temp\mihon-w-library-a36abc23a8cf45c5a2148079c0732795\路径段很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长1\路径段很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长2\路径段很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长3\跨平台本地漫画_验证
```

It contained a directory chapter plus `.cbz`, `.rar`, `.7z`, and `.epub` files. The verifier asserted exit 0 and exact `SUCCEEDED` JSON for both imports, then found `跨平台备份` and `跨平台本地漫画_验证` with chapters in a new process.

## Android fixture provenance

- Generator class: `eu.kanade.tachiyomi.data.backup.DesktopBackupFixtureWriterTest`.
- Generator source: `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupFixtureWriterTest.kt`; SHA-256 at verification: `0f0990d1f5c3dc742e959d4eb6437ca5a8affb6356bafb5665dec5b06eeb4fd8`.
- The generator imports the existing Android `Backup` graph, calls `ProtoBuf.encodeToByteArray(Backup.serializer(), fixtureBackup())`, and writes with Okio gzip. It contains no desktop backup DTO encoder.
- Fixture: `app/build/plan2-fixtures/android-generated.tachibk`.
- Fixture SHA-256 and checksum-file content: `5dc104039a1170d53e2bde40833b0ebedaac9d909ae8dc2ffc57742965bbc7d5`.
- Contract gate: `:app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest`; both semantic contract cases passed in the verifier and fresh Android suite.

## Built artifacts

- Packaged executable: `desktop-app/build/compose/binaries/main/app/MihonW/MihonW.exe`, 532,480 bytes, SHA-256 `ad4c8b925492c83c098ec68c3ebc13e6e5b9020859c51415aa70b8aa94720658`.
- Packaged runtime metadata: `desktop-app/build/compose/binaries/main/app/MihonW/runtime/release`; observed `JAVA_VERSION="17.0.18"` and modules include `java.sql`.
- Android APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, 75,060,119 bytes, SHA-256 `383f539011dc5d3497e05c8c6c3fffdf59b0f54cfe29f26ad50d3303abe89b2a`.

## Manual packaged real-UI acceptance

Computer Use controlled the already-built packaged executable; no terminal application was automated. Shell use was limited to fixture preparation and launching the visible app with an explicit argument.

- Clean explicit data root: `C:\Users\18734\AppData\Local\Temp\mihon-w-manual-plan2-834c8731027047d0bf91951ecc3955de`.
- Database actually created and reopened: `C:\Users\18734\AppData\Local\Temp\mihon-w-manual-plan2-834c8731027047d0bf91951ecc3955de\database\library.db`, 114,688 bytes; final observed SHA-256 `2b3725ab5d26995777cd702c29b53acdf31d34024d3b09d6094a1d1bda94f086`.
- Unicode source selected through the application's native directory chooser: `C:\Users\18734\AppData\Local\Temp\mihon-w-manual-plan2-834c8731027047d0bf91951ecc3955de\源漫画\作者名_日本語\跨平台本地漫画_手动验收`.
- Source contents: directory chapter `第 02 话_目录章节/001.jpg` and archive chapter `第 01 话.cbz`.

Observed UI sequence and facts:

1. The first launch showed `Your library is empty` under the actual Library destination.
2. `Import Android backup` opened the native file dialog. Selecting the generated `.tachibk` produced Report 1: manga 1 inserted/0 merged; chapters 1 inserted/0 merged; categories linked 1; preferences 3 imported/3 skipped. The visible skip categories were exactly `APP_STATE, PRIVATE, UNKNOWN`; no preference key or value was displayed.
3. `Import local manga` opened the native directory chooser. Selecting the Unicode source produced Report 2: manga 1 inserted/0 merged; chapters 2 inserted/0 merged; categories linked 0; preferences 0 imported/0 skipped.
4. The Library grid displayed `跨平台备份` (source 42, 1 chapter, 0 unread) and `跨平台本地漫画_手动验收` (source 0, 2 chapters, 2 unread).
5. Searching `备份` left only `跨平台备份`. Its real detail pane showed author `Windows 迁移验证`, category `Android 收藏`, chapter `第 1 话`, and state `Read · Page 7`. The fixture declares `bookmark=false`, so no bookmarked label appeared.
6. Searching `手动验收` left only the Unicode local title. Its real detail pane showed two unread chapters in the repository's deterministic order: `第 02 话_目录章节`, then `第 01 话.cbz`.
7. Every visible chapter reader control was disabled and labelled `Reader arrives in Plan 3`; no reader navigation or page decoding was available.
8. The window was closed through its title-bar close button. A new packaged process was launched with the same explicit data root. The relaunched Library immediately displayed both titles with the same chapter/unread totals, proving persisted reopen behavior in the real UI.

The manual database was then queried read-only through the same Xerial SQLite JDBC driver used by the implementation. Observed row counts:

| Table/semantic object | Count |
| --- | ---: |
| manga | 2 |
| chapter | 3 |
| category | 1 |
| manga_category | 1 |
| history | 1 |
| tracking | 1 |
| preference_snapshot | 3 |
| source_preference_snapshot | 0 |
| import_report | 2 |
| import_report_item | 5 |
| local_manga_entry | 1 |
| local_chapter_asset | 2 |

The chapter query ordered by manga and `source_order DESC` confirmed the backup row was read, not bookmarked, page 7; both local rows were unread, not bookmarked, page 0, with source orders 1 then 0.

## Rollback and failure evidence

The fresh 77-test data suite includes these named, passing rollback/transaction gates (names are read from JUnit XML):

- `SqlDelightLibraryRepositoryTest :: committed library survives reopen and rollback never leaks rows()`
- `SqlDelightLibraryRepositoryTest :: concurrent mutation waits for an uncommitted transaction and commits independently()`
- `AndroidBackupImporterTest :: non-finite backup values never open a transaction or mutate any table()`
- `AndroidBackupImporterTest :: validation failure does not open a transaction or insert a report()`
- `AndroidBackupImporterTest :: checkpoint failure rolls every imported table back byte for byte()`
- `LibraryImportIntegrationTest :: checkpoint failure rolls back on disk before reopen()`
- `LocalMangaImporterTest :: real SQL failure after promotion rolls transaction and owned media back()`
- `LocalMangaImporterTest :: before-report callback replacement survives while database transaction rolls back()`
- `LocalMangaImporterTest :: replacement installed after promotion survives while database transaction rolls back()`

The same suite also covers corrupt/raw/gzip/JSON/expanded-size codec cases, duplicate/reference/limit validation, exact preference classification, non-regressive merge rules, foreign keys, Unicode/long paths, supported local archive extensions, no-follow link/reparse/traversal rejection, ownership races, orphan cleanup, and staged-media compensation. These assertions are in the committed test sources and all 77 tests passed fresh.

## Explicit remaining boundaries

- Page/archive decoding, asset opening, and a functional reader remain **Plan 3**. The disabled `Reader arrives in Plan 3` control is a boundary indicator, not a reader implementation claim.
- Android-compatible backup export and Android-to-Windows-to-Android round-trip remain **Plan 7**. Plan 2 proves Android-generated import compatibility only; it does not claim a Windows backup encoder or export.
- Installer, portable ZIP release delivery, updater/rollback, and clean Windows 10/11 release-machine evidence remain later release work and are not claimed by this slice.

## Reproduction pointers

- Complete plan: `docs/superpowers/plans/2026-09-01-windows-shared-data-library.md`.
- Extended verifier: `scripts/verify-desktop-library.ps1`.
- Data JUnit XML: `desktop-library-data/build/test-results/test` after running the recorded commands.
- Desktop JUnit XML: `desktop-app/build/test-results/test` after running the recorded commands.
- Android JUnit XML: `app/build/test-results/testDebugUnitTest` after running the recorded commands.
- Packaged process/import/reopen assertions and exact cleanup policy are executable in the extended verifier rather than asserted only in this document.
