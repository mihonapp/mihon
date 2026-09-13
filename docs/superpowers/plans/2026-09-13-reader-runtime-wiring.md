# Reader Runtime Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route the production desktop reader through its existing bounded prefetch, animation, tiling, visible-page, retry, and cache-diagnostic capabilities with Mihon-equivalent preloading plus desktop-specific cancellation improvements.

**Architecture:** Add a session-owned content pipeline between `DefaultReaderSession` and `DesktopReaderFactory`. It owns one active source and one `PageLoadCoordinator`, while a cancellable adjacent-chapter warmer handles Mihon's end-of-chapter warmup. Compose reports actual visibility and consumes full frames, core-selected animation frames, or viewport tiles from the same pipeline.

**Tech Stack:** Kotlin/JVM, kotlinx.coroutines/Flow, Compose Desktop/Skia, JUnit 5, Kotest assertions, Gradle, PowerShell, Corretto 23.

## Global Constraints

- Work only in `.worktrees/reader-runtime-wiring` on branch `codex/reader-runtime-wiring` until verification passes.
- Match Mihon by preloading four following page images and warming the next chapter inside the final five pages.
- Add one behind-page cache and direction-reversal cancellation for desktop navigation.
- Visible work always has priority; background failures never fail the current page.
- Keep decoded memory inside the existing 256 MiB core budget and Compose copies inside the existing 96 MiB bridge budget.
- Preserve page-image ownership, retry invalidation, progress flush, and chapter-boundary callback contracts.
- Use `C:\Users\18734\.jdks\corretto-23.0.2` for Gradle and always pass `--max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`.
- Do not declare completion until the packaged EXE passes its smoke test and the branch is merged to `main`.

---

### Task 1: Make the prefetch window Mihon-equivalent

**Files:**
- Modify: `reader-core/src/main/kotlin/mihon/reader/prefetch/PrefetchPolicy.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/prefetch/PageLoadCoordinatorTest.kt`

**Interfaces:**
- Consumes: `PrefetchPolicy.plan(pageCount, selectedIndex, mode, direction)`.
- Produces: the same API, returning at most four forward image indexes followed by one behind index.

- [ ] **Step 1: Write failing policy tests**

```kotlin
PrefetchPolicy.plan(10, 3, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD) shouldBe
    listOf(4, 5, 6, 7, 2)
PrefetchPolicy.plan(10, 6, ReadingMode.SINGLE_LTR, NavigationDirection.BACKWARD) shouldBe
    listOf(5, 4, 3, 2, 7)
PrefetchPolicy.plan(10, 2, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD) shouldBe
    listOf(4, 5, 6, 7, 1)
```

- [ ] **Step 2: Verify the tests fail**

Run:

```powershell
$env:JAVA_HOME='C:\Users\18734\.jdks\corretto-23.0.2'
.\gradlew.bat :reader-core:test --tests '*PrefetchPolicyTest' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: assertions show only two single-page forward indexes under the old policy.

- [ ] **Step 3: Implement an image-counted window**

```kotlin
object PrefetchPolicy {
    const val AHEAD_PAGES = 4
    const val BEHIND_PAGES = 1

    fun plan(pageCount: Int, selectedIndex: Int, mode: ReadingMode, direction: NavigationDirection): List<Int> {
        require(pageCount > 0)
        require(selectedIndex in 0 until pageCount)
        val visibleWidth = if (mode.isDualPage) 2 else 1
        val step = if (direction == NavigationDirection.FORWARD) 1 else -1
        val firstAhead = selectedIndex + step * visibleWidth
        val ahead = (0 until AHEAD_PAGES).map { firstAhead + step * it }.filter { it in 0 until pageCount }
        val behind = (1..BEHIND_PAGES).map { selectedIndex - step * it }.filter { it in 0 until pageCount }
        return (ahead + behind).distinct()
    }
}
```

- [ ] **Step 4: Run all cache/prefetch tests**

Run the Task 1 command with `--tests 'mihon.reader.cache.*' --tests 'mihon.reader.prefetch.*'`.

Expected: all selected tests pass and high-water assertions remain within capacity.

- [ ] **Step 5: Commit**

```powershell
git add reader-core/src/main/kotlin/mihon/reader/prefetch/PrefetchPolicy.kt reader-core/src/test/kotlin/mihon/reader/prefetch/PageLoadCoordinatorTest.kt
git commit -m "feat(reader): align image prefetch window with Mihon"
```

### Task 2: Introduce the session content-pipeline contract

**Files:**
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderContentPipeline.kt`
- Modify: `reader-core/src/main/kotlin/mihon/reader/session/DefaultReaderSession.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/session/DefaultReaderSessionTest.kt`

