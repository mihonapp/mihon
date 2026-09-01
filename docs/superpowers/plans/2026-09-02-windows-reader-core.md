# Windows Reader Core and Reading Modes Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver the complete Plan 3 Windows local-content reader: secure chapter containers, bounded image decoding/cache/prefetch, every approved reading mode, configurable desktop input, window modes, chapter continuity, and durable progress.

**Architecture:** Add a pure JVM `reader-core` module that owns container enumeration, page identity/order, metadata probing, bounded full/region decoding, memory accounting, prefetch, navigation, retry, and session progress. `desktop-library-data` exposes a narrow reader storage/progress port over the Plan 2 database. `desktop-app` adapts core state to Compose Desktop, converts only visible `BufferedImage` tiles to Compose images, owns input/window behavior, and opens a reader surface from manga details. Remote extension pages are deliberately outside this slice and arrive in Plan 4.

**Tech Stack:** Kotlin/JVM 2.4.10, Java 17, kotlinx-coroutines 1.11.0, Compose Multiplatform Desktop 1.12.0, SQLDelight 2.3.2, Apache Commons Compress 1.28.0, junrar 8.0.0, JDK ImageIO/AWT, JUnit 6, Kotest assertions, Compose UI tests.

**Authoritative references:** [approved Windows design](../specs/2026-08-31-windows-port-design.md), [Apache Commons Compress 1.28.0 coordinates](https://commons.apache.org/proper/commons-compress/dependency-info.html), [Commons Compress supported formats and streaming notes](https://commons.apache.org/proper/commons-compress/examples.html), [Commons Compress 7z limitations](https://commons.apache.org/proper/commons-compress/limitations.html), [junrar releases](https://github.com/junrar/junrar/releases), [ImageReadParam region API](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/imageio/ImageReadParam.html#setSourceRegion(java.awt.Rectangle)).

---

## Fixed scope and invariants

- [ ] Reader input in Plan 3 is a Plan 2 `local_chapter_asset`: standalone image file, directory, CBZ/ZIP, CBT/TAR, CB7/7z, CBR/RAR, or EPUB. A chapter without a local asset stays disabled with an explanatory tooltip; it must not fall through to an Android or network source.
- [ ] `reader-core` has no dependency on `:app`, Android SDK, Compose, SQLDelight, or desktop window APIs.
- [ ] Modes are `SINGLE_LTR`, `SINGLE_RTL`, `DUAL_LTR`, `DUAL_RTL`, `VERTICAL`, and `WEBTOON`. Continuous vertical preserves page gaps; Webtoon removes gaps and fits width.
- [ ] Scale choices are `ORIGINAL`, `FIT_WIDTH`, and `FIT_HEIGHT`. Zoom is clamped to `0.25..8.0`; pan is clamped after layout so content cannot be lost entirely off-screen.
- [ ] Dual-page cover offset is explicit and deterministic: offset off groups `(0,1),(2,3)…`; offset on emits page `0` alone and groups `(1,2),(3,4)…`. RTL reverses visual placement, never the stable page identity.
- [ ] Logical page identity is `(chapterId, entryName)`. Animation frame identity is `(PageId, frameIndex)` and never increases chapter page count. Natural order is case-insensitive numeric-aware path order with `/` separators. Directory and archive readers produce the same order.
- [ ] Animated GIF pages expose bounded frame count/durations after probe. Core schedules, pauses, cancels, loops and restarts frames with a monotonic clock; a page change never leaves its animation running. Other animated formats unsupported by JDK ImageIO fail as `UNSUPPORTED_IMAGE`, never as a crash or blank page.
- [ ] A corrupt page becomes a retryable error page. One corrupt entry does not make other entries unreadable.
- [ ] All local roots/container files and archive paths use one no-follow policy. Reject absolute paths, drive/UNC prefixes, `..`, NUL, symlink/junction/reparse components, archive symlink/hardlink entries, duplicate normalized names, encrypted entries, files replaced during open, and entries outside the selected chapter root.
- [ ] Hard input limits: 100,000 archive entries; 256 MiB per expanded page; 2 GiB cumulative expanded bytes per chapter enumeration; 256 MiB encoded standalone page; width/height `1..200,000`; metadata pixel count at most `4,000,000,000`; XML metadata 8 MiB and nesting depth 64.
- [ ] Full decode is allowed only when `width * height * 4 <= 16 MiB`. Larger static images use 1024×1024 source-region tiles. A reader refusing region decode for an over-budget image returns `REGION_DECODE_UNAVAILABLE`; it never falls back to full allocation. Animated GIF canvases above 16 MiB are rejected because bounded partial-frame composition is unavailable in JDK ImageIO.
- [ ] One 256 MiB core memory budget covers decoded cache residents plus every in-flight ImageIO destination, conversion raster, GIF composition canvas and prefetched decode. Callers reserve conservative peak bytes before allocation; cache eviction frees unpinned reservations; insertion transfers the output reservation into resident weight. The Compose bridge has a separate 96 MiB cap. With the 16 MiB ceiling, dual animated pages may retain at most two current and two replacement frames (64 MiB); tiled pages retain only viewport tiles plus one replacement per visible page. Release/flush old images on key change and never wait for a replacement while holding reservations that make that replacement impossible.
- [ ] 7z explicitly sets `SevenZFile.Builder.setMaxMemoryLimitKiB(131_072)` and reserves that dictionary allowance from the same core budget while extraction is active. A shared chapter counter charges every actual expanded byte, including retry reads, until the `ChapterSource` closes; it cannot be bypassed with forged entry sizes or repeated opens.
- [ ] Prefetch is two logical spreads/pages ahead and one behind, is cancelled on direction/chapter change, and cannot evict a currently pinned visible tile.
- [ ] Progress uses zero-based page indexes to match `chapter.last_page_read`. Navigation backwards persists the actual latest position; writes are ordered by a runtime-wide `(generation, sequence)` pair so delayed debounce work and older sessions are discarded. `read` is monotonic and becomes true only after the final logical page is reached. `history.read_duration` accumulates non-negative foreground deltas and `last_read` is monotonic.
- [ ] A progress write is debounced to 750 ms during movement and synchronously flushed on chapter transition, reader close, and process shutdown.
- [ ] Input defaults: Left/Right and A/D navigate according to reading direction; PageUp/PageDown navigate; Home/End jump; `+/-/0` zoom/reset; `F` fullscreen; `B` borderless; `Esc` exits transient window mode then reader; wheel scrolls continuous modes and navigates paged modes; Ctrl+wheel zooms; mouse side buttons navigate; configurable click regions are left 25%, center 50%, right 25%.
- [ ] Normal, fullscreen, and borderless states preserve the last normal bounds through the existing `WindowPlacement` preference path. No mode change creates a second database/runtime.
- [ ] Reader errors are typed and user-facing English for the current UI language: missing/moved local content, unsupported/encrypted container, unsafe entry, limit exceeded, unsupported/corrupt image, and decoder failure.

## Required public contracts

Create these contracts first and keep later tasks conforming to them. Changes require updating this plan and their contract tests before implementation.

```kotlin
// reader-core/src/main/kotlin/mihon/reader/model/ReaderModels.kt
enum class ReadingMode { SINGLE_LTR, SINGLE_RTL, DUAL_LTR, DUAL_RTL, VERTICAL, WEBTOON }
enum class ScaleMode { ORIGINAL, FIT_WIDTH, FIT_HEIGHT }
enum class ReaderErrorCode {
    MISSING_CONTENT, UNSUPPORTED_CONTAINER, ENCRYPTED_CONTAINER, UNSAFE_ENTRY,
    ENTRY_LIMIT, EXPANDED_LIMIT, IMAGE_LIMIT, UNSUPPORTED_IMAGE, CORRUPT_IMAGE,
    REGION_DECODE_UNAVAILABLE, EMPTY_CHAPTER, CANCELLED, IO
}

data class PageId(val chapterId: Long, val entryName: String)
data class FrameId(val pageId: PageId, val frameIndex: Int)
data class PageDescriptor(
    val id: PageId,
    val encodedBytes: Long?,
    val width: Int? = null,
    val height: Int? = null,
)
data class ReaderViewport(val widthPx: Int, val heightPx: Int, val density: Float)
data class ReaderPosition(val chapterId: Long, val pageIndex: Int, val generation: Long)
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/source/ReaderChapterAsset.kt
data class ReaderChapterAsset(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val storageRoot: Path,
    val relativePath: Path,
    val assetKind: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val lastPageRead: Long,
    val read: Boolean,
)

enum class ChapterDirection { PREVIOUS, NEXT }
enum class ProgressWriteResult { APPLIED, STALE }

interface ReaderChapterCatalog {
    fun chapterAsset(chapterId: Long): ReaderChapterAsset?
    fun adjacentReadableChapter(chapterId: Long, direction: ChapterDirection): ReaderChapterAsset?
}
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/session/ReaderProgress.kt
data class ReaderProgressUpdate(
    val chapterId: Long,
    val pageIndex: Long,
    val completed: Boolean,
    val lastReadEpochMillis: Long,
    val readDurationDeltaMillis: Long,
    val generation: Long,
    val sequence: Long,
)

fun interface ReaderProgressSink {
    suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult
}
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/image/ImageContracts.kt
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val frameDurationsMillis: List<Long>,
    val supportsRegionDecode: Boolean,
)
data class TileKey(val frameId: FrameId, val sampleSize: Int, val source: IntRect)
data class TileRequest(val frameId: FrameId, val sampleSize: Int, val source: IntRect)
interface DecodedTile : AutoCloseable {
    val key: TileKey
    val image: BufferedImage
    val weightBytes: Long
    fun adoptAsCacheResident()
}

interface PageDecoder {
    suspend fun probe(source: BoundedPageInput): ImageMetadata
    suspend fun decodeFull(source: BoundedPageInput, metadata: ImageMetadata, frameIndex: Int): DecodedTile
    suspend fun decodeRegion(source: BoundedPageInput, metadata: ImageMetadata, request: TileRequest): DecodedTile
}
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/source/ChapterSource.kt
interface ChapterSource : AutoCloseable {
    val chapterId: Long
    suspend fun pages(): List<PageDescriptor>
    suspend fun open(page: PageId): BoundedPageInput
}

fun interface ChapterSourceFactory {
    fun open(asset: ReaderChapterAsset): ChapterSource
}
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/session/ReaderSession.kt
interface ReaderSession {
    val state: StateFlow<ReaderState>
    suspend fun open(chapterId: Long)
    fun dispatch(action: ReaderAction)
    suspend fun retry(pageId: PageId)
    suspend fun flushProgress()
    suspend fun closeAndFlush()
    fun cancelWithoutFlush()
}
```

```kotlin
// reader-core/src/main/kotlin/mihon/reader/memory/ReaderMemoryBudget.kt
enum class MemoryKind { ARCHIVE_DICTIONARY, DECODE_INTERMEDIATE, DECODE_OUTPUT, CACHE_RESIDENT }
data class ReaderMemoryMetrics(
    val capacityBytes: Long,
    val reservedBytes: Long,
    val residentBytes: Long,
    val inFlightBytes: Long,
    val highWaterBytes: Long,
    val waiterCount: Int,
)
interface MemoryLease : AutoCloseable {
    val bytes: Long
    val kind: MemoryKind
    fun transferTo(kind: MemoryKind): MemoryLease
}
interface ReaderMemoryBudget {
    suspend fun reserve(bytes: Long, kind: MemoryKind): MemoryLease
    fun registerPressureHandler(handler: suspend (requiredBytes: Long) -> Unit): AutoCloseable
    fun snapshot(): ReaderMemoryMetrics
}
```

```kotlin
// desktop-library-data/src/main/kotlin/mihon/desktop/library/reader/ReaderLibraryPort.kt
interface ReaderLibraryPort : ReaderChapterCatalog, ReaderProgressSink
```

## Execution protocol

For every numbered task:

1. Start from a clean task boundary and inspect overlapping user changes.
2. Add the named failing tests and run the narrow RED command. Record the failure reason in the evidence document.
3. Implement only enough production code to satisfy the task contracts.
4. Run the narrow GREEN command, `spotlessCheck`, and affected upstream tests.
5. Request a fresh spec-compliance review. Fix every Critical/Important finding and re-review.
6. Request a fresh code-quality review. Fix every Critical/Important finding and re-review.
7. Commit with the exact task commit message. Never combine tasks or rewrite prior user commits.

---

### Task 1: Register `reader-core` and lock state/order contracts

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `reader-core/build.gradle.kts`
- Create: `reader-core/src/main/kotlin/mihon/reader/model/ReaderModels.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ReaderChapterAsset.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/BoundedPageInput.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderProgress.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/image/ImageContracts.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/memory/ReaderMemoryBudget.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/layout/PageGrouping.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/layout/NaturalPageComparator.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/layout/PageGroupingTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/layout/NaturalPageComparatorTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/model/ReaderModelInvariantTest.kt`

- [ ] Add `include(":reader-core")`; use the Kotlin JVM and Spotless conventions, Java 17 toolchain, coroutines core/test and the existing test bundle. Add catalog entries `commonsCompress = "1.28.0"`, `junrar = "8.0.0"`, and matching libraries, but do not consume them until Task 3.
- [ ] Write RED tests covering all six modes, invalid viewport/page indexes, LTR/RTL natural order, case and numeric ties (`1`, `2`, `02`, `10`), nested path separators, dual grouping with/without cover offset, and stable identity when visual order reverses.
- [ ] Implement immutable models and pure grouping/order functions. Keep filesystem `Path` only in the source-boundary asset contract, never in `mihon.reader.model`.
- [ ] Add the source/image/progress/memory signatures above as compiling contracts with invariant checks and minimal typed stubs only; their behavior is implemented in later tasks. The session interface is created with its action/state types in Task 6. This makes every task compile against one package and one type definition.

Run RED then GREEN:

```powershell
.\gradlew.bat :reader-core:test --tests "mihon.reader.layout.*" --tests "mihon.reader.model.*"
```

Expected RED: project/classes or assertions are missing. Expected GREEN: all Task 1 tests pass and Gradle configures Android plus both desktop modules without dependency resolution warnings.

```powershell
.\gradlew.bat :reader-core:test :desktop-app:test :desktop-library-data:test spotlessCheck
git add settings.gradle.kts gradle/libs.versions.toml reader-core
git commit -m "feat(reader): establish core reader contracts"
```

### Task 2: Add the atomic reader library/progress port

**Files:**

- Modify: `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq`
- Modify: `desktop-library-data/build.gradle.kts`
- Modify: `desktop-library-data/src/main/kotlin/mihon/desktop/library/db/SqlDelightLibraryRepository.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/reader/ReaderLibraryPort.kt`
- Modify: `desktop-library-data/src/test/kotlin/mihon/desktop/library/db/LibrarySchemaTest.kt`
- Create: `desktop-library-data/src/test/kotlin/mihon/desktop/library/reader/SqlDelightReaderLibraryPortTest.kt`

- [ ] Add `implementation(project(":reader-core"))` to the data module and make `SqlDelightLibraryRepository` implement the core-owned `ReaderChapterCatalog`/`ReaderProgressSink` through the narrow `ReaderLibraryPort`. This direction keeps core independent of persistence.
- [ ] Add a `selectReaderChapterAsset` join from `chapter` through `manga` and `local_manga_entry`/`local_chapter_asset`; keep database `last_page_read` as `Long`, resolve the absolute path in Kotlin as `storageRoot.resolve(relativePath).normalize()`, and re-check containment before returning it.
- [ ] Add preceding/following readable-chapter queries using the complete detail order `source_order DESC, chapter_number DESC, name COLLATE NOCASE, id DESC`. Skip chapters without local assets and regression-test equal source/chapter numbers with name/id ties.
- [ ] Add one repository transaction that updates the exact latest `last_page_read`, ORs `read` with `completed`, sets history `last_read = MAX`, and increments `read_duration` with a checked non-negative delta. Under a repository mutex, store the latest accepted `(generation, sequence)` per chapter so delayed writes from the same session and writes from an older session are returned as `STALE` without mutation; update the accepted pair only after a successful commit.
- [ ] Bound written page index to `0..Int.MAX_VALUE`, duration delta to `0..86_400_000`, generation/sequence/timestamps to non-negative values. Preserve imported database values as `Long`; session restore clamps them in `Long` space before checked conversion to an in-memory index. Overflow in accumulated duration saturates at `Long.MAX_VALUE`.
- [ ] Test missing assets, normalized containment, adjacent chapter direction/ties, `last_page_read > Int.MAX_VALUE` restoration input, backward navigation persistence, completion monotonicity, stale-generation rejection, stale-sequence rejection, concurrent ordering, duration accumulation, overflow saturation, transaction rollback, and flow invalidation.
- [ ] No schema version bump is needed because this task adds queries only. Keep `1.db` unchanged and prove `verifySqlDelightMigration` passes.

Commands:

```powershell
.\gradlew.bat :desktop-library-data:test --tests "mihon.desktop.library.reader.SqlDelightReaderLibraryPortTest"
.\gradlew.bat :desktop-library-data:test :desktop-library-data:verifySqlDelightMigration spotlessCheck
git add desktop-library-data
git commit -m "feat(reader): expose local assets and progress"
```

### Task 3: Implement secure directory and archive chapter sources

**Files:**

- Modify: `reader-core/build.gradle.kts`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ReaderLimits.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ReaderFailure.kt`
- Modify: `reader-core/src/main/kotlin/mihon/reader/source/BoundedPageInput.kt`
- Modify: `reader-core/src/main/kotlin/mihon/reader/source/ChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/SecureLocalPath.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ChapterExpansionBudget.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/memory/BoundedReaderMemoryBudget.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/LocalChapterSourceFactory.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/StandaloneImageChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/DirectoryChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ZipChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/TarChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/SevenZipChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/RarChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/EpubChapterSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/source/ImageEntryPolicy.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/source/ChapterSourceContractTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/source/ArchiveSafetyTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/source/EpubChapterSourceTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/source/ArchiveFixtures.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/memory/BoundedReaderMemoryBudgetTest.kt`
- Create: `reader-core/src/test/resources/archives/valid-rar4.rar`
- Create: `reader-core/src/test/resources/archives/encrypted-rar5.rar`
- Create: `reader-core/src/test/resources/archives/encrypted.zip`
- Create: `reader-core/src/test/resources/archives/encrypted.7z`
- Create: `reader-core/src/test/resources/archives/README.md`

- [ ] Consume Commons Compress and junrar dependencies and verify their XZ/LZMA runtime dependencies are present in the packaged dependency report. Dispatch by Plan 2 `assetKind` plus magic bytes; an extension alone never overrides conflicting magic.
- [ ] `SecureLocalPath` walks every component from the trusted storage root with `NOFOLLOW_LINKS`, rejects Windows reparse points/junctions, captures attributes/file key before open and verifies them immediately after channel creation. Use it for a standalone image, directory, and every archive/EPUB container. Tests inject a replacement between checks and require typed rejection.
- [ ] Standalone source exposes exactly one logical page, applies the 256 MiB encoded limit to actual reads, and has the same open/close/cancellation contract as container entries.
- [ ] Directory source accepts only regular image files beneath the root and rejects symlinks/reparse points by walking each component with `NOFOLLOW_LINKS`.
- [ ] ZIP/TAR/7z/RAR sources enumerate metadata first, normalize logical names, reject archive symlink/hardlink/device entries and encryption, enforce every fixed limit, and expose an input that charges actual decompressed bytes to one source-lifetime `ChapterExpansionBudget`. Forged sizes and repeated retry/open cannot bypass the 2 GiB cumulative counter.
- [ ] Implement the 256 MiB `BoundedReaderMemoryBudget` contract with checked arithmetic, FIFO cancellable waiters, idempotent leases, one-time `transferTo` ownership changes and pressure callbacks invoked outside its lock. For 7z use `SevenZFile`/seekable channel rather than `ArchiveStreamFactory`, set `maxMemoryLimitKiB=131_072`, reserve/release that allowance, and reject encrypted entries. For RAR use junrar 8's archive API and reject encrypted headers/files. Close channels/archives on success, error, and coroutine cancellation.
- [ ] Commit the four small binary archive fixtures in this task. `README.md` records generator/version, benign source contents, redistribution statement and SHA-256. Runtime tests never require WinRAR/7-Zip or network access; programmatic builders may create unencrypted ZIP/TAR/7z fixtures.
- [ ] EPUB reads `META-INF/container.xml`, bounded OPF metadata, manifest and spine. Configure secure processing; disallow DOCTYPE; disable external general/parameter entities, external DTD/schema access, XInclude and entity expansion before parsing each XML/XHTML document. Emit image spine items in spine order; for XHTML spine items, parse bounded markup and emit referenced local images in document order. Reject external/data/javascript URLs and path escape.
- [ ] Image filenames supported at this boundary are PNG, JPEG/JPG, GIF, BMP, and WBMP. Actual decoder support is checked in Task 4.
- [ ] Contract tests run standalone image and each container through identical ordering/open/close assertions. Safety tests cover zip-slip, UNC/drive paths, duplicate normalized names, symlink/reparse directory/container, archive symlink/hardlink, encrypted ZIP/7z/RAR, unsupported RAR, malicious 7z dictionary, forged sizes, repeated-read cumulative limit, exact limit and limit+1, too many entries, corrupt central directory, open-time replacement, cancellation, and file descriptor release (rename/delete immediately after close on Windows).
- [ ] For container.xml, OPF and XHTML separately, tests include local-file XXE, loopback-network XXE with zero requests, external parameter entity, billion-laughs/entity expansion, XInclude and external schema. Each must fail before resolving external content and release the file.

Commands:

```powershell
.\gradlew.bat :reader-core:test --tests "mihon.reader.source.*" --tests "mihon.reader.memory.*"
.\gradlew.bat :reader-core:test spotlessCheck
git add gradle/libs.versions.toml reader-core
git commit -m "feat(reader): load bounded local chapter containers"
```

### Task 4: Add metadata probing, animation, and bounded region decoding

**Files:**

- Modify: `reader-core/build.gradle.kts`
- Modify: `reader-core/src/main/kotlin/mihon/reader/image/ImageContracts.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/image/ImageIoPageDecoder.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/image/BudgetedDecodedTile.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/image/TilePlanner.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/image/ImageIoPageDecoderTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/image/TilePlannerTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/image/ImageFixtures.kt`

- [ ] Probe with `ImageInputStream`/`ImageReader` before raster allocation. Validate dimensions with checked `Long` multiplication and typed errors. Always dispose the reader and close the stream.
- [ ] Preserve alpha for transparent PNG. Normalize output to premultiplied ARGB. Decode JPEG/BMP/WBMP normally and reject absent readers/corrupt headers predictably.
- [ ] GIF metadata exposes frame count and one clamped duration `20..10_000 ms` per frame; decode selects `FrameId.frameIndex`, composites disposal/transparency correctly, and reserves both composition/intermediate peak rasters. A GIF whose composite canvas cannot reserve its conservative peak is rejected as region-unavailable because JDK GIF region reads cannot guarantee bounded animation composition.
- [ ] The load coordinator calls `ChapterSource.open(PageId)` separately for probe and for every full/region decode. `PageDecoder` consumes and closes exactly the supplied fresh `BoundedPageInput`; it never attempts to reopen or reuse a consumed stream. Full decode is used only at or below 16 MiB. Region decode uses `ImageReadParam.setSourceRegion`, chooses power-of-two source subsampling, clips edge tiles, and rejects readers that return a raster materially larger than the request. Probe results update the logical page's metadata; GIF frames never become extra chapter pages.
- [ ] The Task 3 `ReaderMemoryBudget` is shared by decoder, 7z work and later weighted cache. Reserve conservative peak bytes before any `BufferedImage`/conversion/GIF canvas allocation; wait or trigger registered unpinned eviction under contention; release on every error/cancellation; transfer only the final raster into cache residency. Different-page prefetches cannot allocate outside this ledger.
- [ ] Tile planner covers the requested visible source rectangle exactly with 1024-square tiles, includes one tile margin for smooth pan, deduplicates keys, and cannot generate coordinates outside the image.
- [ ] `DecodedTile` owns the final `DECODE_OUTPUT` lease returned by the decoder. `adoptAsCacheResident()` performs a one-time lease `transferTo(CACHE_RESIDENT)` without release/re-reserve; the tile retains that transferred lease until `close()`. `close()` releases the lease and flushes its `BufferedImage` exactly once. Tests prove no reservation gap, double reservation, double adoption or cancellation leak across decode→cache handoff.
- [ ] Test probe/full/region open counts and closure: each operation receives a new input, probe consumption cannot affect decode, archive cumulative expanded bytes include all opens, and cancellation closes the active input.
- [ ] Generate fixtures in test code: opaque JPEG, alpha PNG, multi-frame transparent GIF with disposal, corrupt bytes, exact-budget image, budget+one logical metadata, a streamed highly-compressible 20,000×20,000 PNG (written scanline-by-scanline without constructing its raster), and forged 200,001 dimension header. The extreme PNG is a real supported format decoded by the packaged JDK ImageIO provider; do not commit a giant binary.
- [ ] Register `extremeImageTest` as a dedicated `Test` task with `maxHeapSize = "384m"`, `maxParallelForks = 1`, JUnit tag `extreme-image`, and a test assertion on `Runtime.getRuntime().maxMemory()`. Exclude that tag from ordinary `test`. Assert budget high-water and real region-decode calls rather than merely catching `OutOfMemoryError`.

Commands:

```powershell
.\gradlew.bat :reader-core:test --tests "mihon.reader.image.*"
.\gradlew.bat :reader-core:extremeImageTest
.\gradlew.bat :reader-core:test spotlessCheck
git add reader-core
git commit -m "feat(reader): decode pages within a fixed memory budget"
```

### Task 5: Build weighted cache, pinning, prefetch, retry, and cancellation

**Files:**

- Create: `reader-core/src/main/kotlin/mihon/reader/cache/WeightedTileCache.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/cache/CacheMetrics.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/prefetch/PageLoadCoordinator.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/prefetch/PrefetchPolicy.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/cache/WeightedTileCacheTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/prefetch/PageLoadCoordinatorTest.kt`

- [ ] Implement access-order LRU over the shared 256 MiB reservation ledger with checked `Long` weights, visible-key pin leases, single-flight loads, and deterministic close outside the map lock. An individual object that cannot reserve space is delivered only after other unpinned/in-flight reservations allow it and is closed when its lease ends; no uncached bypass may exceed the ledger.
- [ ] Cache metrics expose resident bytes, pinned bytes, entry count, hit/miss/load/eviction count and high-water bytes. They contain no file paths or titles.
- [ ] Coordinator prioritizes visible work, then two ahead/one behind according to mode/direction. It cancels obsolete work and checks cancellation before opening input, after probe, after decode, and before cache insertion.
- [ ] A page error is memoized only until explicit retry or the asset modification tuple changes. Retry invalidates every tile/frame for the page and starts a fresh single flight.
- [ ] Test exact capacity, one byte over, pinned eviction pressure, concurrent duplicate request, concurrent different-page prefetch, 7z reservation contention, failed load cleanup, cancellation at each boundary, direction reversal, chapter transition, explicit retry, and 10,000-page simulated chapter with total resident+in-flight high-water at or below capacity.

Commands:

```powershell
.\gradlew.bat :reader-core:test --tests "mihon.reader.cache.*" --tests "mihon.reader.prefetch.*"
.\gradlew.bat :reader-core:test spotlessCheck
git add reader-core
git commit -m "feat(reader): bound page cache and prefetch"
```

### Task 6: Implement the reader session and durable chapter continuity

**Files:**

- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderAction.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderState.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderSession.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/DefaultReaderSession.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderGenerationSource.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/AnimationCoordinator.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ProgressDebouncer.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/session/ReaderSettings.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/session/DefaultReaderSessionTest.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/session/ProgressDebouncerTest.kt`

- [ ] State is a sealed load state plus chapter/page descriptors, selected index, mode, cover offset, scale/zoom/pan, viewport, visible tile leases, error, adjacent-chapter availability, and cache metrics. Reducer-style actions validate all indexes and numeric values.
- [ ] Opening requests a strictly increasing generation from an injected runtime-wide `ReaderGenerationSource`, cancels prior loads, resolves the asset, enumerates pages, restores `lastPageRead.coerceIn(0L..lastIndex.toLong()).toInt()` only after the Long clamp, resets the per-generation sequence to zero, and requests visible content. Empty chapters fail with `EMPTY_CHAPTER`.
- [ ] Next/previous operates on logical page/spread for paged modes and viewport anchor for continuous modes. Crossing an edge flushes progress, closes source/leases, opens the adjacent readable chapter, and lands at its first/last page according to direction.
- [ ] Recompute layout without changing selected page when mode, cover offset, scale, zoom, viewport or direction changes. RTL changes intent mapping/placement but not persisted page identity.
- [ ] Accumulate read duration only while foreground, content is visible, and the session is not loading/error. Clamp each clock delta and use an injected monotonic clock in tests.
- [ ] Increment sequence for every progress snapshot. Debounce movements 750 ms; flush exact latest position on chapter transition, close and shutdown. `closeAndFlush()` is idempotent and suspends until the final write completes before releasing the source/cache scope.
- [ ] `AnimationCoordinator` reads probed frame count/durations, requests the current `FrameId`, advances on an injected monotonic delay, loops, pauses when hidden/backgrounded, restarts at frame zero on retry, and cancels immediately on page/chapter/session change. Only the current and replacement frame may be pinned.
- [ ] UI callers use `closeAndFlush()` from a coroutine and await navigation only after completion. `cancelWithoutFlush()` is an emergency process-failure fallback and never masquerades as a successful close; core contains no `runBlocking`.
- [ ] Tests cover all modes, dual offsets, RTL, Webtoon anchors, Long overflow restore, backwards persistence, stale generation/sequence, two concurrent sessions with globally unique tokens, final-page completion, transition both directions, no-adjacent edge, retry, corrupt middle page, animation timing/loop/pause/retry/cancellation and frame retention, duration pause/resume, close race and idempotent cleanup.

Commands:

```powershell
.\gradlew.bat :reader-core:test --tests "mihon.reader.session.*"
.\gradlew.bat :reader-core:test spotlessCheck
git add reader-core
git commit -m "feat(reader): coordinate sessions and chapter progress"
```

### Task 7: Persist reader settings and wire runtime ownership

**Files:**

- Modify: `desktop-app/build.gradle.kts`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderSettingsStore.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderFactory.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/preferences/DesktopPreferenceStoreTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/DesktopReaderSettingsStoreTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/DesktopRuntimeFactoryTest.kt`

- [ ] Add `implementation(project(":reader-core"))`. Adapt `SqlDelightLibraryRepository` to both asset and progress contracts without leaking SQLDelight into core.
- [ ] Persist mode, cover offset, scale, click regions, wheel behavior, and last non-transient reader window mode using versioned preference keys. Unknown/corrupt values fall back independently, not by resetting the entire preferences file.
- [ ] Runtime creates one source factory, decoder, shared budget/cache, runtime-wide atomic generation source, reader factory, and application coroutine scope. Add suspending `shutdown()` that rejects new sessions, awaits every `closeAndFlush()`, then closes the database and aggregates suppressed failures. Existing synchronous `close()` is a non-EDT shutdown-hook/test fallback that delegates through a dedicated blocking shutdown executor; normal Compose Back/exit never calls it.
- [ ] No reader service starts during `--verify-foundation`; CLI verification must remain headless and deterministic.
- [ ] Tests cover defaults, round trip, corrupt/unknown values, globally unique generations across concurrent sessions, suspending runtime shutdown order, non-EDT fallback close, rejection of fallback close on EDT, double close, session factory isolation with shared bounded cache, and failure suppression.

Commands:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.reader.*" --tests "mihon.desktop.DesktopRuntimeFactoryTest" --tests "mihon.desktop.preferences.DesktopPreferenceStoreTest"
.\gradlew.bat :reader-core:test :desktop-library-data:test :desktop-app:test spotlessCheck
git add desktop-app
git commit -m "feat(reader): own Windows reader services at runtime"
```

### Task 8: Build the Compose reader canvas for all six modes

**Files:**

- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderScreen.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderCanvas.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/PagedReader.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ContinuousReader.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChrome.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ComposeTileBridge.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/AnimatedPage.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderSemantics.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderLayoutTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ComposeTileBridgeTest.kt`

- [ ] Render paged single/dual modes from core grouping. Respect LTR/RTL placement, cover offset, original/fit-width/fit-height, smooth zoom and clamped pan. Use a neutral near-black reading surface and existing Material 3 colors for chrome.
- [ ] Continuous mode uses virtualized page items with the configured gap. Webtoon uses virtualized zero-gap fit-width items. Report viewport anchors to core; never compose all chapter pages or retain offscreen Compose images.
- [ ] Convert `BufferedImage` to Compose `ImageBitmap` once per visible `TileKey`, enforce the separate 96 MiB bridge ledger before conversion, and release the old bridge record on disposal. Static tiles use replace-after-release under pressure; animations reserve the replacement before swap. Test 16 MiB single/dual GIF boundaries, two simultaneous dual-page frame swaps (64 MiB), tiled dual pages, pressure cancellation and retained-byte high-water separately from core cache.
- [ ] `AnimatedPage` renders the frame selected by core, cross-swaps only after the replacement bitmap is ready, and reports visibility/background changes back to the animation coordinator. It does not own an independent timer.
- [ ] Chrome contains Back, title/chapter, page `current / total`, mode menu, scale menu, cover toggle when dual, zoom controls, retry, fullscreen/borderless actions, settings, and a compact cache diagnostic available only under `MIHON_W_READER_DEBUG=1`.
- [ ] Reader settings exposes three click-region actions/boundaries, wheel behavior, mode, scale and cover offset. Region editing uses constrained boundaries that always cover 0–100% without overlap; Reset restores the fixed defaults. Saving updates the active session and the versioned store immediately.
- [ ] Hide chrome after 2.5 seconds of reading input and reveal on center click/mouse movement near top. Focus and semantic labels remain available while visually hidden.
- [ ] Loading, empty, corrupt page, missing file and unsupported format states are visible and retryable where appropriate. Errors must not display raw absolute paths.
- [ ] Compose tests use fixed 1280×800 and 800×1000 windows and assert test tags/semantics, visual grouping, RTL placement, dual offset, continuous/Webtoon gap, zoom transform, distinct timed GIF frame pixels, animation pause/offscreen disposal, 96 MiB bridge pressure, chrome/settings actions, error/retry and keyboard focus.

Commands:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.ui.reader.*"
.\gradlew.bat :desktop-app:test :reader-core:test spotlessCheck
git add desktop-app
git commit -m "feat(reader): render every Windows reading mode"
```

### Task 9: Add desktop input mapping and window-mode control

**Files:**

- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/input/ReaderInputMapper.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/input/ClickRegionPolicy.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/window/ReaderWindowController.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/Main.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/window/WindowPlacement.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/input/ReaderInputMapperTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/input/ClickRegionPolicyTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/window/ReaderWindowControllerTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/window/WindowPlacementTest.kt`

- [ ] Map the fixed default keys, wheel, Ctrl+wheel, mouse side buttons, touchpad scroll/pinch and click regions into core actions. Consume an event only when mapped. Text/menu controls receive events first.
- [ ] Direction-aware next/previous is centralized in the mapper. Continuous wheel scroll is pixel/anchor movement, while paged wheel crosses one logical unit after an accumulated threshold and rate limit.
- [ ] Click regions are normalized fractions, reject overlap/gaps on load, and retain center reveal behavior. Touch taps reuse regions; pinch zoom keeps the gesture centroid stable.
- [ ] Controller models `NORMAL`, `FULLSCREEN`, and `BORDERLESS`; saves normal bounds before leaving normal; `Esc` returns to normal before closing reader. Rapid toggles are serialized and never create a duplicate application window.
- [ ] Main window provides the actual window state callbacks to reader UI; core remains unaware of AWT/Compose window APIs.
- [ ] Tests cover every key in LTR/RTL, modifier precedence, wheel thresholds, continuous behavior, click edges, side buttons, pinch centroid, focus non-interception, invalid settings, all transition pairs, escape precedence, bounds restore and rapid toggles.

Commands:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.reader.input.*" --tests "mihon.desktop.reader.window.*" --tests "mihon.desktop.window.WindowPlacementTest"
.\gradlew.bat :desktop-app:test spotlessCheck
git add desktop-app
git commit -m "feat(reader): support Windows input and window modes"
```

### Task 10: Open readable chapters from manga details

**Files:**

- Modify: `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopDestination.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopNavigator.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryPresenter.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/navigation/DesktopDestinationTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryPresenterTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/MangaDetailScreenTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/ReaderNavigationIntegrationTest.kt`

- [ ] Add a typed `Reader(chapterId: Long)` destination whose IDs cannot be zero/negative. Back returns to the exact selected manga/detail pane and preserves library search/scroll state.
- [ ] Detail presenter resolves reader availability for each chapter using `ReaderLibraryPort`, without opening archives on the UI thread. Model states are readable, missing/moved, and remote-only.
- [ ] Replace `Reader arrives in Plan 3` with `Read`/`Continue · Page N`. Enable only readable local chapters; missing/moved content shows `Locate or re-import local content`; remote-only shows `Available after source support`.
- [ ] Clicking passes the exact chapter ID, creates a session, and shows `ReaderScreen`. Startup/open work runs off the EDT/Compose UI dispatcher. Back/close flushes and disposes exactly once.
- [ ] When progress flows invalidate, the detail row updates its read/page label after returning from reader.
- [ ] Tests assert exact ID routing, disabled reasons, no work on UI dispatcher, double-click single session, open error/back, restored detail state and updated progress.

Commands:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.ui.ReaderNavigationIntegrationTest" --tests "mihon.desktop.ui.library.*" --tests "mihon.desktop.navigation.*"
.\gradlew.bat :reader-core:test :desktop-library-data:test :desktop-app:test spotlessCheck
git add desktop-app
git commit -m "feat(reader): launch local chapters from the library"
```

### Task 11: Add deterministic packaged-reader verification

**Files:**

- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/ReaderFixtureBuilder.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/PackagedReaderScenarioTest.kt`
- Create: `scripts/verify-desktop-reader.ps1`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommandRunner.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/cli/DesktopCommandTest.kt`
- Modify: `.github/workflows/build.yml`
- Create: `docs/superpowers/evidence/windows-reader-core.md`

- [ ] Add an internal `--verify-reader=<fixture-root>` command accepted only when `MIHON_W_READER_VERIFY=1`. It imports a deterministic local fixture, opens standalone image and each container/mode through the same runtime/session/decoder factories, decodes required visible tiles, samples distinct GIF frame hashes before/after its duration, advances, transitions, flushes, closes, reopens, validates progress, prints one JSON summary line, and exits nonzero on any failure. Record this as packaged core/render-pipeline verification, not as a live Compose UI claim.
- [ ] Fixture builder creates standalone image, directory, ZIP/CBZ, TAR/CBT, 7z/CB7, RAR/CBR from the already committed Task 3 resource, and EPUB. Runtime verification never shells out to WinRAR/7-Zip and validates the committed fixture hashes/provenance before use.
- [ ] Containers include opaque, transparent, animated, corrupt and nested/numeric entries across the matrix; at least directory, CBZ and EPUB include the streamed real 20,000×20,000 PNG. The packaged CLI scenario proves real ImageIO region decoding, GIF frame changes, corrupt-page isolation, and core resident+in-flight high-water. Compose bridge high-water is proved by Task 8 UI tests and Task 12 live UI evidence, and is labeled separately.
- [ ] Script runs foundation verification, `:reader-core:test`, the dedicated `:reader-core:extremeImageTest`, both desktop module tests, SQLDelight migration check, Spotless, and `packageDistributionForCurrentOS`; records the extreme worker `maxMemory`; launches `MihonW.exe` in three separate processes (initial/open, reopen/continue, final completion), enforces a 90-second timeout each, captures stdout/stderr, validates JSON and DB state, and deletes the temporary root after processes release handles.
- [ ] `.github/workflows/build.yml` Windows job runs `scripts/verify-desktop-reader.ps1` and uploads the EXE plus verifier logs on failure. Android build/test job remains byte-for-byte unchanged except shared cache effects.
- [ ] Evidence records dependency versions, RED/GREEN commands, test counts, cache high-water, fixture SHA-256 values, packaged executable path/hash, process exit codes, progress rows, reviewers and findings/fixes.

Commands:

```powershell
pwsh -NoProfile -File .\scripts\verify-desktop-reader.ps1
```

Expected: final line `Mihon W desktop reader verification passed.`; packaged executable exists at `desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe`; no Java/EXE process retains the verifier root.

```powershell
git add desktop-app scripts .github/workflows/build.yml docs/superpowers/evidence/windows-reader-core.md
git commit -m "ci(reader): verify packaged Windows reading flows"
```

### Task 12: Manual Windows acceptance and final boundary audit

**Files:**

- Modify: `docs/superpowers/evidence/windows-reader-core.md`
- Modify: `docs/superpowers/plans/2026-09-02-windows-reader-core.md`
- Create: `docs/superpowers/evidence/windows-reader-core/01-detail-read.png`
- Create: `docs/superpowers/evidence/windows-reader-core/02-single-ltr.png`
- Create: `docs/superpowers/evidence/windows-reader-core/03-single-rtl.png`
- Create: `docs/superpowers/evidence/windows-reader-core/04-dual-cover-offset.png`
- Create: `docs/superpowers/evidence/windows-reader-core/05-vertical.png`
- Create: `docs/superpowers/evidence/windows-reader-core/06-webtoon.png`
- Create: `docs/superpowers/evidence/windows-reader-core/07-image-edge-cases.png`
- Create: `docs/superpowers/evidence/windows-reader-core/07a-animated-frame-a.png`
- Create: `docs/superpowers/evidence/windows-reader-core/07b-animated-frame-b.png`
- Create: `docs/superpowers/evidence/windows-reader-core/08-zoom-pan-chrome.png`
- Create: `docs/superpowers/evidence/windows-reader-core/09-window-modes.png`
- Create: `docs/superpowers/evidence/windows-reader-core/10-missing-content.png`

- [ ] Build and launch the packaged EXE, not `gradle run`, with a fresh data directory containing the verifier library.
- [ ] Capture and inspect screenshots at 1280×800 and a narrow portrait window for: manga detail with enabled Read/Continue; single LTR and RTL; dual cover offset on/off; vertical; Webtoon; transparent page; two visibly different timed frames of the same animated page; corrupt page error; zoom/pan; chrome hidden/visible; fullscreen; borderless; missing-content error.
- [ ] Use real mouse/keyboard/touchpad input where available: click regions, side buttons if present, wheel, Ctrl+wheel, arrow/Page keys, Home/End, zoom reset, F/B/Esc. Record unavailable physical hardware explicitly; automated mappings are not mislabeled as physical acceptance.
- [ ] Read through a chapter boundary, close mid-chapter after navigating backwards, relaunch the EXE, and verify exact page restoration, read completion, duration increase, and detail-row refresh.
- [ ] Observe Task Manager/process metrics while rapidly traversing a long/extreme chapter for five minutes. Record working-set start/peak/end and core/Compose high-water metrics; total chapter length must not cause linear growth.
- [ ] Inspect logs for uncaught exceptions, OOM, native decoder errors, ANR-like UI stalls, leaked handles, and raw absolute paths in UI.
- [ ] Run the complete fresh boundary suite with `--rerun-tasks`:

```powershell
.\gradlew.bat :reader-core:test :reader-core:extremeImageTest :desktop-library-data:test :desktop-library-data:verifySqlDelightMigration :desktop-app:test :app:testDebugUnitTest :app:assembleDebug spotlessCheck --rerun-tasks
pwsh -NoProfile -File .\scripts\verify-desktop-reader.ps1
git status --short
```

- [ ] Request one final independent reviewer to compare the approved design, every checked plan item, implementation, tests, evidence and packaged behavior. Fix and re-run for every Critical/Important finding until approved.
- [ ] Mark a checkbox complete only when its evidence exists. Replace all temporary `Plan 3`, `TODO`, placeholder reader labels, debug-only behavior and test fixture paths in production UI.
- [ ] Confirm `git status --short` contains only the Task 12 Files entries (plan, evidence markdown, and the listed screenshot directory), then commit:

```powershell
git add docs/superpowers/evidence/windows-reader-core.md docs/superpowers/evidence/windows-reader-core docs/superpowers/plans/2026-09-02-windows-reader-core.md
git commit -m "docs(reader): record Windows reader acceptance"
```

## Completion gate

Plan 3 is complete only when all of the following are simultaneously true:

- [ ] All checkboxes above are checked with matching evidence.
- [ ] `reader-core`, `desktop-library-data`, and `desktop-app` tests pass from a clean rerun.
- [ ] SQLDelight migration verification and Spotless pass.
- [ ] The packaged EXE verifier passes across three processes and progress persists.
- [ ] All six modes, three scale modes, cover offsets, RTL, zoom/pan, chapter continuity, window modes, and input mappings have automated coverage.
- [ ] Standalone image, directory, CBZ/ZIP, CBT/TAR, CB7/7z, CBR/RAR, EPUB and every image edge fixture have contract coverage.
- [ ] Resident decoded cache never exceeds 256 MiB, the Compose bridge never exceeds 96 MiB, a full decode never exceeds 16 MiB, and extreme images prove region decode under a 384 MiB test heap.
- [ ] Manual packaged Windows evidence includes screenshots and real-input results without crashes, leaked handles, raw paths, or linear memory growth.
- [ ] Final independent review reports no open Critical or Important finding.
- [ ] Working tree is clean after the final documentation commit; no branch is pushed unless the user explicitly requests it.
