# Desktop Library Adaptive Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken horizontal comfortable-grid cards with readable cover-led cards and make every library grid calculate a safe desktop column count from the live content width.

**Architecture:** Add a pure `LibraryGridLayout` sizing boundary that converts available width and the persisted size preference into fixed columns and effective cell width. `LibraryContent` observes its constraints and applies that result to existing Compose grids, while focused card and toolbar composables own desktop-responsive presentation without changing library state or data contracts.

**Tech Stack:** Kotlin, Compose Multiplatform Desktop, Material 3, JUnit 5, Kotest, Compose UI tests, Gradle on Corretto 23.

## Global Constraints

- Preserve `LibraryUiState`, presenter callbacks, stored display-mode names, and the persisted `gridSize` preference range of 120–280 dp.
- Opening online content must not add it to the library or a category; this layout slice changes no membership behavior.
- Support Windows 10 22H2 and Windows 11 x64 desktop resizing.
- Serialize Gradle with `--max-workers=1` and compile with `<JDK 23 path>`.
- Do not claim packaging or runtime completion without a built artifact and launch evidence.

---

## File Structure

- Create `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryGridLayout.kt`: pure mode bounds and width-to-column calculation.
- Create `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryGridLayoutTest.kt`: deterministic sizing and boundary tests.
- Modify `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`: responsive toolbar, fixed grid columns, and vertical comfortable card.
- Modify `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt`: card geometry, text bounds, and resize regressions.

