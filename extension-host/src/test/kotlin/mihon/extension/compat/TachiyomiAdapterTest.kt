package mihon.extension.compat

import eu.kanade.tachiyomi.source.CatalogueSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.source.model.FilterList as TFilterList
import eu.kanade.tachiyomi.source.model.MangasPage as TMangasPage
import eu.kanade.tachiyomi.source.model.Page as TPage
import eu.kanade.tachiyomi.source.model.SChapter as TSChapter
import eu.kanade.tachiyomi.source.model.SManga as TSManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate as TSMangaUpdate

class TachiyomiAdapterTest {

    private class MockTachiyomiSource : CatalogueSource {
        override val id: Long = 987654321L
        override val name: String = "Mock Tachiyomi Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): TMangasPage {
            val manga = TSManga.create().apply {
                url = "/manga/popular/$page"
                title = "Popular Title $page"
                artist = "Popular Artist"
                author = "Popular Author"
                description = "Popular Description"
                genre = "Action, Adventure"
                status = TSManga.ONGOING
                thumbnail_url = "https://example.com/cover.jpg"
                initialized = true
            }
            return TMangasPage(listOf(manga), hasNextPage = true)
        }

        override suspend fun getLatestUpdates(page: Int): TMangasPage {
            val manga = TSManga.create().apply {
                url = "/manga/latest/$page"
                title = "Latest Title $page"
            }
            return TMangasPage(listOf(manga), hasNextPage = false)
        }

        override suspend fun getSearchManga(page: Int, query: String, filters: TFilterList): TMangasPage {
            val manga = TSManga.create().apply {
                url = "/search?q=$query&p=$page"
                title = "Result for $query"
            }
            return TMangasPage(listOf(manga), hasNextPage = false)
        }

        override suspend fun getMangaUpdate(
            manga: TSManga,
            chapters: List<TSChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): TSMangaUpdate {
            if (manga.url == "/manga/partial") {
                val partial = TSManga.create().apply {
                    description = "Partial details"
                    initialized = true
                }
                return TSMangaUpdate(partial, emptyList())
            }
            val updatedManga = manga.apply {
                description = "Detailed description"
                initialized = true
            }
            val ch1 = TSChapter.create().apply {
                url = "/chapter/1"
                name = "Chapter 1"
                chapter_number = 1.0f
                date_upload = 1700000000L
                scanlator = "ScanGroup"
            }
            return TSMangaUpdate(updatedManga, listOf(ch1))
        }

        override suspend fun getPageList(chapter: TSChapter): List<TPage> {
            return listOf(
                TPage(0, url = chapter.url, imageUrl = "https://example.com/page0.jpg"),
                TPage(1, url = chapter.url, imageUrl = "https://example.com/page1.jpg"),
            )
        }
    }

    @Test
    fun `adapter maps all catalogue source operations correctly`(): Unit = runBlocking {
        val mockSource = MockTachiyomiSource()
        val adapter = TachiyomiCatalogueSourceAdapter(mockSource)

        adapter.id shouldBe 987654321L
        adapter.name shouldBe "Mock Tachiyomi Source"
        adapter.lang shouldBe "en"
        adapter.supportsLatest shouldBe true

        // Popular
        val pop = adapter.getPopularManga(1)
        pop.hasNextPage shouldBe true
        pop.mangas shouldHaveSize 1
        val firstManga = pop.mangas.first()
        firstManga.url shouldBe "/manga/popular/1"
        firstManga.title shouldBe "Popular Title 1"
        firstManga.artist shouldBe "Popular Artist"
        firstManga.author shouldBe "Popular Author"
        firstManga.genre shouldBe listOf("Action", "Adventure")
        firstManga.status shouldBe SManga.ONGOING
        firstManga.thumbnailUrl shouldBe "https://example.com/cover.jpg"
        firstManga.initialized shouldBe true

        // Latest
        val latest = adapter.getLatestUpdates(2)
        latest.hasNextPage shouldBe false
        latest.mangas.first().url shouldBe "/manga/latest/2"

        // Search
        val search = adapter.searchManga(1, "solo leveling", FilterList())
        search.mangas.first().title shouldBe "Result for solo leveling"

        // Details
        val details = adapter.getMangaDetails(SManga(url = "/manga/1", title = "Test"))
        details.description shouldBe "Detailed description"
        details.initialized shouldBe true

        // Chapters
        val chapters = adapter.getChapterList(SManga(url = "/manga/1", title = "Test"))
        chapters shouldHaveSize 1
        val ch = chapters.first()
        ch.url shouldBe "/chapter/1"
        ch.name shouldBe "Chapter 1"
        ch.chapterNumber shouldBe 1.0f
        ch.scanlator shouldBe "ScanGroup"

        // Pages
        val pages = adapter.getPageList(SChapter(url = "/chapter/1", name = "Chapter 1"))
        pages shouldHaveSize 2
        pages[0].index shouldBe 0
        pages[0].imageUrl shouldBe "https://example.com/page0.jpg"
        pages[1].index shouldBe 1
        pages[1].imageUrl shouldBe "https://example.com/page1.jpg"
    }

    @Test
    fun `adapter merges partial manga details with the requested manga`(): Unit = runBlocking {
        val adapter = TachiyomiCatalogueSourceAdapter(MockTachiyomiSource())

        val details = adapter.getMangaDetails(
            SManga(
                url = "/manga/partial",
                title = "Existing title",
                thumbnailUrl = "https://example.com/existing.jpg",
            ),
        )

        details.url shouldBe "/manga/partial"
        details.title shouldBe "Existing title"
        details.thumbnailUrl shouldBe "https://example.com/existing.jpg"
        details.description shouldBe "Partial details"
    }
}
