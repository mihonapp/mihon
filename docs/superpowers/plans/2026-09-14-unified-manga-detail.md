# Unified Manga Detail Implementation Plan

> **For Codex:** Use the executing-plans skill to implement this plan task by task. Keep the work isolated in `codex/unified-manga-detail`, use tests before production changes, and merge the verified branch back into `main`.

**Goal:** Make source-browse and library manga details use the same complete desktop UI and the same working actions, while preserving explicit library membership.

**Architecture:** Persist a successfully loaded online manga and its chapters as a non-favorite backing record, then select that record in the existing `LibraryPresenter` detail pipeline. Split list selection from detail selection so the detail pipeline can observe both favorite and non-favorite records. Pass one `MangaDetailActions` bundle into both library and browse hosts, and extend the shared `MangaDetailScreen` with source refresh and explicit add/remove-library controls. Category and tracking actions on a non-favorite record use a confirmation gate that favorites the record before opening the requested dialog.

**Tech Stack:** Kotlin, Compose Desktop Material 3, coroutines/StateFlow, SQLDelight repository ports, kotlin.test/JUnit, Gradle.

---

### Task 1: Let the shared detail presenter select non-library backing records

**Files:**
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryPresenterTest.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryPresenter.kt`

1. Add a failing presenter test that inserts a manga with `favorite = false`, calls a new unrestricted detail-selection method, and asserts that `detailState` loads the manga and chapters while `state.selectedMangaId` remains unset.
2. Run `./gradlew.bat :desktop-app:test --tests "mihon.desktop.ui.library.LibraryPresenterTest" --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` and confirm the new test fails.
3. Split `selectedMangaId` into library-list selection and `detailMangaId`. Keep library navigation behavior unchanged, add `openMangaDetail(id)` for any persisted record, make the detail flow observe `detailMangaId`, and add a close method that clears the correct state.
4. Add presenter methods to change the current detail record's favorite state and expose a monotonic detail refresh trigger after mutations.
5. Re-run the focused presenter test and commit with `feat(detail): support persisted online manga details`.

### Task 2: Define one action contract and add source/library controls to the shared screen

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailActions.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/MangaDetailScreenTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/MangaDetailScreenActionsTest.kt`

1. Add failing Compose tests for visible refresh and add/remove-library controls, their callbacks, and responsive wrapping of the desktop header action group at constrained widths.
2. Run `./gradlew.bat :desktop-app:test --tests "mihon.desktop.ui.library.MangaDetailScreenTest" --tests "mihon.desktop.ui.library.MangaDetailScreenActionsTest" --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` and confirm the new tests fail.
3. Introduce `MangaDetailActions`, containing the existing edit, category, tracking, chapter, batch, download, settings, refresh, and membership callbacks. Replace duplicated callback lists in the shared hosts with this bundle.
4. Extend `MangaDetailScreen` with an explicit favorite-state action, refresh action/progress state, and a `FlowRow`-based action area that remains usable in narrow split panes and wide standalone views. Keep the existing browser button, cover actions, notes, filters, search, sorting, selection, chapter settings, and resume action.
5. Make both library layouts call the same shared action contract and re-run the focused Compose tests.
6. Commit with `feat(detail): unify shared manga action surface`.

### Task 3: Replace the online-only detail screen with the shared detail pipeline

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/browse/BrowseContentView.kt`
- Delete: `desktop-app/src/main/kotlin/mihon/desktop/ui/browse/OnlineMangaDetailScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/extension/OnlineMangaSyncServiceTest.kt`
- Delete: `desktop-app/src/test/kotlin/mihon/desktop/ui/browse/OnlineMangaDetailScreenTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/browse/UnifiedOnlineMangaDetailTest.kt`

1. Add failing tests proving that a successful source detail load persists manga plus chapters as a non-favorite record, opens that record in the shared detail state, and that a failed source load writes nothing.
2. Add a failing host test proving the online route receives the complete `MangaDetailActions` contract and invokes refresh, membership, edit, category, tracking, read, bookmark, mark-read, single/batch download, delete, filter, sort, search, and settings callbacks through the shared screen.
3. Run the online sync and unified host tests and confirm the new assertions fail.
4. In `BrowseContentView`, replace `OnlineMangaDetailScreen` with: source detail/chapter fetch; `prepareOnlineMangaForReading`; `LibraryPresenter.openMangaDetail`; then `MangaDetailScreen` using the same state and action bundle as the library route. Keep load failures transactional and render the shared error/retry state.
5. Wire refresh to fetch the source again and resync while preserving local read/bookmark/download/custom metadata. Wire membership to explicit favorite toggling only; merely viewing or reading must not add the title to the visible library.
6. Remove the obsolete online-only screen and migrate its useful assertions into the unified tests.
7. Re-run the focused tests and commit with `feat(browse): use unified manga detail page`.

### Task 4: Gate category/tracking organization and preserve every page capability

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/MangaDetailScreenActionsTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/CategoryManagementTest.kt`

1. Add failing tests for the non-favorite organization confirmation: cancel leaves the record non-favorite and opens nothing; confirm favorites it and then opens category or tracking.
2. Implement one pending organization action in the app host and one Material 3 confirmation dialog. Favorite the current backing record only on confirmation, then dispatch the original category/tracking action.
3. Verify the library page also exposes refresh, add/remove membership, and browser navigation where supported, so the feature union is genuinely available on both entry paths.
4. Run the focused tests and commit with `feat(detail): gate library-only organization actions`.

### Task 5: Regression, packaged runtime, screenshots, and merge

**Files:**
- Modify if required by findings: files touched above only
- Evidence: `desktop-app/build/compose/binaries/main/app/`

1. Run the full suite: `./gradlew.bat :desktop-app:test --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` with `JAVA_HOME=C:\Users\18734\.jdks\corretto-23.0.2`.
2. Build the distributable: `./gradlew.bat :desktop-app:createDistributable --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`.
3. Launch the packaged application with an isolated no-space data root. Verify source browse → online detail → refresh → explicit add/remove → category/tracking confirmation → chapter search/filter/sort → bookmark/read/download/delete/batch actions → reader, then verify the same controls from the library detail entry.
4. Capture desktop-width and split-pane screenshots. Check that actions wrap without clipping, the header uses the available width, and both entry paths show the same feature set.
5. Run `git diff --check`, inspect `git status --short`, and commit any verification-driven fixes.
6. Merge `codex/unified-manga-detail` into `main`, re-run the focused unified-detail tests from merged `main`, then remove the worktree and feature branch.
