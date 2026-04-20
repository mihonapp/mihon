package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderAutoCacheManagerTest {

    @Test
    fun `update does nothing when disabled`() = runTest {
        val calls = mutableListOf<String>()
        val manager = ReaderAutoCacheManager(
            scope = backgroundScope,
            prepareChapter = { calls += "prepare:${it.chapter.name}" },
            canCacheChapter = { true },
            cacheChapterPages = { calls += "cache:${it.chapter.name}" },
        )

        manager.update(
            enabled = false,
            viewerChapters = viewerChapters(current = chapter("current"), next = chapter("next")),
        )

        advanceUntilIdle()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `update caches current chapter before next chapter when enabled`() = runTest {
        val calls = mutableListOf<String>()
        val manager = ReaderAutoCacheManager(
            scope = backgroundScope,
            prepareChapter = { calls += "prepare:${it.chapter.name}" },
            canCacheChapter = { true },
            cacheChapterPages = { calls += "cache:${it.chapter.name}" },
        )

        manager.update(
            enabled = true,
            viewerChapters = viewerChapters(current = chapter("current"), next = chapter("next")),
        )

        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "prepare:current",
                "cache:current",
                "prepare:next",
                "cache:next",
            ),
            calls,
        )
    }

    @Test
    fun `update skips chapters that are not cacheable`() = runTest {
        val calls = mutableListOf<String>()
        val manager = ReaderAutoCacheManager(
            scope = backgroundScope,
            prepareChapter = { calls += "prepare:${it.chapter.name}" },
            canCacheChapter = { it.chapter.name == "current" },
            cacheChapterPages = { calls += "cache:${it.chapter.name}" },
        )

        manager.update(
            enabled = true,
            viewerChapters = viewerChapters(current = chapter("current"), next = chapter("next")),
        )

        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "prepare:current",
                "cache:current",
                "prepare:next",
            ),
            calls,
        )
    }

    @Test
    fun `update cancels previous job before starting a new one`() = runTest {
        val calls = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val manager = ReaderAutoCacheManager(
            scope = backgroundScope,
            prepareChapter = { calls += "prepare:${it.chapter.name}" },
            canCacheChapter = { true },
            cacheChapterPages = { chapter ->
                calls += "cache:${chapter.chapter.name}"
                try {
                    awaitCancellation()
                } finally {
                    cancelled += chapter.chapter.name
                }
            },
        )

        manager.update(
            enabled = true,
            viewerChapters = viewerChapters(current = chapter("first"), next = chapter("second")),
        )
        runCurrent()

        manager.update(
            enabled = true,
            viewerChapters = viewerChapters(current = chapter("replacement"), next = null),
        )
        runCurrent()

        assertEquals(listOf("first"), cancelled)
        assertEquals(
            listOf(
                "prepare:first",
                "cache:first",
                "prepare:replacement",
                "cache:replacement",
            ),
            calls,
        )
    }

    private fun viewerChapters(current: ReaderChapter, next: ReaderChapter?): ViewerChapters {
        return ViewerChapters(
            currChapter = current,
            prevChapter = null,
            nextChapter = next,
        )
    }

    private fun chapter(name: String, pageCount: Int = 2): ReaderChapter {
        val dbChapter = ChapterImpl().apply {
            id = name.hashCode().toLong()
            manga_id = 1L
            url = "/$name"
            this.name = name
        }
        val readerChapter = ReaderChapter(dbChapter)
        val pages = List(pageCount) { index ->
            ReaderPage(index).apply {
                chapter = readerChapter
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)
        return readerChapter
    }
}