**Interfaces:**
- Produces: `ReaderContentPipeline.open`, `updatePosition`, `loadVisible`, `retry`, `metrics`, `warmAdjacent`, and `closeChapter`.
- Consumes: `ChapterSource`, `PageId`, `ReadingMode`, `NavigationDirection`, and actual visible-page lists.

- [ ] **Step 1: Add a recording fake and failing session tests**

```kotlin
private class RecordingContentPipeline : ReaderContentPipeline {
    val positions = mutableListOf<ReaderContentPosition>()
    var closed = 0
    override suspend fun open(source: ChapterSource) = source.pages()
    override fun updatePosition(position: ReaderContentPosition) { positions += position }
    override suspend fun loadVisible(pageId: PageId) = null
    override suspend fun retry(pageId: PageId) = null
    override fun metrics() = CacheMetrics(32, 16, 1, 2, 3, 4, 0, 32)
    override fun warmAdjacent(request: AdjacentChapterWarmup?) = Unit
    override fun closeChapter() { closed++ }
    override fun close() = closeChapter()
}
```

Assert that open reports the initial position, page selection reports current mode/direction/visible pages, reversal changes direction, state receives metrics, and chapter/session close closes the pipeline once.

- [ ] **Step 2: Verify session tests fail because the contract is absent**

Run `:reader-core:test --tests '*DefaultReaderSessionTest'` with the global Gradle flags.

Expected: compilation fails on `ReaderContentPipeline`.

- [ ] **Step 3: Add the narrow contract**

```kotlin
data class ReaderContentPosition(
    val selectedIndex: Int,
    val visiblePages: List<PageId>,
    val mode: ReadingMode,
    val direction: NavigationDirection,
    val foreground: Boolean,
    val contentVisible: Boolean,
)

data class AdjacentChapterWarmup(val asset: ReaderChapterAsset, val preloadFirstUnit: Boolean)

interface ReaderContentPipeline : AutoCloseable {
    suspend fun open(source: ChapterSource): List<PageDescriptor>
    fun updatePosition(position: ReaderContentPosition)
    suspend fun loadVisible(pageId: PageId): AutoCloseable?
    suspend fun retry(pageId: PageId): AutoCloseable?
    fun metrics(): CacheMetrics
    fun warmAdjacent(request: AdjacentChapterWarmup?)
    fun closeChapter()
}
```

- [ ] **Step 4: Route session open, movement, visibility, retry, metrics, and close through the pipeline**

Use `pipeline.open(opened)` instead of a second direct `opened.pages()` call. After every position-affecting action call `pipeline.updatePosition(...)`, update `ReaderState.cacheMetrics`, and request the actual visible pages. Keep visible-load failures fatal only for explicitly visible content.

- [ ] **Step 5: Run all reader session tests and commit**

Run `:reader-core:test --tests 'mihon.reader.session.*'`; expect all pass.

```powershell
git add reader-core/src/main/kotlin/mihon/reader/session reader-core/src/test/kotlin/mihon/reader/session/DefaultReaderSessionTest.kt
git commit -m "feat(reader): route sessions through content pipeline"
```

### Task 3: Wire the desktop coordinator and next-chapter warmer

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderContentPipeline.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderFactory.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/DesktopReaderContentPipelineTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/reader/DesktopOnlineReaderIntegrationTest.kt`

**Interfaces:**
- Consumes: `ReaderContentPipeline` from Task 2 and `PageLoadCoordinator`.
- Produces: a session-scoped pipeline and `DesktopReaderContent` load methods used by Compose.

- [ ] **Step 1: Write failing integration tests**

Create a counting `ChapterSourceFactory`. Assert initial visible load and four forward prefetches share one source/coordinator, repeated visible access does not re-open online bytes, reversal cancels old prefetch, warmup starts only within five pages, transition warmup decodes the first visible unit, and `closeAndFlush()` closes active and warm sources.

- [ ] **Step 2: Verify the focused desktop tests fail**

Run:

```powershell
.\gradlew.bat :desktop-app:test --tests '*DesktopReaderContentPipelineTest' --tests '*DesktopOnlineReaderIntegrationTest' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: the new pipeline type is missing and the old factory reopens sources per frame.

- [ ] **Step 3: Implement the desktop pipeline**

