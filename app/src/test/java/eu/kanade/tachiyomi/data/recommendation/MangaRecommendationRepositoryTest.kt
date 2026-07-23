package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class MangaRecommendationRepositoryTest {

    @Test
    fun `network disabled never calls the source`() = runTest {
        val source = FakeSource()
        val repository = repository(local = listOf(domainManga("local", tags = listOf("romance"))))

        repository.observe(source, sourceManga("target", tags = "romance"), null, false).toList()

        assertEquals(0, source.searchCalls)
        assertEquals(0, source.detailCalls)
    }

    @Test
    fun `network budget is four requests with at most two details`() = runTest {
        val source = FakeSource().apply {
            search = { _, query, _ ->
                if (query == "Creator") {
                    MangasPage((1..12).map { sourceManga("creator-$it") }, false)
                } else {
                    MangasPage(listOf(sourceManga("similar", tags = "romance, school")), false)
                }
            }
            details = { manga ->
                SMangaUpdate(manga.copy().apply { author = "Creator" }, emptyList())
            }
        }
        val repository = repository(
            local = listOf(domainManga("local", tags = listOf("romance", "school"))),
        )
        val target = sourceManga("target", author = "Creator", tags = "romance, school")

        repository.observe(source, target, null, true).toList()

        assertEquals(2, source.searchCalls)
        assertEquals(2, source.detailCalls)
        assertEquals(4, source.searchCalls + source.detailCalls)
    }

    @Test
    fun `429 stops the page without a retry loop`() = runTest {
        val source = FakeSource().apply {
            search = { _, _, _ ->
                throw HttpException(429, "30")
            }
        }
        val repository = repository()

        repository.observe(
            source,
            sourceManga("target", author = "Creator", tags = "romance, school"),
            null,
            true,
        ).toList()

        assertEquals(1, source.searchCalls)
        assertEquals(0, source.detailCalls)
    }

    @Test
    fun `progressive emissions only append cards`() = runTest {
        val source = FakeSource().apply {
            search = { _, query, _ ->
                when (query) {
                    "Creator" -> MangasPage(
                        listOf(sourceManga("network-creator", author = "Creator", tags = "romance, school")),
                        false,
                    )
                    else -> MangasPage(
                        listOf(sourceManga("network-similar", author = "Other", tags = "romance, school")),
                        false,
                    )
                }
            }
        }
        val repository = repository(
            local = listOf(domainManga("local-similar", author = "Other", tags = listOf("romance", "school"))),
        )

        val emissions = repository.observe(
            source,
            sourceManga("target", author = "Creator", tags = "romance, school"),
            null,
            true,
        ).toList()

        assertTrue(emissions.size >= 3)
        emissions.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.creatorWorks.containsAll(previous.creatorWorks))
            assertTrue(next.similarManga.containsAll(previous.similarManga))
        }
    }

    @Test
    fun `local rows filter current excluded other sources and cross row duplicates`() = runTest {
        val source = FakeSource()
        val excluded = domainManga("excluded", author = "Other", tags = listOf("romance", "school"))
        val repository = repository(
            local = listOf(
                domainManga("target", author = "Creator", tags = listOf("romance", "school")),
                domainManga("creator", author = "Creator", tags = listOf("romance", "school")),
                domainManga("similar", author = "Other", tags = listOf("romance", "school")),
                excluded,
                domainManga("other-source", sourceId = 2L, tags = listOf("romance", "school")),
            ),
        )
        val excludedKeys = RecommendationMetadata.card(1L, excluded.toSourceManga()).identity.exposureKeys

        val rows = repository.observe(
            source,
            sourceManga("target", author = "Creator", tags = "romance, school"),
            null,
            false,
            excludedKeys,
        ).toList().last()
        val creatorUrls = rows.creatorWorks.map { it.manga.url }
        val similarUrls = rows.similarManga.map { it.manga.url }

        assertEquals(listOf("creator"), creatorUrls)
        assertEquals(listOf("similar"), similarUrls)
        assertTrue(rows.creatorWorks.all { it.sourceId == source.id })
        assertTrue(rows.similarManga.all { it.sourceId == source.id })
        assertTrue((creatorUrls intersect similarUrls.toSet()).isEmpty())
    }

    private fun repository(local: List<Manga> = emptyList()): MangaRecommendationRepository {
        return MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> local },
            aniListLoader = { emptyList() },
            seedProvider = { 1L },
        )
    }

    private fun sourceManga(
        url: String,
        author: String? = null,
        tags: String? = null,
    ): SManga = SManga.create().apply {
        this.url = url
        title = url
        this.author = author
        genre = tags
        initialized = true
    }

    private fun domainManga(
        url: String,
        sourceId: Long = 1L,
        author: String? = null,
        tags: List<String>? = null,
    ): Manga = Manga.create().copy(
        id = url.hashCode().toLong(),
        source = sourceId,
        url = url,
        title = url,
        author = author,
        genre = tags,
        initialized = true,
    )

    private fun Manga.toSourceManga(): SManga = sourceManga(url, author, genre?.joinToString(", "))

    private class FakeSource : Source {
        override val id = 1L
        override val name = "Fake"
        override val supportsLatest = false
        var searchCalls = 0
        var detailCalls = 0
        var search: suspend (Int, String, FilterList) -> MangasPage = { _, _, _ -> MangasPage(emptyList(), false) }
        var details: suspend (SManga) -> SMangaUpdate = { SMangaUpdate(it, emptyList()) }

        override suspend fun getPopularManga(page: Int): MangasPage = error("Not used")
        override suspend fun getLatestUpdates(page: Int): MangasPage = error("Not used")

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            searchCalls++
            return search(page, query, filters)
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            detailCalls++
            return details(manga)
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = error("Not used")
    }
}
