# Reader Auto Cache Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global reader toggle that pre-caches all images for the current and next chapter in order when enabled from the in-reader Reading mode sheet.

**Architecture:** Keep the preference global in `ReaderPreferences` and expose it in the in-reader `ReadingModePage`. Add a small reader-scoped coordinator that starts or cancels sequential caching jobs from `ReaderViewModel`, and extend `HttpPageLoader` with an awaitable single-page cache API so the coordinator can cache online chapters without relying on the viewer-only `loadPage()` contract.

**Tech Stack:** Kotlin, coroutines, JUnit 5, MockK, Android Compose settings UI.

---

### Task 1: Cover sequential auto-cache orchestration with tests

**Files:**
- Create: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/ReaderAutoCacheManagerTest.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderAutoCacheManager.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `update caches current chapter before next chapter when enabled`() = runTest {
    // Arrange manager with fake chapter loader/cache lambdas.
    // Act by enabling auto cache for viewer chapters.
    // Assert current chapter work completes before next chapter work.
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: FAIL because `ReaderAutoCacheManager` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal class ReaderAutoCacheManager(
    private val scope: CoroutineScope,
    private val prepareChapter: suspend (ReaderChapter) -> Unit,
    private val canCacheChapter: (ReaderChapter) -> Boolean,
    private val cacheChapterPages: suspend (ReaderChapter) -> Unit,
) {
    private var job: Job? = null

    fun update(enabled: Boolean, viewerChapters: ViewerChapters?) {
        job?.cancel()
        if (!enabled || viewerChapters == null) return

        job = scope.launch {
            listOfNotNull(viewerChapters.currChapter, viewerChapters.nextChapter).forEach { chapter ->
                prepareChapter(chapter)
                if (canCacheChapter(chapter)) {
                    cacheChapterPages(chapter)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: PASS

### Task 2: Add awaitable page caching to the online page loader

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `cachePage waits for a queued online page to become ready`() = runTest {
    // Exercise the awaitable cache API through a loader double or targeted unit test.
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: FAIL because the cache coordinator cannot await page completion yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
suspend fun awaitCache(page: ReaderPage) {
    val queuedPage = enqueue(page, PriorityPage.BACKGROUND)
    try {
        page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
    } finally {
        if (!currentCoroutineContext().isActive && queuedPage != null) {
            queue.remove(queuedPage)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: PASS

### Task 3: Wire the preference, UI toggle, and reader integration

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `disabling auto cache prevents coordinator work`() = runTest {
    // Assert ViewModel-side coordinator updates can be gated by preference state.
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: FAIL until the preference and ViewModel wiring exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
val autoCacheReaderChapters = preferenceStore.getBoolean("reader_auto_cache_chapters", false)

CheckboxItem(
    label = stringResource(MR.strings.pref_auto_cache_reader_chapters),
    subLabel = stringResource(MR.strings.pref_auto_cache_reader_chapters_summary),
    pref = screenModel.preferences.autoCacheReaderChapters,
)
```

- [ ] **Step 4: Run targeted test and app unit tests**

Run: `./gradlew :app:testStandardDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.ReaderAutoCacheManagerTest"`
Expected: PASS
