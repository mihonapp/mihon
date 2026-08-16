# Panel-to-Panel Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `PANEL_BY_PANEL` reading mode that navigates a manga page one detected panel at a time (zoom/pan per panel, multiple stops on wide/spread panels), similar to GlobalComix's Guided View.

**Architecture:** Generalize the existing dual-page-split pan mechanism (`ReaderPageImageView.canPanRight()`/`panRight()`, consumed by `PagerViewer.moveRight()`/`moveLeft()`) from two hardcoded halves to N detected panel stops. Panel boundaries are found on-device via connected-component analysis on a downsampled page bitmap (no OpenCV, no bundled ML model); wide panels get extra stops from ML Kit on-device text detection, falling back to fixed geometric subdivision. Detected panels are cached in a new SQLDelight table keyed by chapter+page.

**Tech Stack:** Kotlin, Android `SubsamplingScaleImageView` (via `ReaderPageImageView`), SQLDelight (Metro DI, `@Inject`/`@ContributesBinding`), `kotlinx.serialization`, ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`), JUnit 5 + MockK for unit tests.

**Spec:** `docs/superpowers/specs/2026-08-16-panel-to-panel-reader-design.md`

## Global Constraints

- New reader-viewer code lives under package `eu.kanade.tachiyomi.ui.reader.viewer.panel` (new subpackage, matching the existing `viewer.pager` / `viewer.webtoon` / `viewer.navigation` split).
- Pure algorithmic logic (boundary detection, reading order, clustering) must not import `android.*` types, so it can run as a plain JVM unit test — this project has no Robolectric dependency and none should be added for this feature.
- SQLDelight: new tables need both a `.sq` file (current schema) and a numbered `.sqm` migration (`data/src/main/sqldelight/tachiyomi/migrations/14.sqm` — 13 is the current latest). Table/column names are `snake_case`; query files generate `database.<filename>Queries`.
- Metro DI: classes needing DI annotate the class with `@Inject` (constructor injection); classes that must be reachable from manually-constructed classes (like `PagerViewer` subclasses, which are `new`'d directly in `ReadingMode.toViewer()`) must additionally be exposed as a property on `mihon.app.di.AppGraph`.
- New user-facing strings go in `i18n/src/commonMain/moko-resources/base/strings.xml` only (other locales are translated separately, out of scope here).
- Unit tests use JUnit 5 (`org.junit.jupiter.api`) and MockK, matching `app/src/test/java/mihon/core/migration/MigratorTest.kt`. Run with `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.

---