```kotlin
class DesktopReaderContentPipeline(
    private val coordinator: PageLoadCoordinator,
    private val sourceFactory: ChapterSourceFactory,
    private val scope: CoroutineScope,
) : ReaderContentPipeline {
    private var source: ChapterSource? = null
    private var warmJob: Job? = null

    override suspend fun open(source: ChapterSource): List<PageDescriptor> {
        closeChapter()
        this.source = source
        return coordinator.openChapter(source)
    }

    override fun updatePosition(position: ReaderContentPosition) {
        coordinator.updatePosition(position.selectedIndex, position.visiblePages, position.mode, position.direction)
    }

    override suspend fun loadVisible(pageId: PageId) = coordinator.loadVisible(pageId)
    override suspend fun retry(pageId: PageId) = coordinator.retry(pageId)
    override fun metrics() = coordinator.cacheMetrics
}
```

Complete `warmAdjacent` with a single superseding low-priority job. Enumerate pages within the last-five-page trigger; decode the first visible unit only for a transition-boundary request. Catch non-cancellation warmup failures without modifying reader state.

- [ ] **Step 4: Return a session/content handle from the factory**

Introduce a `DesktopReaderHandle` containing `session: ReaderSession` and `content: DesktopReaderContent`. Keep a compatibility `createSession()` wrapper only for non-UI callers, and make the real reader destination use `createHandle()` so each page uses its own session pipeline.

- [ ] **Step 5: Verify focused tests and commit**

Run the Task 3 focused tests plus `:reader-core:test --tests 'mihon.reader.prefetch.*'`; expect all pass.

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/reader desktop-app/src/test/kotlin/mihon/desktop/reader
git commit -m "feat(reader): connect bounded preloading to desktop runtime"
```

### Task 4: Report actual visible pages and cache metrics

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/PagedReader.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ContinuousReader.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenTest.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/session/DefaultReaderSessionTest.kt`

**Interfaces:**
- Consumes: `ReaderAction.SetVisiblePages` and pipeline metrics from Tasks 2–3.
- Produces: accurate visible sets for single, dual, vertical, and Webtoon layouts.

- [ ] **Step 1: Add failing Compose tests**

Assert dual mode reports two page IDs, a continuous viewport reports only intersecting items, scrolling changes the set, and the cache diagnostic displays non-zero resident/pinned values from session state.

- [ ] **Step 2: Verify focused UI tests fail**

Run `:desktop-app:test --tests '*ReaderScreenTest'`; expect visible-set and metric assertions to fail.

- [ ] **Step 3: Add visibility callbacks**

Add `onVisiblePagesChanged: (List<PageId>) -> Unit` to paged/continuous readers. Use the current spread in paged mode and `LazyListState.layoutInfo.visibleItemsInfo` in continuous modes. In `ReaderScreen`, dispatch only distinct sets:

```kotlin
onVisiblePagesChanged = { ids ->
    if (ids != session.state.value.visiblePages) {
        session.dispatch(ReaderAction.SetVisiblePages(ids))
    }
}
```

- [ ] **Step 4: Verify UI and session tests and commit**

Run `:desktop-app:test --tests '*ReaderScreenTest' :reader-core:test --tests '*DefaultReaderSessionTest'`; expect all pass.

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/ui/reader reader-core/src/test/kotlin/mihon/reader/session desktop-app/src/test/kotlin/mihon/desktop/ui/reader
git commit -m "feat(reader): report visible pages to preload pipeline"
```

### Task 5: Connect core animation timing

**Files:**
- Modify: `reader-core/src/main/kotlin/mihon/reader/session/AnimationCoordinator.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/AnimatedPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/DecodedReaderPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/session/DefaultReaderSessionTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenTest.kt`

**Interfaces:**
- Consumes: session foreground/content visibility and session-owned content frame loader.
- Produces: `AnimationCoordinator.selectedFrame: StateFlow<FrameId?>` consumed by `AnimatedPage`.

- [ ] **Step 1: Add failing coordinator and Compose tests**

Assert frame IDs publish in duration order, pause on foreground/content-visible false, restart at frame zero on retry, cancel when the page leaves the visible set, and never retain more than current plus replacement bridge frames.

- [ ] **Step 2: Verify focused animation tests fail**

Run reader session tests and `:desktop-app:test --tests '*ReaderScreenTest*animation*'`; expect missing `selectedFrame`/production wiring failures.

- [ ] **Step 3: Publish core-selected frames and remove the UI timer**

Add a private `MutableStateFlow<FrameId?>`, expose `StateFlow`, update it immediately before each coordinated frame load, and clear it on cancellation. Replace the loop and `delay()` in `DecodedReaderPage` with static-page loading or `AnimatedPage(selectedFrame = ...)` supplied by the session content handle.

- [ ] **Step 4: Pass real foreground state**

Remove `foreground = true` from `MihonDesktopApp.kt`; use the reader state reported by `ReaderScreen` and visible-set membership for animation activity.

- [ ] **Step 5: Verify animation tests and commit**

Run reader session and reader Compose tests; expect all pass.

```powershell
git add reader-core/src/main/kotlin/mihon/reader/session/AnimationCoordinator.kt desktop-app/src/main/kotlin/mihon/desktop/ui desktop-app/src/test/kotlin/mihon/desktop/ui/reader reader-core/src/test/kotlin/mihon/reader/session
git commit -m "feat(reader): connect coordinated animated page playback"
```

### Task 6: Connect viewport tiling for large zoomed pages

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/TiledReaderPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/DecodedReaderPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderCanvas.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderContentPipeline.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/TiledReaderPageTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/reader/DesktopReaderContentPipelineTest.kt`

