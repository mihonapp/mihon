package eu.kanade.tachiyomi.ui.webview

import androidx.lifecycle.ViewModelStore
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.manga.model.toDomainManga
import okhttp3.Headers
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

class WebViewViewModelTest {
    @Test
    fun findsMatchingMangaThroughUrlSearchWithoutResolvableSource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val mangaUrl = "https://example.com/title/123"
            val unrelatedUrl = "https://example.com/search"
            val manga = SManga.create().apply {
                url = "/123"
                title = "Manga"
            }
            val unrelatedManga = SManga.create().apply {
                url = "/456"
                title = "Another Manga"
            }
            val source = mockk<HttpSource> {
                every { id } returns 10L
                every { headers } returns Headers.Builder().build()
                every { getHomeUrl() } returns "https://example.com"
                every { getFilterList() } returns FilterList()
                every { getMangaUrl(manga) } returns mangaUrl
                every { getMangaUrl(unrelatedManga) } returns "https://example.com/title/456"
                coEvery { getSearchManga(1, mangaUrl, any()) } returns MangasPage(listOf(unrelatedManga, manga), false)
                coEvery { getSearchManga(1, unrelatedUrl, any()) } returns MangasPage(listOf(unrelatedManga), false)
            }
            val sourceManager = mockk<SourceManager> {
                coEvery { get(10L) } returns source
                coEvery { getAll() } returns listOf(source)
            }
            val domainManga = manga.toDomainManga(source.id)
            val networkToLocalManga = mockk<NetworkToLocalManga> {
                coEvery { this@mockk(domainManga) } returns domainManga.copy(id = 42L)
            }
            val model = WebViewViewModel(10L, sourceManager, mockk(), networkToLocalManga, mockk())
            viewModels.put("webview", model)

            model.canOpenManga(mangaUrl) shouldBe true
            coVerify(exactly = 0) { source.getSearchManga(any(), any(), any()) }
            model.resolveManga(mangaUrl) shouldBe 42L
            model.resolveManga(unrelatedUrl) shouldBe null
            model.canOpenManga("https://unrelated.example/title/123") shouldBe false
            coVerify(exactly = 1) { networkToLocalManga(any<Manga>()) }
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}