### Task 1: Deterministic adaptive grid sizing

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryGridLayout.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryGridLayoutTest.kt`

**Interfaces:**
- Consumes: `LibraryDisplayMode`, available width in dp, persisted requested width in dp.
- Produces: `internal data class LibraryGridMetrics(val columns: Int, val cellWidthDp: Float)` and `internal fun calculateLibraryGridMetrics(mode: LibraryDisplayMode, availableWidthDp: Float, requestedWidthDp: Float, spacingDp: Float): LibraryGridMetrics`.

- [ ] **Step 1: Write failing sizing tests**

```kotlin
class LibraryGridLayoutTest {
    @Test
    fun `comfortable grid keeps readable cells across desktop widths`() {
        val narrow = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 360f, 180f, 16f)
        val standard = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 1180f, 180f, 16f)
        val ultrawide = calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, 2200f, 180f, 16f)

        narrow.columns shouldBe 2
        standard.columns shouldBe 6
        ultrawide.columns.shouldBeGreaterThan(standard.columns)
        listOf(narrow, standard, ultrawide).forAll { it.cellWidthDp.shouldBeInRange(168f, 224f) }
    }

    @Test
    fun `invalid measurements fall back to one safe comfortable card`() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -1f, 0f).forEach { width ->
            calculateLibraryGridMetrics(LibraryDisplayMode.ComfortableGrid, width, Float.NaN, -1f) shouldBe
                LibraryGridMetrics(columns = 1, cellWidthDp = 180f)
        }
    }

    @Test
    fun `columns never decrease as width grows`() {
        val columns = (320..2400 step 40).map {
            calculateLibraryGridMetrics(LibraryDisplayMode.CompactGrid, it.toFloat(), 180f, 12f).columns
        }
        columns.zipWithNext().forEach { (left, right) -> right.shouldBeGreaterThanOrEqual(left) }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='<JDK 23 path>'
.\gradlew.bat :desktop-app:test --tests '*LibraryGridLayoutTest' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: compilation fails because `LibraryGridMetrics` and `calculateLibraryGridMetrics` do not exist.

- [ ] **Step 3: Implement the pure layout boundary**

Define mode bounds with comfortable `168f..224f`, compact `132f..196f`, and cover-only `112f..176f`. Clamp a finite requested size into the selected bounds, sanitize spacing, compute `floor((available + spacing) / (target + spacing))`, and then increase columns when the resulting width would exceed the maximum. List mode returns one column. Invalid available widths return one column at the mode default target.

```kotlin
internal data class LibraryGridMetrics(
    val columns: Int,
    val cellWidthDp: Float,
)

internal fun calculateLibraryGridMetrics(
    mode: LibraryDisplayMode,
    availableWidthDp: Float,
    requestedWidthDp: Float,
    spacingDp: Float,
): LibraryGridMetrics {
    val bounds = LibraryGridBounds.forMode(mode)
    val target = requestedWidthDp.takeIf(Float::isFinite)?.coerceIn(bounds.minimum, bounds.maximum)
        ?: bounds.default
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) return LibraryGridMetrics(1, bounds.default)
    val spacing = spacingDp.takeIf { it.isFinite() && it >= 0f } ?: 0f
    var columns = floor((availableWidthDp + spacing) / (target + spacing)).toInt().coerceAtLeast(1)
    var width = (availableWidthDp - spacing * (columns - 1)) / columns
    while (width > bounds.maximum) {
        columns += 1
        width = (availableWidthDp - spacing * (columns - 1)) / columns
    }
    while (columns > 1 && width < bounds.minimum) {
        columns -= 1
        width = (availableWidthDp - spacing * (columns - 1)) / columns
    }
    return LibraryGridMetrics(columns, width.coerceAtLeast(bounds.minimum))
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: `LibraryGridLayoutTest` passes with no failures.

- [ ] **Step 5: Commit the sizing boundary**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryGridLayout.kt desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryGridLayoutTest.kt
git commit -m "feat(library): add deterministic desktop grid sizing"
```

### Task 2: Cover-led comfortable cards and adaptive grids

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `calculateLibraryGridMetrics(...)` from Task 1 and unchanged `LibraryManga`/selection callbacks.
- Produces: fixed-column comfortable, compact, and cover-only grids; vertical comfortable cards with stable `library-item-<id>` and `item-select-<id>` tags.

- [ ] **Step 1: Add failing card and resize UI tests**

Extend `setLibraryContent` with `width: Dp = 1000.dp` and replace its `fillMaxSize()` root modifier with `requiredSize(width, 800.dp)`. Then assert that a long comfortable-card title occupies a region wider than 120 dp and that a standard desktop width places at least five cards on its first grid row.

```kotlin
@Test
fun `comfortable card keeps title in a readable horizontal region`() = runComposeUiTest {
    setLibraryContent(
        LibraryUiState(loading = false, items = listOf(manga(42, "A deliberately long desktop manga title"))),
        width = 520.dp,
    )
    val titleBounds = onNodeWithText("A deliberately long desktop manga title").fetchSemanticsNode().boundsInRoot
    titleBounds.width.shouldBeGreaterThan(120f)
}

@Test
fun `comfortable grid adds columns when desktop width grows`() = runComposeUiTest {
    setLibraryContent(
        LibraryUiState(loading = false, items = (1L..12L).map { manga(it, "Manga $it") }),
        width = 1180.dp,
    )
    val bounds = (1L..12L)
        .map { onNodeWithTag("library-item-$it").fetchSemanticsNode().boundsInRoot }
    val firstTop = bounds.minOf { it.top }
    val firstRow = bounds.count { abs(it.top - firstTop) < 1f }
    firstRow.shouldBeGreaterThanOrEqual(5)
}
```

- [ ] **Step 2: Run UI tests and verify RED**

Run:

```powershell
.\gradlew.bat :desktop-app:test --tests '*LibraryScreenTest' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: the narrow card title-width assertion fails against the old horizontal card.

- [ ] **Step 3: Apply fixed columns from live constraints**

Wrap the non-list grid content in `BoxWithConstraints`, calculate metrics from `maxWidth.value`, and use `GridCells.Fixed(metrics.columns)` for each mode. Keep per-mode spacing and bottom padding. Use `Modifier.widthIn(max = metrics.cellWidthDp.dp)` only where a fixed-column cell can exceed the mode maximum because the root is narrower than the readable minimum.

- [ ] **Step 4: Recompose `ComfortableMangaCard` vertically**

Use a portrait `Box` with `aspectRatio(0.68f)` for `MangaCover`, overlay unread and selection badges, then render a bounded metadata column below it. Keep all text max-line limits and ellipsis.

```kotlin
Column {
    Box(Modifier.fillMaxWidth().aspectRatio(0.68f)) {
        MangaCover(..., modifier = Modifier.fillMaxSize())
        if (manga.unreadCount > 0) UnreadBadge(..., Modifier.align(Alignment.TopEnd))
        if (isSelectionMode) SelectionCheckbox(..., Modifier.align(Alignment.TopStart))
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(5.dp)) {
        Text(manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(manga.author ?: "Source ${manga.sourceId}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${manga.chapterCount} ${chapterLabel(manga.chapterCount)}", maxLines = 1)
    }
}
```

- [ ] **Step 5: Run focused UI and sizing tests and verify GREEN**

```powershell
.\gradlew.bat :desktop-app:test --tests '*LibraryGridLayoutTest' --tests '*LibraryScreenTest' --tests '*LibraryCategoryFilterTest' --tests '*LibraryBatchActionsTest' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: all selected tests pass; existing IDs, selection, metadata, and callbacks remain available.

- [ ] **Step 6: Commit adaptive cards**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt
git commit -m "fix(library): adapt shelf cards to desktop widths"
```

### Task 3: Responsive desktop controls and end-to-end verification

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: existing library actions and the grids from Task 2.
- Produces: toolbars that wrap at narrow widths without clipping and runtime evidence at narrow, standard, and ultrawide sizes.

- [ ] **Step 1: Write a failing narrow-toolbar UI test**

Render the library with `setLibraryContent(..., width = 760.dp)` and assert the display-mode chips, grid slider, filter/sort control, multi-select control, category management, and search field all exist and have bounds contained by `library-screen`.

```kotlin
val rootBounds = onNodeWithTag("library-screen").fetchSemanticsNode().boundsInRoot
listOf(
    "display-mode-ComfortableGrid",
    "library-grid-slider",
    "library-filter-sort-button",
    "library-toggle-selection",
    "library-manage-categories-button",
    "library-search",
).forEach { tag ->
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    bounds.left.shouldBeGreaterThanOrEqual(0f)
    bounds.right.shouldBeLessThanOrEqual(rootBounds.right)
}
```

- [ ] **Step 2: Run the narrow-toolbar test and verify RED**

Run the Task 2 Step 2 command. Expected: one or more controls extend beyond the 760 dp root or overlap because the old bars are single rows.

- [ ] **Step 3: Implement responsive control groups**

Use `BoxWithConstraints` in `LibraryPane`. At widths below 980 dp, place actions and control groups in separate full-width rows; above that threshold retain two compact rows. Replace the display chip row and right controls with bounded `FlowRow` groups. Keep category scrolling and anchor management on its own trailing position when space allows.

- [ ] **Step 4: Run all library tests and formatting**

```powershell
.\gradlew.bat :desktop-app:spotlessApply :desktop-app:test --tests 'mihon.desktop.ui.library.*' --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
.\gradlew.bat :desktop-app:spotlessCheck :desktop-app:test --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
git diff --check
```

Expected: both Gradle commands end in `BUILD SUCCESSFUL`; `git diff --check` prints nothing.

- [ ] **Step 5: Build and launch a desktop distributable**

```powershell
.\gradlew.bat :desktop-app:createDistributable --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
& 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe' --portable --data-dir=C:\Temp\MihonWLibraryAdaptive
```

Expected: the distributable is created, `MihonW.exe` remains running, and the library opens using the isolated data directory.

- [ ] **Step 6: Verify actual responsive presentation**

Resize the app to approximately 760×900, 1440×900, and 2200×1100. At each size verify controls remain reachable, text stays horizontal, columns change immediately, cards remain bounded, keyboard focus is visible, and scrolling/selecting/opening a manga works. Capture evidence for the final report.

- [ ] **Step 7: Commit responsive controls and evidence-safe code**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt
git commit -m "feat(library): wrap controls for desktop window sizes"
```