**Interfaces:**
- Consumes: `TilePlanner.plan`, viewport/zoom transforms, and `PageLoadCoordinator.loadRegion`.
- Produces: `DesktopReaderContent.loadTiles(pageId, visibleBounds, sampleSize)` and a tiled Compose renderer.

- [ ] **Step 1: Add failing pipeline and Compose tests**

Assert a normal page uses one full-frame request; a large page above `MAX_DISPLAY_PIXELS` at the requested zoom uses region requests; requests include exactly one tile margin; viewport changes cancel stale jobs; replacement tiles swap without a blank frame; disposal closes all bridge leases.

- [ ] **Step 2: Verify tests fail on the old full-frame-only renderer**

Run `:desktop-app:test --tests '*TiledReaderPageTest' --tests '*DesktopReaderContentPipelineTest'`; expect no region loads.

- [ ] **Step 3: Add the region API and renderer**

Use `TilePlanner.plan(...)`, call `coordinator.loadRegion(...)` for each request, bridge each tile once, and draw it at bounds scaled into page coordinates. Key `LaunchedEffect` by page, visible bounds, and sample size so superseded viewport work is cancelled.

- [ ] **Step 4: Keep prefetch bounded and full-page**

Do not create zoom-detail tiles during background prefetch. Confirm `cache.metrics.highWaterBytes` and `memoryBudget.metrics.highWaterBytes` remain within their capacities under rapid zoom tests.

- [ ] **Step 5: Verify image/UI tests and commit**

Run `:reader-core:test --tests 'mihon.reader.image.*' --tests 'mihon.reader.prefetch.*' :desktop-app:test --tests '*TiledReaderPageTest' --tests '*ReaderScreenTest'`; expect all pass.

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/ui/reader desktop-app/src/main/kotlin/mihon/desktop/reader desktop-app/src/test/kotlin/mihon/desktop
git commit -m "feat(reader): render zoomed large images with viewport tiles"
```

### Task 7: End-to-end verification, packaging, and merge evidence

**Files:**
- Create: `docs/superpowers/reports/2026-09-13-reader-runtime-wiring-verification.md`
- Modify only if a failing test exposes a scoped defect in files from Tasks 1–6.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: reproducible test/package/smoke evidence and a merge-ready branch.

- [ ] **Step 1: Run formatting and focused suites**

```powershell
$env:JAVA_HOME='C:\Users\18734\.jdks\corretto-23.0.2'
.\gradlew.bat :reader-core:spotlessCheck :reader-core:test :desktop-app:spotlessCheck :desktop-app:test --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero failed tests, only documented Windows permission-gated skips.

- [ ] **Step 2: Run source hygiene checks**

```powershell
git diff --check main...HEAD
git status --short
```

Expected: no whitespace errors; only the verification report may remain uncommitted.

- [ ] **Step 3: Build the distributable and smoke-test the packaged EXE**

```powershell
.\gradlew.bat :desktop-app:createDistributable --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
& '.\desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe' --smoke-test
if ($LASTEXITCODE -ne 0) { throw "Packaged smoke failed: $LASTEXITCODE" }
```

Expected: the EXE exists and exits with code 0.

- [ ] **Step 4: Record exact evidence and commit**

Record branch/HEAD, suite counts from XML, package path/size/SHA-256, bundled Java version, smoke exit code, and remaining limitations in the report.

```powershell
git add docs/superpowers/reports/2026-09-13-reader-runtime-wiring-verification.md
git commit -m "docs(reader): record runtime wiring verification"
```

- [ ] **Step 5: Merge only after clean verification**

From `D:\my project\mihon-w`:

```powershell
git status --short
git merge --no-ff codex/reader-runtime-wiring
```

Expected: clean `main` containing all implementation and evidence commits. Re-run the packaged EXE smoke from `main` before reporting completion.