### Task 1: Panel data model

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/Panel.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTest.kt`

**Interfaces:**
- Produces: `PanelDirection` (enum: `LTR`, `RTL`), `PanelRect(left: Float, top: Float, right: Float, bottom: Float)` with `width`/`height` computed properties and `PanelRect.FULL_PAGE` constant, `Panel(bounds: PanelRect, subStops: List<PanelRect> = emptyList())`, `PanelPageData(panels: List<Panel>)`, `fun List<Panel>.flattenToStops(): List<PanelRect>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelTest {

    @Test
    fun `flattenToStops uses subStops when present, otherwise the panel bounds`() {
        val simple = Panel(bounds = PanelRect(0f, 0f, 0.5f, 1f))
        val wide = Panel(
            bounds = PanelRect(0.5f, 0f, 1f, 1f),
            subStops = listOf(
                PanelRect(0.5f, 0f, 0.7f, 1f),
                PanelRect(0.5f, 0f, 1f, 1f),
            ),
        )

        val stops = listOf(simple, wide).flattenToStops()

        assertEquals(
            listOf(
                PanelRect(0f, 0f, 0.5f, 1f),
                PanelRect(0.5f, 0f, 0.7f, 1f),
                PanelRect(0.5f, 0f, 1f, 1f),
            ),
            stops,
        )
    }

    @Test
    fun `PanelRect width and height are computed from bounds`() {
        val rect = PanelRect(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.6f)

        assertEquals(0.6f, rect.width, 0.0001f)
        assertEquals(0.5f, rect.height, 0.0001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelTest"`
Expected: FAIL (compilation error — `Panel`, `PanelRect`, `flattenToStops` don't exist yet)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlinx.serialization.Serializable

enum class PanelDirection { LTR, RTL }

@Serializable
data class PanelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val FULL_PAGE = PanelRect(0f, 0f, 1f, 1f)
    }
}

@Serializable
data class Panel(
    val bounds: PanelRect,
    val subStops: List<PanelRect> = emptyList(),
)

@Serializable
data class PanelPageData(val panels: List<Panel>)

fun List<Panel>.flattenToStops(): List<PanelRect> {
    return flatMap { panel -> panel.subStops.ifEmpty { listOf(panel.bounds) } }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/Panel.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTest.kt
git commit -m "feat(reader): add panel-by-panel data model"
```

---

### Task 2: Panel boundary detector

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PixelBuffer.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelBoundaryDetector.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelBoundaryDetectorTest.kt`

**Interfaces:**
- Consumes: none (pure logic; `PixelBuffer` is a plain framework-independent pixel grid, not `android.graphics.Bitmap`).
- Produces: `class PixelBuffer(width: Int, height: Int, pixels: IntArray)` with `luminanceAt(x: Int, y: Int): Int`; `fun Bitmap.toPixelBuffer(maxDimension: Int): PixelBuffer` (Android glue, used by Task 7); `object PanelBoundaryDetector { fun detect(buffer: PixelBuffer): List<PanelRect> }`.

Detection works by: computing background luminance from the page's border pixels, marking pixels that differ from it as "ink," eroding the ink mask by one pixel (severs thin drawn border lines but leaves thick artwork/bleeding regions connected), then taking connected components of what survives erosion as candidate panels. This means gutter-separated panels stay separate, panels sharing only a thin drawn border still separate (the border erodes away), and panels with no border and bleeding/overlapping art stay merged as one region — with no separate merge step needed.

- [ ] **Step 1: Write the failing test**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelBoundaryDetectorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun buffer(width: Int, height: Int, fill: (x: Int, y: Int) -> Int): PixelBuffer {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = fill(x, y)
            }
        }
        return PixelBuffer(width, height, pixels)
    }

    @Test
    fun `two blocks separated by a wide gutter detect as two panels`() {
        // 30x20 page: a 10x10 black block on the left, a 10x10 black block on the right,
        // separated by a 10px-wide white gutter down the middle.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
            if (inLeftBlock || inRightBlock) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `a single large block detects as one panel`() {
        val page = buffer(30, 20) { x, y -> if (x in 2..27 && y in 2..17) black else white }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `a blank page detects no panels`() {
        val page = buffer(30, 20) { _, _ -> white }

        val panels = PanelBoundaryDetector.detect(page)

        assertTrue(panels.isEmpty())
    }

    @Test
    fun `blocks joined by a thick bridge stay merged as one panel`() {
        // Two 10x10 blocks joined by a bridge that is 4px tall, thick enough to survive erosion.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
            val inBridge = x in 10..19 && y in 8..11
            if (inLeftBlock || inRightBlock || inBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `blocks joined only by a 1px border line separate into two panels`() {
        // Two 10x10 blocks joined by a single 1px-tall line (simulates a shared drawn border
        // with no real gutter), surrounded by white above and below the line.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
            val inThinBridge = x in 10..19 && y == 9
            if (inLeftBlock || inRightBlock || inThinBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelBoundaryDetectorTest"`
Expected: FAIL (compilation error — `PixelBuffer`, `PanelBoundaryDetector` don't exist yet)

- [ ] **Step 3: Write minimal implementation**

`PixelBuffer.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

class PixelBuffer(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixels size must be width * height" }
    }

    fun luminanceAt(x: Int, y: Int): Int {
        val c = pixels[y * width + x]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}

fun Bitmap.toPixelBuffer(maxDimension: Int): PixelBuffer {
    val scale = min(1f, maxDimension.toFloat() / max(width, height))
    val targetWidth = max(1, (width * scale).toInt())
    val targetHeight = max(1, (height * scale).toInt())
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true) else this
    val pixels = IntArray(targetWidth * targetHeight)
    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    if (scaled !== this) scaled.recycle()
    return PixelBuffer(targetWidth, targetHeight, pixels)
}
```

`PanelBoundaryDetector.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.abs

object PanelBoundaryDetector {

    private const val INK_LUMINANCE_DELTA = 40
    private const val MIN_PANEL_AREA_FRACTION = 0.02f
    private const val NOISE_AREA_FRACTION = 0.0005f

    fun detect(buffer: PixelBuffer): List<PanelRect> {
        if (buffer.width < 3 || buffer.height < 3) return emptyList()

        val background = estimateBackgroundLuminance(buffer)
        val isInk = BooleanArray(buffer.width * buffer.height) { i ->
            val x = i % buffer.width
            val y = i / buffer.width
            abs(buffer.luminanceAt(x, y) - background) > INK_LUMINANCE_DELTA
        }

        val eroded = erode(isInk, buffer.width, buffer.height)
        val pageArea = buffer.width * buffer.height
        val noiseFloor = NOISE_AREA_FRACTION * pageArea
        val minArea = MIN_PANEL_AREA_FRACTION * pageArea

        return connectedComponentBoxes(eroded, buffer.width, buffer.height)
            .filter { box -> boxArea(box) >= noiseFloor }
            .filter { box -> boxArea(box) >= minArea }
            .map { box ->
                PanelRect(
                    left = box.left / buffer.width.toFloat(),
                    top = box.top / buffer.height.toFloat(),
                    right = (box.right + 1) / buffer.width.toFloat(),
                    bottom = (box.bottom + 1) / buffer.height.toFloat(),
                )
            }
    }

    private fun estimateBackgroundLuminance(buffer: PixelBuffer): Int {
        val samples = mutableListOf<Int>()
        for (x in 0 until buffer.width) {
            samples += buffer.luminanceAt(x, 0)
            samples += buffer.luminanceAt(x, buffer.height - 1)
        }
        for (y in 0 until buffer.height) {
            samples += buffer.luminanceAt(0, y)
            samples += buffer.luminanceAt(buffer.width - 1, y)
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun erode(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!mask[i]) continue
                var allInk = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height || !mask[ny * width + nx]) {
                            allInk = false
                            break@loop
                        }
                    }
                }
                out[i] = allInk
            }
        }
        return out
    }

    private class MutableBox(var left: Int, var top: Int, var right: Int, var bottom: Int)

    private fun boxArea(box: MutableBox): Int = (box.right - box.left + 1) * (box.bottom - box.top + 1)

    private fun connectedComponentBoxes(mask: BooleanArray, width: Int, height: Int): List<MutableBox> {
        val visited = BooleanArray(mask.size)
        val boxes = mutableListOf<MutableBox>()
        val stack = ArrayDeque<Int>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            visited[start] = true
            stack.addLast(start)
            val box = MutableBox(start % width, start / width, start % width, start / width)

            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % width
                val y = i / width
                if (x < box.left) box.left = x
                if (x > box.right) box.right = x
                if (y < box.top) box.top = y
                if (y > box.bottom) box.bottom = y

                if (x > 0) tryVisit(i - 1, mask, visited, stack)
                if (x < width - 1) tryVisit(i + 1, mask, visited, stack)
                if (y > 0) tryVisit(i - width, mask, visited, stack)
                if (y < height - 1) tryVisit(i + width, mask, visited, stack)
            }
            boxes += box
        }
        return boxes
    }

    private fun tryVisit(i: Int, mask: BooleanArray, visited: BooleanArray, stack: ArrayDeque<Int>) {
        if (mask[i] && !visited[i]) {
            visited[i] = true
            stack.addLast(i)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelBoundaryDetectorTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PixelBuffer.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelBoundaryDetector.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelBoundaryDetectorTest.kt
git commit -m "feat(reader): add connected-component panel boundary detector"
```

---

### Task 3: Panel reading order

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelReadingOrder.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelReadingOrderTest.kt`

**Interfaces:**
- Consumes: `PanelRect`, `PanelDirection` (Task 1).
- Produces: `object PanelReadingOrder { fun sort(rects: List<PanelRect>, direction: PanelDirection): List<PanelRect> }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelReadingOrderTest {

    private val topLeft = PanelRect(0f, 0f, 0.5f, 0.5f)
    private val topRight = PanelRect(0.5f, 0f, 1f, 0.5f)
    private val bottomLeft = PanelRect(0f, 0.5f, 0.5f, 1f)
    private val bottomRight = PanelRect(0.5f, 0.5f, 1f, 1f)

    @Test
    fun `orders a 2x2 grid left-to-right per row for LTR`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.LTR)

        assertEquals(listOf(topLeft, topRight, bottomLeft, bottomRight), ordered)
    }

    @Test
    fun `orders a 2x2 grid right-to-left per row for RTL`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.RTL)

        assertEquals(listOf(topRight, topLeft, bottomRight, bottomLeft), ordered)
    }

    @Test
    fun `a full-width panel on top stays its own row before the split row below`() {
        val topBanner = PanelRect(0f, 0f, 1f, 0.4f)
        val shuffled = listOf(bottomRight, bottomLeft, topBanner)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.LTR)

        assertEquals(listOf(topBanner, bottomLeft, bottomRight), ordered)
    }

    @Test
    fun `empty input returns empty output`() {
        assertEquals(emptyList<PanelRect>(), PanelReadingOrder.sort(emptyList(), PanelDirection.LTR))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelReadingOrderTest"`
Expected: FAIL (compilation error — `PanelReadingOrder` doesn't exist yet)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.max
import kotlin.math.min

object PanelReadingOrder {

    private const val ROW_OVERLAP_THRESHOLD = 0.5f

    fun sort(rects: List<PanelRect>, direction: PanelDirection): List<PanelRect> {
        if (rects.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<PanelRect>>()
        for (rect in rects.sortedBy { it.top }) {
            val row = rows.lastOrNull { verticallyOverlaps(it, rect) }
            if (row != null) {
                row += rect
            } else {
                rows += mutableListOf(rect)
            }
        }

        return rows.flatMap { row ->
            val sorted = row.sortedBy { it.left }
            if (direction == PanelDirection.RTL) sorted.reversed() else sorted
        }
    }

    private fun verticallyOverlaps(row: List<PanelRect>, rect: PanelRect): Boolean {
        val rowTop = row.minOf { it.top }
        val rowBottom = row.maxOf { it.bottom }
        val overlapTop = max(rowTop, rect.top)
        val overlapBottom = min(rowBottom, rect.bottom)
        val overlap = (overlapBottom - overlapTop).coerceAtLeast(0f)
        val shorterHeight = min(rect.height, rowBottom - rowTop)
        return shorterHeight > 0f && overlap >= ROW_OVERLAP_THRESHOLD * shorterHeight
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelReadingOrderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelReadingOrder.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelReadingOrderTest.kt
git commit -m "feat(reader): add direction-aware panel reading order"
```

---

### Task 4: Geometric sub-stop generator

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelSubStopGenerator.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/GeometricPanelSubStopGeneratorTest.kt`

**Interfaces:**
- Consumes: `PanelRect`, `PanelDirection` (Task 1).
- Produces: `interface PanelSubStopGenerator { suspend fun generate(panel: PanelRect, direction: PanelDirection, cropPanel: suspend () -> Bitmap?): List<PanelRect> }`, `object GeometricPanelSubStopGenerator : PanelSubStopGenerator`. (`cropPanel` is unused by the geometric generator but is part of the shared interface — Task 5's text-aware generator uses it and falls back to this one.)

- [ ] **Step 1: Write the failing test**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometricPanelSubStopGeneratorTest {

    @Test
    fun `a narrow panel gets no sub-stops`() = runTest {
        val panel = PanelRect(0f, 0f, 0.4f, 0.5f) // aspect ratio 0.8

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { null }

        assertTrue(stops.isEmpty())
    }

    @Test
    fun `a wide panel gets ordered sub-stops ending with the full panel, LTR`() = runTest {
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f) // aspect ratio 6.0, wide spread

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { null }

        assertEquals(4, stops.size)
        assertEquals(panel, stops.last())
        for (i in 0 until stops.size - 2) {
            assertTrue(stops[i].left < stops[i + 1].left, "stops should move left-to-right")
        }
    }

    @Test
    fun `a wide panel orders sub-stops right-to-left before the full panel, RTL`() = runTest {
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.RTL) { null }

        assertEquals(4, stops.size)
        assertEquals(panel, stops.last())
        for (i in 0 until stops.size - 2) {
            assertTrue(stops[i].left > stops[i + 1].left, "stops should move right-to-left")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.GeometricPanelSubStopGeneratorTest"`
Expected: FAIL (compilation error — `PanelSubStopGenerator`, `GeometricPanelSubStopGenerator` don't exist yet)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap

interface PanelSubStopGenerator {
    /**
     * Returns ordered sub-stops for [panel], or an empty list if it doesn't need any
     * (the panel itself is the only stop). When non-empty, the last stop is always the
     * full [panel] bounds. [cropPanel] lazily crops the panel out of the full-resolution
     * page bitmap, for generators that need to inspect panel content (e.g. OCR).
     */
    suspend fun generate(panel: PanelRect, direction: PanelDirection, cropPanel: suspend () -> Bitmap?): List<PanelRect>
}

object GeometricPanelSubStopGenerator : PanelSubStopGenerator {

    private const val WIDE_ASPECT_THRESHOLD = 2f
    private const val STOP_WIDTH_FRACTION = 0.45f
    private val STOP_CENTERS = listOf(1f / 6f, 3f / 6f, 5f / 6f)

    override suspend fun generate(
        panel: PanelRect,
        direction: PanelDirection,
        cropPanel: suspend () -> Bitmap?,
    ): List<PanelRect> {
        if (panel.height <= 0f || panel.width / panel.height < WIDE_ASPECT_THRESHOLD) return emptyList()

        val stopWidth = panel.width * STOP_WIDTH_FRACTION
        val stops = STOP_CENTERS.map { fraction ->
            val centerX = panel.left + panel.width * fraction
            PanelRect(
                left = (centerX - stopWidth / 2f).coerceAtLeast(panel.left),
                top = panel.top,
                right = (centerX + stopWidth / 2f).coerceAtMost(panel.right),
                bottom = panel.bottom,
            )
        }
        val ordered = if (direction == PanelDirection.RTL) stops.reversed() else stops
        return ordered + panel
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.GeometricPanelSubStopGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelSubStopGenerator.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/GeometricPanelSubStopGeneratorTest.kt
git commit -m "feat(reader): add fixed geometric sub-stop generator for wide panels"
```

---

### Task 5: Text-aware sub-stop generator (ML Kit)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTextClustering.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/TextAwarePanelSubStopGenerator.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTextClusteringTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/TextAwarePanelSubStopGeneratorTest.kt`

**Interfaces:**
- Consumes: `PanelRect`, `PanelDirection`, `PanelSubStopGenerator`, `GeometricPanelSubStopGenerator` (Tasks 1, 4).
- Produces: `object PanelTextClustering { fun clusterByGap(centers: List<PanelRect>, panelWidth: Float): List<List<PanelRect>> }`, `class TextAwarePanelSubStopGenerator(recognizer: TextRecognizer = ...) : PanelSubStopGenerator`.

- [ ] **Step 1: Add the ML Kit dependency**

In `gradle/libs.versions.toml`, add to `[versions]` (after `metrox-viewmodel-compose`, before `moko-resources`):

```toml
mlkit-text-recognition = "16.0.1"
```

Add to `[libraries]` (after `metrox-viewmodel-compose`, before `moko-resources`):

```toml
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }
```

In `app/build.gradle.kts`, after the `implementation(libs.image.decoder)` line (in the "Image loading" block):

```kotlin
    // Panel-by-panel text-block detection
    implementation(libs.mlkit.textRecognition)
```

- [ ] **Step 2: Write the failing test for clustering (pure logic)**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelTextClusteringTest {

    @Test
    fun `text blocks close together form one cluster`() {
        val blocks = listOf(
            PanelRect(0.1f, 0f, 0.2f, 0.1f),
            PanelRect(0.22f, 0f, 0.32f, 0.1f),
        )

        val clusters = PanelTextClustering.clusterByGap(blocks, panelWidth = 1f)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().size)
    }

    @Test
    fun `text blocks far apart form separate clusters`() {
        val blocks = listOf(
            PanelRect(0.05f, 0f, 0.15f, 0.1f),
            PanelRect(0.8f, 0f, 0.9f, 0.1f),
        )

        val clusters = PanelTextClustering.clusterByGap(blocks, panelWidth = 1f)

        assertEquals(2, clusters.size)
    }

    @Test
    fun `empty input produces no clusters`() {
        assertEquals(emptyList<List<PanelRect>>(), PanelTextClustering.clusterByGap(emptyList(), panelWidth = 1f))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelTextClusteringTest"`
Expected: FAIL (compilation error — `PanelTextClustering` doesn't exist yet)

- [ ] **Step 4: Write `PanelTextClustering`**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

object PanelTextClustering {

    private const val CLUSTER_GAP_FRACTION = 0.15f

    /** Groups [rects] (e.g. OCR text-block boxes) into clusters by horizontal center gap. */
    fun clusterByGap(rects: List<PanelRect>, panelWidth: Float): List<List<PanelRect>> {
        if (rects.isEmpty()) return emptyList()

        val sorted = rects.sortedBy { centerX(it) }
        val clusters = mutableListOf(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            val prevCenter = centerX(clusters.last().last())
            val center = centerX(sorted[i])
            if (center - prevCenter > CLUSTER_GAP_FRACTION * panelWidth) {
                clusters += mutableListOf(sorted[i])
            } else {
                clusters.last() += sorted[i]
            }
        }
        return clusters
    }

    private fun centerX(rect: PanelRect): Float = (rect.left + rect.right) / 2f
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelTextClusteringTest"`
Expected: PASS

- [ ] **Step 6: Write the failing test for the generator (mocked ML Kit)**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextAwarePanelSubStopGeneratorTest {

    private fun textBlock(box: Rect): Text.TextBlock = mockk {
        every { boundingBox } returns box
    }

    private fun textResult(blocks: List<Text.TextBlock>): Text = mockk {
        every { textBlocks } returns blocks
    }

    @Test
    fun `narrow panels never call the recognizer`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.4f, 0.5f)

        val stops = generator.generate(panel, PanelDirection.LTR) { mockk() }

        assertTrue(stops.isEmpty())
    }

    @Test
    fun `wide panel with no detected text falls back to geometric stops`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        every { recognizer.process(any()) } returns Tasks.forResult(textResult(emptyList()))
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)
        val bitmap = mockk<Bitmap> {
            every { width } returns 900
            every { height } returns 150
        }

        val stops = generator.generate(panel, PanelDirection.LTR) { bitmap }

        val expected = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { bitmap }
        assertEquals(expected, stops)
    }

    @Test
    fun `wide panel with two separated text blocks produces two stops plus the full reveal`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        val blocks = listOf(
            textBlock(Rect(50, 20, 150, 100)),
            textBlock(Rect(700, 20, 800, 100)),
        )
        every { recognizer.process(any()) } returns Tasks.forResult(textResult(blocks))
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)
        val bitmap = mockk<Bitmap> {
            every { width } returns 900
            every { height } returns 150
        }

        val stops = generator.generate(panel, PanelDirection.LTR) { bitmap }

        assertEquals(3, stops.size)
        assertEquals(panel, stops.last())
        assertTrue(stops[0].left < stops[1].left)
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.TextAwarePanelSubStopGeneratorTest"`
Expected: FAIL (compilation error — `TextAwarePanelSubStopGenerator` doesn't exist yet)

- [ ] **Step 8: Write `TextAwarePanelSubStopGenerator`**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sub-stops for a wide panel based on where its dialogue text is, using on-device OCR
 * to find text-block bounding boxes rather than a trained bubble-detection model. Falls
 * back to [GeometricPanelSubStopGenerator] whenever OCR finds nothing or times out.
 */
class TextAwarePanelSubStopGenerator(
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : PanelSubStopGenerator {

    override suspend fun generate(
        panel: PanelRect,
        direction: PanelDirection,
        cropPanel: suspend () -> Bitmap?,
    ): List<PanelRect> {
        if (panel.height <= 0f || panel.width / panel.height < WIDE_ASPECT_THRESHOLD) return emptyList()

        val bitmap = cropPanel() ?: return GeometricPanelSubStopGenerator.generate(panel, direction, cropPanel)
        val textBlocks = detectTextBlocks(bitmap, panel)
        if (textBlocks.isEmpty()) return GeometricPanelSubStopGenerator.generate(panel, direction, cropPanel)

        val clusters = PanelTextClustering.clusterByGap(textBlocks, panel.width).take(MAX_STOPS)
        val stops = clusters.map { cluster -> boundingRect(cluster, panel) }
        val ordered = if (direction == PanelDirection.RTL) stops.reversed() else stops
        return ordered + panel
    }

    private suspend fun detectTextBlocks(bitmap: Bitmap, panel: PanelRect): List<PanelRect> {
        val text = withTimeoutOrNull(OCR_TIMEOUT_MS) { recognize(bitmap) } ?: return emptyList()
        return text.textBlocks.mapNotNull { block ->
            block.boundingBox?.let { toPanelLocalRect(it, bitmap.width, bitmap.height, panel) }
        }
    }

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun toPanelLocalRect(box: Rect, bitmapWidth: Int, bitmapHeight: Int, panel: PanelRect): PanelRect {
        return PanelRect(
            left = panel.left + panel.width * (box.left / bitmapWidth.toFloat()),
            top = panel.top + panel.height * (box.top / bitmapHeight.toFloat()),
            right = panel.left + panel.width * (box.right / bitmapWidth.toFloat()),
            bottom = panel.top + panel.height * (box.bottom / bitmapHeight.toFloat()),
        )
    }

    private fun boundingRect(cluster: List<PanelRect>, panel: PanelRect): PanelRect {
        val padding = 0.05f * panel.width
        return PanelRect(
            left = (cluster.minOf { it.left } - padding).coerceAtLeast(panel.left),
            top = panel.top,
            right = (cluster.maxOf { it.right } + padding).coerceAtMost(panel.right),
            bottom = panel.bottom,
        )
    }

    companion object {
        private const val WIDE_ASPECT_THRESHOLD = 2f
        private const val OCR_TIMEOUT_MS = 3000L
        private const val MAX_STOPS = 4
    }
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.TextAwarePanelSubStopGeneratorTest" --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelTextClusteringTest"`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTextClustering.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/TextAwarePanelSubStopGenerator.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelTextClusteringTest.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/TextAwarePanelSubStopGeneratorTest.kt
git commit -m "feat(reader): add ML Kit text-aware sub-stop generator for wide panels"
```

---

### Task 6: Panel cache storage

**Files:**
- Create: `data/src/main/sqldelight/tachiyomi/data/panel_cache.sq`
- Create: `data/src/main/sqldelight/tachiyomi/migrations/14.sqm`
- Create: `app/src/main/java/eu/kanade/tachiyomi/data/reader/PanelCacheRepository.kt`
- Modify: `app/src/main/java/mihon/app/di/AppGraph.kt`

**Interfaces:**
- Consumes: `PanelPageData` (Task 1).
- Produces: `class PanelCacheRepository { suspend fun get(chapterId: Long, pageIndex: Int, imageHash: String): PanelPageData?; suspend fun save(chapterId: Long, pageIndex: Int, imageHash: String, data: PanelPageData) }`, exposed as `AppGraph.panelCacheRepository`.

- [ ] **Step 1: Add the SQLDelight table**

`data/src/main/sqldelight/tachiyomi/data/panel_cache.sq`:

```sql
CREATE TABLE panel_cache(
    chapter_id INTEGER NOT NULL,
    page_index INTEGER NOT NULL,
    image_hash TEXT NOT NULL,
    panels_json TEXT NOT NULL,
    detected_at INTEGER NOT NULL,
    PRIMARY KEY (chapter_id, page_index),
    FOREIGN KEY(chapter_id) REFERENCES chapters (_id)
    ON DELETE CASCADE
);

getPanels:
SELECT image_hash, panels_json
FROM panel_cache
WHERE chapter_id = :chapterId AND page_index = :pageIndex;

upsert:
INSERT INTO panel_cache(chapter_id, page_index, image_hash, panels_json, detected_at)
VALUES (:chapterId, :pageIndex, :imageHash, :panelsJson, :detectedAt)
ON CONFLICT(chapter_id, page_index)
DO UPDATE
SET
    image_hash = :imageHash,
    panels_json = :panelsJson,
    detected_at = :detectedAt;
```

- [ ] **Step 2: Add the migration**

`data/src/main/sqldelight/tachiyomi/migrations/14.sqm`:

```sql
CREATE TABLE panel_cache(
    chapter_id INTEGER NOT NULL,
    page_index INTEGER NOT NULL,
    image_hash TEXT NOT NULL,
    panels_json TEXT NOT NULL,
    detected_at INTEGER NOT NULL,
    PRIMARY KEY (chapter_id, page_index),
    FOREIGN KEY(chapter_id) REFERENCES chapters (_id)
    ON DELETE CASCADE
);
```

- [ ] **Step 3: Write the repository**

```kotlin
package eu.kanade.tachiyomi.data.reader

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelPageData
import kotlinx.serialization.json.Json
import tachiyomi.data.Database

@Inject
@SingleIn(AppScope::class)
class PanelCacheRepository(
    private val database: Database,
    private val json: Json,
) {

    suspend fun get(chapterId: Long, pageIndex: Int, imageHash: String): PanelPageData? {
        val row = database.panel_cacheQueries
            .getPanels(chapterId, pageIndex.toLong())
            .awaitAsOneOrNull()
            ?: return null
        if (row.image_hash != imageHash) return null
        return runCatching { json.decodeFromString<PanelPageData>(row.panels_json) }.getOrNull()
    }

    suspend fun save(chapterId: Long, pageIndex: Int, imageHash: String, data: PanelPageData) {
        database.panel_cacheQueries.upsert(
            chapterId = chapterId,
            pageIndex = pageIndex.toLong(),
            imageHash = imageHash,
            panelsJson = json.encodeToString(PanelPageData.serializer(), data),
            detectedAt = System.currentTimeMillis(),
        )
    }
}
```

- [ ] **Step 4: Expose it on `AppGraph`**

In `app/src/main/java/mihon/app/di/AppGraph.kt`, add the import:

```kotlin
import eu.kanade.tachiyomi.data.reader.PanelCacheRepository
```

And add the property near `val downloadManager: DownloadManager` (line 94):

```kotlin
    val downloadManager: DownloadManager
    val panelCacheRepository: PanelCacheRepository
```

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin :data:compileKotlin`
Expected: BUILD SUCCESSFUL (confirms the SQLDelight table/migration generate correctly and Metro resolves `PanelCacheRepository`)

- [ ] **Step 6: Commit**

```bash
git add data/src/main/sqldelight/tachiyomi/data/panel_cache.sq data/src/main/sqldelight/tachiyomi/migrations/14.sqm app/src/main/java/eu/kanade/tachiyomi/data/reader/PanelCacheRepository.kt app/src/main/java/mihon/app/di/AppGraph.kt
git commit -m "feat(reader): add panel cache table and repository"
```

---

### Task 7: Panel detector orchestrator

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelConfidence.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelDetector.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelConfidenceTest.kt`

**Interfaces:**
- Consumes: `PanelRect`, `PanelDirection`, `Panel`, `PanelPageData`, `PanelSubStopGenerator`, `Bitmap.toPixelBuffer` (Tasks 1, 2, 4), `PanelBoundaryDetector.detect` (Task 2), `PanelReadingOrder.sort` (Task 3), `PanelCacheRepository` (Task 6). Also consumes `ReaderPage` (`app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/ReaderPage.kt`, has `.index: Int` from `Page` and `.chapter: ReaderChapter` with `.chapter.id: Long?`).
- Produces: `object PanelConfidence { fun isLowConfidence(rects: List<PanelRect>): Boolean }`, `class PanelDetector(panelCacheRepository: PanelCacheRepository, subStopGenerator: PanelSubStopGenerator) { suspend fun detect(page: ReaderPage, imageBytes: Buffer, direction: PanelDirection): List<Panel> }` — used by Task 8/9.

- [ ] **Step 1: Write the failing test for the confidence check (pure logic)**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelConfidenceTest {

    @Test
    fun `no detected panels is low confidence`() {
        assertTrue(PanelConfidence.isLowConfidence(emptyList()))
    }

    @Test
    fun `too many small scattered panels is low confidence`() {
        val scattered = (0 until 20).map { i ->
            val x = (i % 5) * 0.02f
            val y = (i / 5) * 0.02f
            PanelRect(x, y, x + 0.01f, y + 0.01f)
        }

        assertTrue(PanelConfidence.isLowConfidence(scattered))
    }

    @Test
    fun `a normal 2x2 grid covering most of the page is high confidence`() {
        val grid = listOf(
            PanelRect(0f, 0f, 0.48f, 0.48f),
            PanelRect(0.52f, 0f, 1f, 0.48f),
            PanelRect(0f, 0.52f, 0.48f, 1f),
            PanelRect(0.52f, 0.52f, 1f, 1f),
        )

        assertFalse(PanelConfidence.isLowConfidence(grid))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelConfidenceTest"`
Expected: FAIL (compilation error — `PanelConfidence` doesn't exist yet)

- [ ] **Step 3: Write `PanelConfidence`**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

object PanelConfidence {

    private const val MAX_PLAUSIBLE_PANELS = 12
    private const val MIN_COVERED_AREA_FRACTION = 0.15

    fun isLowConfidence(rects: List<PanelRect>): Boolean {
        if (rects.isEmpty() || rects.size > MAX_PLAUSIBLE_PANELS) return true
        val coveredArea = rects.sumOf { (it.width * it.height).toDouble() }
        return coveredArea < MIN_COVERED_AREA_FRACTION
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelConfidenceTest"`
Expected: PASS

- [ ] **Step 5: Write `PanelDetector`**

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.data.reader.PanelCacheRepository
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import java.security.MessageDigest
import kotlin.math.max

class PanelDetector(
    private val panelCacheRepository: PanelCacheRepository,
    private val subStopGenerator: PanelSubStopGenerator,
) {

    suspend fun detect(page: ReaderPage, imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val chapterId = page.chapter.chapter.id ?: return listOf(Panel(PanelRect.FULL_PAGE))
        val hash = imageBytes.contentHash()

        panelCacheRepository.get(chapterId, page.index, hash)?.let { return it.panels }

        val panels = withTimeoutOrNull(DETECTION_BUDGET_MS) {
            runDetection(imageBytes, direction)
        } ?: listOf(Panel(PanelRect.FULL_PAGE))

        panelCacheRepository.save(chapterId, page.index, hash, PanelPageData(panels))
        return panels
    }

    private suspend fun runDetection(imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageBytes.copy().inputStream(), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return listOf(Panel(PanelRect.FULL_PAGE))

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DETECTION_DIMENSION)
        val smallBitmap = BitmapFactory.decodeStream(
            imageBytes.copy().inputStream(),
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return listOf(Panel(PanelRect.FULL_PAGE))

        val rawRects = PanelBoundaryDetector.detect(smallBitmap.toPixelBuffer(MAX_DETECTION_DIMENSION))
        smallBitmap.recycle()

        if (PanelConfidence.isLowConfidence(rawRects)) return listOf(Panel(PanelRect.FULL_PAGE))

        val ordered = PanelReadingOrder.sort(rawRects, direction)
        val fullBitmap = lazy { BitmapFactory.decodeStream(imageBytes.copy().inputStream()) }
        return ordered.map { rect ->
            val subStops = subStopGenerator.generate(rect, direction) {
                fullBitmap.value?.let { cropNormalized(it, rect) }
            }
            Panel(rect, subStops)
        }
    }

    private fun cropNormalized(bitmap: Bitmap, rect: PanelRect): Bitmap {
        val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxDimension) sample *= 2
        return sample
    }

    private fun Buffer.contentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(snapshot().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DETECTION_BUDGET_MS = 2000L
        private const val MAX_DETECTION_DIMENSION = 400
    }
}
```

- [ ] **Step 6: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelConfidence.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelDetector.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/viewer/panel/PanelConfidenceTest.kt
git commit -m "feat(reader): add panel detector orchestrator with caching and budget guard"
```

---

### Task 8: Panel-by-panel pan/zoom navigation in the pager viewer

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerPageHolder.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`

**Interfaces:**
- Consumes: `PanelRect`, `PanelDirection`, `flattenToStops` (Task 1), `PanelDetector` (Task 7). Also consumes `PanelByPanelViewer` from Task 9 (`panelDetector: PanelDetector`, `panelDirection: PanelDirection` properties) — this task adds the `viewer is PanelByPanelViewer` references that only compile once Task 9 exists, so Task 9's class stub must exist first; see the note at the start of Task 9's steps for the ordering used to keep every task's build green.
- Produces on `ReaderPageImageView`: `var panelModeActive: Boolean`, `fun setPanelStops(stops: List<PanelRect>)`, `fun hasPanelStops(): Boolean`, `fun canAdvancePanelStop(): Boolean`, `fun canRetreatPanelStop(): Boolean`, `fun advancePanelStop()`, `fun retreatPanelStop()`. These are consumed by `PagerViewer.moveRight()`/`moveLeft()` (this task) and by `PagerPageHolder` (this task).

> **Note on ordering:** This task references `PanelByPanelViewer`, which Task 9 creates. Do Task 9's Step 1 (just the `PanelByPanelViewer` class with `panelDetector`/`panelDirection`, no `ReadingMode` wiring yet) before Step 1 below, then return to finish Task 9 afterward. This keeps every intermediate commit compiling.

- [ ] **Step 1: Create the `PanelByPanelViewer` stub (pulled forward from Task 9)**

Create `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PanelByPanelViewer.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDetector
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDirection
import eu.kanade.tachiyomi.ui.reader.viewer.panel.TextAwarePanelSubStopGenerator

/**
 * Implementation of a PagerViewer that navigates panel-by-panel within each page before
 * flipping to the next page, generalizing the dual-page-split pan mechanism in
 * [eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView] to N detected panel stops.
 */
class PanelByPanelViewer(activity: ReaderActivity) : PagerViewer(activity) {

    val panelDetector = PanelDetector(
        panelCacheRepository = graph.panelCacheRepository,
        subStopGenerator = TextAwarePanelSubStopGenerator(),
    )

    val panelDirection: PanelDirection
        get() = if (
            ReadingMode.fromPreference(readerPreferences.defaultReadingMode.get()) == ReadingMode.RIGHT_TO_LEFT
        ) {
            PanelDirection.RTL
        } else {
            PanelDirection.LTR
        }

    override fun createPager(): Pager = Pager(activity)
}
```

- [ ] **Step 2: Generalize the pan mechanism in `ReaderPageImageView`**

In `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt`, add the import:

```kotlin
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelRect
```

and add `import kotlin.math.min` (not already imported in this file).

Replace the existing `onPageSelected` method:

```kotlin
    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }
```

with:

```kotlin
    /** Set by [PagerPageHolder] before load starts, when this page belongs to a panel-by-panel viewer. */
    var panelModeActive: Boolean = false

    private var panelStops: List<PanelRect> = emptyList()
    private var panelStopIndex: Int = -1
    private var panelStopsEnterForward: Boolean = true

    open fun onPageSelected(forward: Boolean) {
        panelStopsEnterForward = forward
        if (panelModeActive) return
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    /**
     * Sets the ordered list of panel stops (normalized 0f..1f image-fraction coordinates) for
     * panel-by-panel guided navigation, and jumps to the first (or last, if this page was
     * entered backward) stop. Pass an empty list to clear (falls back to a single full-page stop).
     */
    fun setPanelStops(stops: List<PanelRect>) {
        panelStops = stops.ifEmpty { listOf(PanelRect.FULL_PAGE) }
        panelStopIndex = if (panelStopsEnterForward) 0 else panelStops.lastIndex
        jumpToPanelStop(panelStopIndex)
    }

    fun hasPanelStops(): Boolean = panelStops.isNotEmpty()

    fun canAdvancePanelStop(): Boolean = panelStops.isNotEmpty() && panelStopIndex < panelStops.lastIndex

    fun canRetreatPanelStop(): Boolean = panelStops.isNotEmpty() && panelStopIndex > 0

    fun advancePanelStop() {
        if (!canAdvancePanelStop()) return
        panelStopIndex++
        animateToPanelStop(panelStopIndex)
    }

    fun retreatPanelStop() {
        if (!canRetreatPanelStop()) return
        panelStopIndex--
        animateToPanelStop(panelStopIndex)
    }

    private fun jumpToPanelStop(index: Int) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        val target = panelStops.getOrNull(index) ?: return
        if (view.isReady) {
            val (scale, center) = view.panelStopTarget(target)
            view.setScaleAndCenter(scale, center)
        } else {
            view.setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        setupZoom(config)
                        val (scale, center) = view.panelStopTarget(target)
                        view.setScaleAndCenter(scale, center)
                        this@ReaderPageImageView.onImageLoaded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageLoadError(e)
                    }
                },
            )
        }
    }

    private fun animateToPanelStop(index: Int) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        val target = panelStops.getOrNull(index) ?: return
        val (scale, center) = view.panelStopTarget(target)
        view.animateScaleAndCenter(scale, center)!!
            .withEasing(EASE_OUT_QUAD)
            .withDuration(250)
            .withInterruptible(true)
            .start()
    }

    private fun SubsamplingScaleImageView.panelStopTarget(rect: PanelRect): Pair<Float, PointF> {
        val targetScale = min(
            width / (rect.width * sWidth),
            height / (rect.height * sHeight),
        ).coerceIn(minScale, maxScale)
        val center = PointF(
            (rect.left + rect.width / 2f) * sWidth,
            (rect.top + rect.height / 2f) * sHeight,
        )
        return targetScale to center
    }
```

- [ ] **Step 3: Trigger detection and wire panel stops in `PagerPageHolder`**

In `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerPageHolder.kt`, add imports:

```kotlin
import eu.kanade.tachiyomi.ui.reader.viewer.panel.flattenToStops
import okio.Buffer
```

(`okio.Buffer` and `okio.BufferedSource` are already imported; only add `Buffer` if not already present — check the existing import list first since `Buffer` is already imported in this file.)

Replace the `init` block:

```kotlin
    init {
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }
```

with:

```kotlin
    init {
        if (viewer is PanelByPanelViewer) {
            panelModeActive = true
        }
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }
```

Replace the `setImage()` private suspend function:

```kotlin
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        val streamFn = page.stream ?: return

        try {
            val (source, isAnimated, background) = withIOContext {
                val source = streamFn().use { process(item, Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                Triple(source, isAnimated, background)
            }
            withUIContext {
                setImage(
                    source,
                    isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                    ),
                )
                if (!isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }
```

with:

```kotlin
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        val streamFn = page.stream ?: return

        try {
            val (source, isAnimated, background, panelSourceBytes) = withIOContext {
                val source = streamFn().use { process(item, Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                val panelSourceBytes = if (!isAnimated && viewer is PanelByPanelViewer) {
                    Buffer().apply { writeAll(source.peek()) }
                } else {
                    null
                }
                PageLoadResult(source, isAnimated, background, panelSourceBytes)
            }
            withUIContext {
                setImage(
                    source,
                    isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                    ),
                )
                if (!isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
            if (panelSourceBytes != null && viewer is PanelByPanelViewer) {
                loadPanels(viewer, panelSourceBytes)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private data class PageLoadResult(
        val source: BufferedSource,
        val isAnimated: Boolean,
        val background: android.graphics.drawable.Drawable?,
        val panelSourceBytes: Buffer?,
    )

    private suspend fun loadPanels(viewer: PanelByPanelViewer, imageBytes: Buffer) {
        val panels = viewer.panelDetector.detect(page, imageBytes, viewer.panelDirection)
        withUIContext {
            setPanelStops(panels.flattenToStops())
        }
    }
```

- [ ] **Step 4: Generalize `moveRight()`/`moveLeft()` in `PagerViewer`**

In `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`, replace `moveRight()`:

```kotlin
    protected open fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        }
    }
```

with:

```kotlin
    protected open fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            when {
                holder != null && holder.hasPanelStops() && holder.canAdvancePanelStop() -> holder.advancePanelStop()
                holder != null && config.navigateToPan && holder.canPanRight() -> holder.panRight()
                else -> pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        }
    }
```

and `moveLeft()`:

```kotlin
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        }
    }
```

with:

```kotlin
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            when {
                holder != null && holder.hasPanelStops() && holder.canRetreatPanelStop() -> holder.retreatPanelStop()
                holder != null && config.navigateToPan && holder.canPanLeft() -> holder.panLeft()
                else -> pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        }
    }
```

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerPageHolder.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PanelByPanelViewer.kt
git commit -m "feat(reader): generalize dual-page pan mechanism to N panel stops"
```

---

### Task 9: Wire up the `PANEL_BY_PANEL` reading mode

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt`
- Create: `app/src/main/res/drawable/ic_reader_panel_by_panel_24dp.xml`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`

**Interfaces:**
- Consumes: `PanelByPanelViewer` (Task 8, Step 1).
- Produces: `ReadingMode.PANEL_BY_PANEL`, selectable in reader settings and resolved by `ReadingMode.toViewer()`.

- [ ] **Step 1: Add the string resource**

In `i18n/src/commonMain/moko-resources/base/strings.xml`, after the `vertical_plus_viewer` line (currently line 458):

```xml
    <string name="panel_by_panel_viewer">Panel by panel</string>
```

- [ ] **Step 2: Add the drawable icon**

Create `app/src/main/res/drawable/ic_reader_panel_by_panel_24dp.xml` (a 2x2 grid, matching the existing `ic_reader_*_24dp` viewport/style):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#000"
        android:pathData="M3,11h8V3H3v8zm0,10h8v-8H3v8zm10,0h8v-8h-8v8zm0,-18v8h8V3h-8z" />
</vector>
```

- [ ] **Step 3: Add the `ReadingMode` entry**

In `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt`, add the import:

```kotlin
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PanelByPanelViewer
```

Add the enum entry after `CONTINUOUS_VERTICAL` (before the closing `;`):

```kotlin
    PANEL_BY_PANEL(
        MR.strings.panel_by_panel_viewer,
        R.drawable.ic_reader_panel_by_panel_24dp,
        0x00000006,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
```

Update `toViewer()` to handle the new entry in both branches:

```kotlin
        fun toViewer(preference: Int?, activity: ReaderActivity): Viewer {
            if (activity.appGraph.basePreferences.highQualityRenderer.get()) {
                return when (fromPreference(preference)) {
                    LEFT_TO_RIGHT -> WebGpuViewer(activity, isReversed = false, isVertical = false)
                    RIGHT_TO_LEFT -> WebGpuViewer(activity, isReversed = true, isVertical = false)
                    VERTICAL -> WebGpuViewer(activity, isReversed = false, isVertical = true)
                    WEBTOON -> WebGpuViewerContinuous(activity)
                    CONTINUOUS_VERTICAL -> WebGpuViewerContinuous(activity)
                    // Panel-by-panel always uses the SubsamplingScaleImageView-based pager,
                    // regardless of the high-quality (WebGpu) renderer setting.
                    PANEL_BY_PANEL -> PanelByPanelViewer(activity)
                    DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
                }
            }
            return when (fromPreference(preference)) {
                LEFT_TO_RIGHT -> L2RPagerViewer(activity)
                RIGHT_TO_LEFT -> R2LPagerViewer(activity)
                VERTICAL -> VerticalPagerViewer(activity)
                WEBTOON -> WebtoonViewer(activity)
                CONTINUOUS_VERTICAL -> WebtoonViewer(activity, isContinuous = false)
                PANEL_BY_PANEL -> PanelByPanelViewer(activity)
                DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
            }
        }
```

- [ ] **Step 4: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt app/src/main/res/drawable/ic_reader_panel_by_panel_24dp.xml i18n/src/commonMain/moko-resources/base/strings.xml
git commit -m "feat(reader): add Panel by panel reading mode"
```

---

### Task 10: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full new test suite**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.viewer.panel.*"`
Expected: BUILD SUCCESSFUL, all tests passing

- [ ] **Step 2: Full app build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual QA pass**

Install the debug build on a device/emulator and, in a manga's reader settings, select "Panel by panel." Open a chapter and confirm:
- Tapping/swiping through a page moves panel-by-panel (zoom+pan) before flipping to the next page.
- Reaching the last panel on a page and advancing flips to the next page's first panel; going back from a page's first panel lands on the previous page's last panel.
- A wide/spread panel shows multiple stops before revealing the full panel.
- Pinching to zoom escapes guided navigation, and the next tap animates back into the stop sequence.
- Rereading a chapter (leaving and reopening) doesn't re-run detection from scratch (should feel instant on the second read — panel cache hit).
- A page with no clear panels (e.g. a splash page) still navigates normally (single stop = full page).

No code changes expected from this task unless QA turns up a bug — if it does, fix it in a follow-up commit and re-run Steps 1-3.
