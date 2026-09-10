package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class HttpPageLoaderTest {

    @Test
    fun `a cached page that no longer decodes is downloaded again`() = runBlocking {
        val imageUrl = "https://example.com/page.jpg"
        val page = readerPage(imageUrl)
        val chapterCache = mockk<ChapterCache>(relaxed = true)
        every { chapterCache.isImageInCache(imageUrl) } returns true
        every { chapterCache.isUsableImageInCache(imageUrl) } returns false
        val source = mockk<HttpSource>(relaxed = true)
        coEvery { source.getImage(page) } returns mockk<Response>(relaxed = true)
        val loader = HttpPageLoader(page.chapter, source, chapterCache)

        try {
            val subscription = launch { loader.loadPage(page) }

            coVerify(timeout = 5_000, exactly = 1) { source.getImage(page) }
            subscription.cancel()
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `a cached page that still decodes is served without downloading it again`() = runBlocking {
        val imageUrl = "https://example.com/page.jpg"
        val page = readerPage(imageUrl)
        val chapterCache = mockk<ChapterCache>(relaxed = true)
        every { chapterCache.isImageInCache(imageUrl) } returns true
        every { chapterCache.isUsableImageInCache(imageUrl) } returns true
        val source = mockk<HttpSource>(relaxed = true)
        val loader = HttpPageLoader(page.chapter, source, chapterCache)
        val ready = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { page.statusFlow.first { it == Page.State.Ready } }
        }

        try {
            val subscription = launch { loader.loadPage(page) }
            ready.await()

            coVerify(exactly = 0) { source.getImage(any()) }
            subscription.cancel()
        } finally {
            loader.recycle()
        }
    }

    private fun readerPage(imageUrl: String): ReaderPage {
        val page = ReaderPage(index = 0, imageUrl = imageUrl)
        val chapter = mockk<ReaderChapter>(relaxed = true)
        every { chapter.pages } returns listOf(page)
        page.chapter = chapter
        return page
    }
}
