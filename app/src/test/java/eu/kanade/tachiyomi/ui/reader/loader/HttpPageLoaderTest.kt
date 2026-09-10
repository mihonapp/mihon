package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Response
import org.junit.jupiter.api.Test

class HttpPageLoaderTest {

    @Test
    fun `retry reloads a ready page after an image decoding failure`() {
        val chapter = mockk<ReaderChapter>(relaxed = true)
        val source = mockk<HttpSource>()
        val chapterCache = mockk<ChapterCache>(relaxed = true)
        val response = mockk<Response>(relaxed = true)
        val page = ReaderPage(index = 0, imageUrl = "https://example.com/page.jpg").apply {
            status = Page.State.Ready
        }
        val loader = HttpPageLoader(chapter, source, chapterCache)
        coEvery { source.getImage(page) } returns response

        try {
            loader.retryPage(page)

            coVerify(timeout = 5_000, exactly = 1) { source.getImage(page) }
        } finally {
            loader.recycle()
        }
    }
}
