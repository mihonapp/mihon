package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.data.track.anilist.dto.ALRecommendation
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRecommendationMedia
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRecommendationTitle
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class MangaRecommendationRepositoryTest {

    @Test
    fun `creator cards come from the current source instead of the local pool`() = runTest {
        val sourceMatch = sourceManga("alice", "Alice Work", author = "Alice")
        val localMatch = domainManga("stale-local", "Stale Local", author = "Alice")
        val descriptionOnly = domainManga("description", "Description Only", description = "Circle: Alice")
        val source = FakeSource(searchResults = mapOf("Alice" to listOf(sourceMatch)))
        val repository = repository(local = listOf(localMatch, descriptionOnly))

        val first = repository.observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).first { it.creatorWorks.isNotEmpty() }

        assertEquals(listOf("alice"), first.creatorWorks.map(SManga::url))
        assertEquals(1, source.requestCount.get())
    }

    @Test
    fun `creator details preserve canonical search identity and require exact creator`() = runTest {
        val searchIdentity = sourceManga("canonical-url", "Canonical title")
        val unrelated = sourceManga("unrelated", "Unrelated")
        val source = FakeSource(
            searchResults = mapOf("Alice" to listOf(searchIdentity, unrelated)),
            details = mapOf(
                "canonical-url" to SManga.create().apply { author = "Alice" },
                "unrelated" to SManga.create().apply { author = "Bob" },
            ),
        )
        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(1, result.creatorWorks.size)
        assertEquals("canonical-url", result.creatorWorks.single().url)
        assertEquals("Canonical title", result.creatorWorks.single().title)
        assertEquals("Alice", result.creatorWorks.single().author)
    }

    @Test
    fun `missing AniList track performs no external request`() = runTest {
        val externalCalls = AtomicInteger()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = {
                externalCalls.incrementAndGet()
                emptyList()
            },
        )

        repository.observeRecommendations(
            source = FakeSource(),
            manga = sourceManga("current", "Current", genre = "Romance"),
            aniListId = null,
            forceRefresh = true,
        ).toList()

        assertEquals(0, externalCalls.get())
    }

    @Test
    fun `AniList recommendation maps one exact title through current source`() = runTest {
        val exact = sourceManga("same-source", "Frieren")
        val source = FakeSource(
            searchResults = mapOf("Frieren" to listOf(exact, sourceManga("noise", "Frieren Extra"))),
        )
        val recommendation = aniListRecommendation(
            id = 10,
            title = "Frieren",
            genres = listOf("Fantasy"),
        )
        val result = repository(aniList = listOf(recommendation)).observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "Adventure"),
            aniListId = 154587,
            forceRefresh = true,
        ).toList().last()

        assertEquals(listOf("same-source"), result.similarManga.map(SManga::url))
        assertTrue(source.searchQueries.contains("Frieren"))
        assertTrue(source.filterListCalls.get() >= 1)
    }

    @Test
    fun `AniList evidence is fused with the current source exact genre route`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { query, filters ->
                when {
                    query == "External" -> listOf(sourceManga("external", "External"))
                    query.isEmpty() && filters.filterIsInstance<Filter.CheckBox>().single().state -> {
                        (1..12).map { sourceManga("romance-$it", "Romance $it") }
                    }
                    else -> emptyList()
                }
            },
        )
        val result = repository(
            aniList = listOf(aniListRecommendation(10, "External", genres = listOf("Romance"))),
        ).observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "Romance"),
            aniListId = 10,
            forceRefresh = true,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.any { it.url == "external" })
        assertEquals(0, source.popularRequests.get())
        assertEquals(2, source.requestCount.get())
    }

    @Test
    fun `ambiguous exact AniList mapping is hidden`() = runTest {
        val source = FakeSource(
            searchResults = mapOf(
                "Same" to listOf(
                    sourceManga("edition-a", "Same"),
                    sourceManga("edition-b", "Same"),
                ),
            ),
        )
        val result = repository(aniList = listOf(aniListRecommendation(11, "Same"))).observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "Mystery"),
            aniListId = 11,
            forceRefresh = true,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
    }

    @Test
    fun `legacy CatalogueSource Rx bridge supports creator search and details`() = runTest {
        val source = LegacyCatalogueSource(
            searchResult = sourceManga("legacy-work", "Legacy Work"),
            details = SManga.create().apply { author = "Alice" },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(listOf("legacy-work"), result.creatorWorks.map(SManga::url))
        assertEquals(1, source.searchCalls.get())
        assertEquals(1, source.detailCalls.get())
    }

    @Test
    fun `StubSource is authoritative hidden without invoking loaders`() = runTest {
        val localCalls = AtomicInteger()
        val aniListCalls = AtomicInteger()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ ->
                localCalls.incrementAndGet()
                emptyList()
            },
            aniListLoader = {
                aniListCalls.incrementAndGet()
                emptyList()
            },
        )

        val emissions = repository.observeRecommendations(
            source = StubSource(id = 42, lang = "en", name = "Missing"),
            manga = sourceManga("current", "Current", author = "Alice", genre = "Fantasy"),
            aniListId = 123,
            forceRefresh = true,
        ).toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.single().creatorWorks.isEmpty())
        assertTrue(emissions.single().similarManga.isEmpty())
        assertTrue(emissions.single().creatorAuthoritative)
        assertTrue(emissions.single().similarAuthoritative)
        assertEquals(0, localCalls.get())
        assertEquals(0, aniListCalls.get())
    }

    @Test
    fun `each creator search receives a fresh default FilterList`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(MutableTextFilter()) },
        )

        repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice; Bob"),
            aniListId = null,
            forceRefresh = true,
        ).toList()

        assertEquals(2, source.searchFilters.size)
        assertTrue(source.searchFilters[0] !== source.searchFilters[1])
        assertEquals(listOf("", ""), source.searchFilterInitialStates.sorted())
    }

    @Test
    fun `explicit creator mismatch is rejected without details request`() = runTest {
        val source = FakeSource(
            searchResults = mapOf(
                "Alice" to listOf(sourceManga("bob-work", "Bob Work", author = "Bob")),
            ),
            details = mapOf(
                "bob-work" to SManga.create().apply { author = "Alice" },
            ),
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertTrue(result.creatorWorks.isEmpty())
        assertEquals(0, source.detailRequests.get())
    }

    @Test
    fun `AniList bound path never requests popular and stays within ten source calls`() = runTest {
        val creatorCandidates = (1..12).map { sourceManga("creator-$it", "Creator $it") }
        val source = FakeSource(
            searchResults = buildMap {
                put("Alice", creatorCandidates)
                put("Bob", creatorCandidates)
                (1..4).forEach { index ->
                    put("External $index", listOf(sourceManga("external-$index", "External $index")))
                }
            },
            popular = (1..12).map { sourceManga("popular-$it", "Popular $it") },
        )
        val requests = mutableListOf<String>()
        val recommendations = (1..4).map { aniListRecommendation(it.toLong(), "External $it") }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { recommendations },
            onSourceRequest = { synchronized(requests) { requests += it } },
        )

        repository.observeRecommendations(
            source = source,
            manga = sourceManga(
                url = "bound-current",
                title = "Current",
                author = "Alice; Bob",
                genre = "Fantasy",
            ),
            aniListId = 100,
            forceRefresh = true,
        ).toList()

        assertTrue(requests.size <= 10, "source requests were $requests")
        assertFalse("popular" in requests)
        assertEquals(0, source.popularRequests.get())
    }

    @Test
    fun `unbound path stays within the twelve call hard limit`() = runTest {
        val unresolved = (1..12).map { sourceManga("unresolved-$it", "Unresolved $it") }
        val requests = mutableListOf<String>()
        val source = FakeSource(
            searchResults = mapOf(
                "Alice" to unresolved,
                "Bob" to unresolved,
            ),
            popular = unresolved,
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { error("AniList must not be queried without a track") },
            onSourceRequest = { synchronized(requests) { requests += it } },
        )

        repository.observeRecommendations(
            source = source,
            manga = sourceManga(
                url = "unbound-current",
                title = "Current",
                author = "Alice; Bob",
                genre = "Fantasy",
            ),
            aniListId = null,
            forceRefresh = true,
        ).toList()

        assertTrue(requests.size <= 12, "source requests were $requests")
    }

    @Test
    fun `partial creator hydration makes a fresh source search on the next visit`() = runTest {
        val unresolved = (1..6).map { sourceManga("work-$it", "Work $it") }
        val source = FakeSource(
            searchResults = mapOf("Alice" to unresolved),
            details = unresolved.associate { candidate ->
                candidate.url to SManga.create().apply { author = "Alice" }
            },
        )

        val repository = repository()
        val current = sourceManga("current", "Current", author = "Alice")
        val result = repository.observeRecommendations(
            source = source,
            manga = current,
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(4, source.detailRequests.get())
        assertTrue(source.detailRequests.get() > 2)
        assertEquals(4, result.creatorWorks.size)
        assertFalse(result.creatorAuthoritative)
        val requestsAfterFirst = source.requestCount.get()

        val next = repository.observeRecommendations(
            source = source,
            manga = current,
            aniListId = null,
        ).toList().last()
        assertTrue(source.requestCount.get() > requestsAfterFirst)
        assertTrue(next.creatorWorks.all { it.url.startsWith("work-") })
    }

    @Test
    fun `Chinese generic tags are ignored and a more specific genre changes the source route`() = runTest {
        val selectedRoutes = mutableListOf<String>()
        val source = FakeSource(
            filterFactory = {
                FilterList(TestGenreCheckBox("懸疑"), TestGenreCheckBox("校園"))
            },
            searchHandler = { query, filters ->
                val route = filters.filterIsInstance<Filter.CheckBox>()
                    .filter { it.state }
                    .map { RecommendationMetadata.normalizeGenre(it.name) }
                    .sorted()
                    .joinToString("+")
                if (query.isNotEmpty() || route.isEmpty()) {
                    emptyList()
                } else {
                    synchronized(selectedRoutes) { selectedRoutes += route }
                    (1..12).map { index -> sourceManga("$route-$index", "$route $index") }
                }
            },
        )
        val repository = repository()

        val mystery = repository.observeRecommendations(
            source = source,
            manga = sourceManga("mystery-current", "Mystery", genre = "日本, 悬疑"),
            aniListId = null,
        ).toList().last()
        val schoolMystery = repository.observeRecommendations(
            source = source,
            manga = sourceManga("school-current", "School Mystery", genre = "日本, 悬疑, 校园"),
            aniListId = null,
        ).toList().last()

        assertEquals(10, mystery.similarManga.size)
        assertEquals(10, schoolMystery.similarManga.size)
        assertTrue(mystery.similarManga.all { it.url.startsWith("mystery-") })
        assertTrue(schoolMystery.similarManga.all { it.url.startsWith("mystery+school_life-") })
        assertFalse(
            mystery.similarManga.take(4).map(SManga::url) == schoolMystery.similarManga.take(4).map(SManga::url),
        )
        assertEquals("mystery", selectedRoutes.first())
        assertEquals(listOf("mystery+school_life"), selectedRoutes.drop(1))
        assertEquals(0, source.detailRequests.get())
        assertEquals(0, source.popularRequests.get())
    }

    @Test
    fun `dynamic single select Chinese source retries filters and fuses two fresh genre routes`() = runTest {
        val filterGeneration = AtomicInteger()
        val routedGenres = mutableListOf<String>()
        val source = FakeSource(
            filterFactory = {
                if (filterGeneration.incrementAndGet() == 1) {
                    FilterList(Filter.Header("点击“重置”尝试刷新题材分类"))
                } else {
                    FilterList(
                        TestSortFilter("排序", arrayOf("热门", "更新时间")),
                        TestGenreSelect("题材", arrayOf("全部", "后宫", "悬疑")),
                    )
                }
            },
            searchHandler = { query, filters ->
                assertTrue(query.isEmpty())
                val genreFilter = filters.filterIsInstance<TestGenreSelect>().single()
                val genre = genreFilter.values[genreFilter.state]
                val canonicalGenre = RecommendationMetadata.normalizeGenres(genre).single()
                synchronized(routedGenres) { routedGenres += canonicalGenre }

                val sort = filters.filterIsInstance<TestSortFilter>().single().state
                assertEquals(Filter.Sort.Selection(index = 1, ascending = false), sort)
                (1..15).map { index ->
                    sourceManga("fresh-$canonicalGenre-$index", "Fresh $canonicalGenre $index")
                }
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "日本, 後宮, 懸疑"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.all { it.url.startsWith("fresh-") })
        assertEquals(setOf("harem", "mystery"), routedGenres.toSet())
        assertFalse("日本" in routedGenres)
        assertTrue(source.filterListCalls.get() >= 4)
        assertEquals(2, source.requestCount.get())
        assertEquals(0, source.popularRequests.get())
        assertEquals(0, source.detailRequests.get())
    }

    @Test
    fun `nHentai style text filters put the original tag in Tags instead of Categories`() = runTest {
        var seenCategories: String? = null
        var seenTags: String? = null
        val source = FakeSource(
            name = "nHentai",
            filterFactory = {
                FilterList(TestTextFilter("Categories"), TestTextFilter("Tags"))
            },
            searchHandler = { query, filters ->
                val textFilters = filters.filterIsInstance<Filter.Text>()
                seenCategories = textFilters.single { it.name == "Categories" }.state
                seenTags = textFilters.single { it.name == "Tags" }.state
                if (query.isEmpty() && seenTags == "愛情") {
                    (1..12).map { sourceManga("tag-$it", "Tag $it") }
                } else {
                    emptyList()
                }
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "日本, 愛情"),
            aniListId = null,
        ).toList().last()

        assertEquals("", seenCategories)
        assertEquals("愛情", seenTags)
        assertEquals(10, result.similarManga.size)
        assertEquals(0, source.detailRequests.get())
        assertEquals(0, source.popularRequests.get())
        assertEquals(1, source.requestCount.get())
    }

    @Test
    fun `ordinary explicit English Tags filter keeps its verified source results without detail hydration`() = runTest {
        val seenTags = mutableListOf<String>()
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Tags")) },
            searchHandler = { query, filters ->
                val seenTag = filters.filterIsInstance<Filter.Text>().single().state
                seenTags += seenTag
                if (query.isEmpty() && seenTag == "Romance") {
                    (1..12).map { sourceManga("romance-$it", "Romance $it") }
                } else {
                    emptyList()
                }
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "Romance, Comedy, School Life"),
            aniListId = null,
        ).toList().last()

        assertTrue("Romance" in seenTags)
        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.all { it.url.startsWith("romance-") })
        assertEquals(0, source.detailRequests.get())
        assertEquals(0, source.popularRequests.get())
        assertTrue(source.requestCount.get() <= 2)
    }

    @Test
    fun `a three hundred millisecond strong source response publishes cards within one second`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..24).map { sourceManga("fast-$it", "Fast $it") }
                } else {
                    emptyList()
                }
            },
            requestDelayMillis = 300,
        )
        var firstCardsAt: Long? = null

        repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "Romance"),
            aniListId = null,
        ).collect { rows ->
            if (rows.similarManga.isNotEmpty() && firstCardsAt == null) {
                firstCardsAt = testScheduler.currentTime
            }
        }

        assertTrue((firstCardsAt ?: Long.MAX_VALUE) <= 1_000L)
        assertEquals(0, source.detailRequests.get())
    }

    @Test
    fun `weak category text routes require candidate tag verification`() = runTest {
        val candidates = listOf(
            sourceManga("romance", "Romance"),
            sourceManga("action", "Action"),
        )
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Categories")) },
            searchHandler = { _, _ -> candidates },
            details = mapOf(
                "romance" to SManga.create().apply { genre = "愛情" },
                "action" to SManga.create().apply { genre = "アクション" },
            ),
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "romance"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(listOf("romance"), result.similarManga.map(SManga::url))
        assertEquals(2, source.detailRequests.get())
    }

    @Test
    fun `a 429 on a text route immediately stops further recommendation requests`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Tags")) },
            searchHandler = { _, _ -> throw HttpException(429) },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "tag-a, tag-b"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
        assertEquals(1, source.requestCount.get())
        assertEquals(0, source.popularRequests.get())
        assertEquals(0, source.detailRequests.get())
    }

    @Test
    fun `a 429 starts a short source cooldown without caching any cards`() = runTest {
        var now = 1_000L
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Tags")) },
            searchHandler = { _, _ -> throw HttpException(429) },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
        )
        val target = sourceManga("current", "Current", genre = "romance, school life")

        val first = repository.observeRecommendations(source, target, null).toList().last()
        assertTrue(first.similarManga.isEmpty())
        assertEquals(1, source.requestCount.get())

        val duringCooldown = repository.observeRecommendations(source, target, null).toList().last()
        assertTrue(duringCooldown.similarManga.isEmpty())
        assertEquals(1, source.requestCount.get())

        now += 46_000L
        repository.observeRecommendations(source, target, null).toList()
        assertEquals(2, source.requestCount.get())
    }

    @Test
    fun `nHentai source route 429 uses the shared host backoff`() = runTest {
        var now = 1_000L
        val relatedCalls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                relatedCalls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(emptyList())
            }
        }
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Tags")) },
            searchHandler = { _, _ -> throw HttpException(429) },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val target = sourceManga("/g/1/", "Current", genre = "romance")

        val limited = repository.observeRecommendations(source, target, null).toList().last()
        assertEquals(now + 60 * 1_000L, limited.retryAtMillis)
        assertEquals(1, source.requestCount.get())
        assertEquals(1, relatedCalls.get())

        now += 60 * 1_000L - 1L
        repository.observeRecommendations(source, target, null).toList()
        assertEquals(1, source.requestCount.get())
        assertEquals(1, relatedCalls.get())

        now += 1L
        repository.observeRecommendations(source, target, null).toList()
        assertEquals(2, source.requestCount.get())
        // The independent related empty-result cache is still fresh; only the generic route retries.
        assertEquals(1, relatedCalls.get())
    }

    @Test
    fun `source related produces ten similar cards with one budgeted call and no details`() = runTest {
        val related = (1..24).map { sourceManga("/g/$it/", "Related $it", genre = "tag-$it") }
        val requests = mutableListOf<String>()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return NhentaiRelatedOutcome.Success(related)
            }
        }
        val source = FakeSource()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = provider,
            onSourceRequest = { requests += it },
        )

        val result = repository.observeRecommendations(
            source = source,
            manga = sourceManga("/g/999/", "Current"),
            aniListId = null,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertEquals(listOf("source-related"), requests)
        assertEquals(0, source.requestCount.get())
        assertEquals(0, source.detailRequests.get())
    }

    @Test
    fun `partial source related results use at most one generic fallback request`() = runTest {
        for (relatedCount in 1..9) {
            val requests = mutableListOf<String>()
            val provider = object : NhentaiRelatedProvider {
                override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                    TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

                override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                    return NhentaiRelatedOutcome.Success(
                        (1..relatedCount).map {
                            sourceManga("related-$relatedCount-$it", "Related $it", genre = "Romance")
                        },
                    )
                }
            }
            val source = FakeSource(
                filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
                searchHandler = { _, filters ->
                    if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                        (1..12).map {
                            sourceManga("fallback-$relatedCount-$it", "Fallback $it", genre = "Romance")
                        }
                    } else {
                        emptyList()
                    }
                },
            )
            val repository = MangaRecommendationRepository(
                localCandidateLoader = { _, _ -> emptyList() },
                aniListLoader = { emptyList() },
                nhentaiRelatedProvider = provider,
                requestCoordinator = RecommendationRequestCoordinator(
                    monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
                    randomUnit = { 0.0 },
                ),
                monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
                onSourceRequest = { requests += it },
            )

            val result = repository.observeRecommendations(
                source = source,
                manga = sourceManga("/g/999/", "Current", genre = "Romance"),
                aniListId = null,
            ).toList().last()

            assertEquals(10, result.similarManga.size, "related count $relatedCount")
            assertEquals(listOf("source-related", "genre-search"), requests, "related count $relatedCount")
            assertEquals(1, source.requestCount.get(), "related count $relatedCount")
            assertEquals(0, source.detailRequests.get(), "related count $relatedCount")
        }
    }

    @Test
    fun `empty related result is target scoped negative cached`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(emptyList())
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()
        val target = sourceManga("/g/999/", "Current")

        val first = repository.observeRecommendations(source, target, null).toList().last()
        val second = repository.observeRecommendations(source, target, null).toList().last()

        assertTrue(first.similarManga.isEmpty())
        assertTrue(second.similarManga.isEmpty())
        assertEquals(1, calls.get())
        assertEquals(0, source.requestCount.get())
    }

    @Test
    fun `source related 429 stops generic fallback for the current run`() = runTest {
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return NhentaiRelatedOutcome.RateLimited(120.seconds)
            }
        }
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, _ -> (1..12).map { sourceManga("fallback-$it", "Fallback $it") } },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = provider,
        )

        val result = repository.observeRecommendations(
            source = source,
            manga = sourceManga("/g/999/", "Current", genre = "Romance"),
            aniListId = null,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
        assertFalse(result.similarAuthoritative)
        assertTrue(result.retryAtMillis != null)
        assertEquals(0, source.requestCount.get())
    }

    @Test
    fun `concurrent related lookups stop at the first rate limit`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return NhentaiRelatedOutcome.RateLimited(30.seconds)
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { 1_000L },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()

        listOf("/g/1/", "/g/2/", "/g/3/").map { url ->
            async {
                repository.observeRecommendations(source, sourceManga(url, url), null).toList().last()
            }
        }.awaitAll()

        assertEquals(1, calls.get())
        assertEquals(0, source.requestCount.get())
    }

    @Test
    fun `same gallery uses its own related cache while another gallery loads independently`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(
                    (1..12).map { sourceManga("${target.cacheKey}-$it", "Related $it", genre = "romance") },
                )
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()

        val first = repository.observeRecommendations(source, sourceManga("/g/1/", "One"), null).toList().last()
        val again = repository.observeRecommendations(source, sourceManga("/g/1/", "One"), null).toList().last()
        val other = repository.observeRecommendations(source, sourceManga("/g/2/", "Two"), null).toList().last()

        assertEquals(2, calls.get())
        assertEquals(10, first.similarManga.size)
        assertTrue(first.similarManga.all { it.url.startsWith("42:/g/1/") })
        assertTrue(again.similarManga.all { it.url.startsWith("42:/g/1/") })
        val sharedUrls = first.similarManga
            .map(SManga::url)
            .toSet()
            .intersect(other.similarManga.map(SManga::url).toSet())
        assertTrue(sharedUrls.isEmpty())
    }

    @Test
    fun `host cooldown still serves the cached recommendations for their own gallery`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return if (target.cacheKey.endsWith("/g/1/")) {
                    NhentaiRelatedOutcome.Success(
                        (1..12).map { sourceManga("cached-$it", "Cached $it", genre = "romance") },
                    )
                } else {
                    NhentaiRelatedOutcome.RateLimited(30.seconds)
                }
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { 1_000L },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()

        val first = repository.observeRecommendations(source, sourceManga("/g/1/", "One"), null).toList().last()
        val limited = repository.observeRecommendations(source, sourceManga("/g/2/", "Two"), null).toList().last()
        val cached = repository.observeRecommendations(source, sourceManga("/g/1/", "One"), null).toList().last()

        assertEquals(10, first.similarManga.size)
        assertTrue(limited.similarManga.isEmpty())
        assertEquals(10, cached.similarManga.size)
        assertTrue(cached.similarManga.all { it.url.startsWith("cached-") })
        assertEquals(2, calls.get())
    }

    @Test
    fun `force refresh bypasses the fresh related cache`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                val generation = calls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(
                    (1..12).map {
                        sourceManga("generation-$generation-$it", "Generation $generation work $it", genre = "romance")
                    },
                )
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()
        val target = sourceManga("/g/1/", "One")

        val first = repository.observeRecommendations(source, target, null).toList().last()
        val cached = repository.observeRecommendations(source, target, null).toList().last()
        val refreshed = repository.observeRecommendations(
            source,
            target,
            null,
            forceRefresh = true,
        ).toList().last()

        assertTrue(first.similarManga.all { it.url.startsWith("generation-1-") })
        assertTrue(cached.similarManga.all { it.url.startsWith("generation-1-") })
        assertTrue(refreshed.similarManga.all { it.url.startsWith("generation-2-") })
        assertEquals(2, calls.get())
    }

    @Test
    fun `related lookup automatically becomes eligible after bounded cooldown`() = runTest {
        var now = 1_000L
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return if (calls.getAndIncrement() == 0) {
                    NhentaiRelatedOutcome.RateLimited(null)
                } else {
                    NhentaiRelatedOutcome.Success(
                        (1..12).map { sourceManga("recovered-$it", "Recovered $it", genre = "romance") },
                    )
                }
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()
        val target = sourceManga("/g/1/", "One")

        val limited = repository.observeRecommendations(source, target, null).toList().last()
        now += 60 * 1_000L - 1L
        val cooling = repository.observeRecommendations(source, target, null).toList().last()
        now += 1L
        val recovered = repository.observeRecommendations(source, target, null).toList().last()

        assertTrue(limited.similarManga.isEmpty())
        assertTrue(cooling.similarManga.isEmpty())
        assertEquals(2, calls.get())
        assertEquals(10, recovered.similarManga.size)
    }

    @Test
    fun `timed out related lookup receives a transient retry and recovers`() = runTest {
        var now = 1_000L
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return if (calls.getAndIncrement() == 0) {
                    delay(5_000L)
                    NhentaiRelatedOutcome.Success(emptyList())
                } else {
                    NhentaiRelatedOutcome.Success(
                        (1..12).map { sourceManga("recovered-$it", "Recovered $it", genre = "romance") },
                    )
                }
            }
        }
        val coordinator = RecommendationRequestCoordinator(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            randomUnit = { 0.0 },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            nhentaiRelatedProvider = provider,
            requestCoordinator = coordinator,
        )
        val source = FakeSource()
        val target = sourceManga("/g/1/", "One")

        val timedOut = repository.observeRecommendations(source, target, null).toList().last()
        assertTrue(timedOut.similarManga.isEmpty())
        assertEquals(now + 30_000L, timedOut.retryAtMillis)

        now += 30_000L
        val recovered = repository.observeRecommendations(source, target, null).toList().last()

        assertEquals(2, calls.get())
        assertEquals(10, recovered.similarManga.size)
    }

    @Test
    fun `capability cooldown is retried when tagged generic fallback stays empty`() = runTest {
        var now = 1_000L
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return if (calls.getAndIncrement() == 0) {
                    NhentaiRelatedOutcome.HttpFailure(
                        statusCode = 404,
                        classification = NhentaiHttpFailureClassification.CAPABILITY_UNAVAILABLE,
                    )
                } else {
                    NhentaiRelatedOutcome.Success(
                        (1..12).map { sourceManga("related-$it", "Related $it", genre = "romance") },
                    )
                }
            }
        }
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, _ -> emptyList() },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val target = sourceManga("/g/1/", "One", genre = "Romance")

        val unavailable = repository.observeRecommendations(source, target, null).toList().last()
        assertTrue(unavailable.similarManga.isEmpty())
        assertEquals(now + 6 * 60 * 60 * 1_000L, unavailable.retryAtMillis)

        now += 6 * 60 * 60 * 1_000L
        val recovered = repository.observeRecommendations(source, target, null).toList().last()

        assertEquals(2, calls.get())
        assertEquals(10, recovered.similarManga.size)
    }

    @Test
    fun `empty related response is retried after its target scoped negative cache`() = runTest {
        var now = 1_000L
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                return if (calls.getAndIncrement() == 0) {
                    NhentaiRelatedOutcome.Success(emptyList())
                } else {
                    NhentaiRelatedOutcome.Success(
                        (1..12).map { sourceManga("related-$it", "Related $it", genre = "romance") },
                    )
                }
            }
        }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { now },
            nhentaiRelatedProvider = provider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        )
        val source = FakeSource()
        val target = sourceManga("/g/1/", "One")

        val empty = repository.observeRecommendations(source, target, null).toList().last()
        assertTrue(empty.similarManga.isEmpty())
        assertEquals(now + 15 * 60 * 1_000L, empty.retryAtMillis)

        now += 15 * 60 * 1_000L
        val recovered = repository.observeRecommendations(source, target, null).toList().last()

        assertEquals(2, calls.get())
        assertEquals(10, recovered.similarManga.size)
    }

    @Test
    fun `rate sensitive queue timeout returns a retry instead of a permanent empty row`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(
                    (1..12).map {
                        sourceManga("${target.cacheKey}-$it", "Related $it", genre = "romance")
                    },
                )
            }
        }
        val coordinator = RecommendationRequestCoordinator(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            randomUnit = { 0.0 },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { 1_000L + testScheduler.currentTime },
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            nhentaiRelatedProvider = provider,
            requestCoordinator = coordinator,
        )
        val source = FakeSource()

        val results = (1..12).map { galleryId ->
            async {
                repository.observeRecommendations(
                    source,
                    sourceManga("/g/$galleryId/", "Gallery $galleryId"),
                    null,
                ).toList().last()
            }
        }.awaitAll()
        val timedOut = results.filter { it.similarManga.isEmpty() }

        assertTrue(timedOut.isNotEmpty())
        assertTrue(timedOut.all { it.retryAtMillis != null })
        assertTrue(calls.get() < results.size)
    }

    @Test
    fun `partial rate sensitive result still receives a coordinated retry after timeout`() = runTest {
        val calls = AtomicInteger()
        val provider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
                calls.incrementAndGet()
                return NhentaiRelatedOutcome.Success(
                    (1..5).map { sourceManga("related-$it", "Related $it", genre = "Romance") },
                )
            }
        }
        val coordinator = RecommendationRequestCoordinator(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            randomUnit = { 0.0 },
        )
        val source = FakeSource(
            requestDelayMillis = 20_000L,
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, _ -> emptyList() },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            now = { 1_000L + testScheduler.currentTime },
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            nhentaiRelatedProvider = provider,
            requestCoordinator = coordinator,
        )

        val timedOut = repository.observeRecommendations(
            source,
            sourceManga("/g/1/", "Current", genre = "Romance"),
            null,
        ).toList().last()

        assertEquals(5, timedOut.similarManga.size)
        assertEquals(36_000L, timedOut.retryAtMillis)
        assertEquals(1, calls.get())
        assertEquals(1, source.requestCount.get())
    }

    @Test
    fun `nHentai creator text filters verify returned metadata without details`() = runTest {
        val routedStates = mutableListOf<Map<String, String>>()
        val bareResults = listOf(sourceManga("current", "Current")) +
            (1..11).map { sourceManga("work-$it", "Work $it", author = "Circle Name") }
        val relatedProvider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome =
                NhentaiRelatedOutcome.Success(emptyList())
        }
        val source = FakeSource(
            filterFactory = {
                FilterList(
                    TestTextFilter("Tags"),
                    TestTextFilter("Groups"),
                    TestTextFilter("Artists"),
                    TestTextFilter("Uploader"),
                    TestTextFilter("Pages"),
                )
            },
            searchHandler = { _, filters ->
                val states = filters.filterIsInstance<Filter.Text>().associate { it.name to it.state }
                synchronized(routedStates) { routedStates += states }
                bareResults
            },
        )

        val result = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = relatedProvider,
        ).observeRecommendations(
            source = source,
            manga = sourceManga(
                url = "current",
                title = "Current",
                author = "Circle: Circle Name",
                artist = "Artist Name",
            ),
            aniListId = null,
        ).toList().last()

        assertEquals(10, result.creatorWorks.size)
        assertFalse(result.creatorWorks.any { it.url == "current" })
        assertEquals(listOf("", ""), source.searchQueries.sorted())
        assertTrue(routedStates.any { it["Groups"] == "Circle Name" && it["Artists"].isNullOrEmpty() })
        assertTrue(routedStates.any { it["Artists"] == "Artist Name" && it["Groups"].isNullOrEmpty() })
        routedStates.forEach { states ->
            assertEquals("", states["Tags"])
            assertEquals("", states["Uploader"])
            assertEquals("", states["Pages"])
        }
        assertEquals(0, source.detailRequests.get())
        assertEquals(0, source.popularRequests.get())
        assertEquals(2, source.requestCount.get())
    }

    @Test
    fun `nHentai creator text filters never trust bare results`() = runTest {
        val relatedProvider = object : NhentaiRelatedProvider {
            override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget =
                TestRelatedTarget(source.id, "nhentai.net", "${source.id}:${manga.url}")

            override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome =
                NhentaiRelatedOutcome.Success(emptyList())
        }
        val source = FakeSource(
            filterFactory = { FilterList(TestTextFilter("Artists")) },
            searchHandler = { _, _ ->
                (1..12).map { sourceManga("bare-$it", "Bare $it") }
            },
        )

        val result = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            nhentaiRelatedProvider = relatedProvider,
            requestCoordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 }),
        ).observeRecommendations(
            source = source,
            manga = sourceManga("/g/999/", "Current", artist = "Artist Name"),
            aniListId = null,
        ).toList().last()

        assertTrue(result.creatorWorks.isEmpty())
        assertTrue(source.detailRequests.get() > 0)
        assertTrue(source.requestCount.get() <= 4)
    }

    @Test
    fun `genre routes are requested fresh for every manga`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("懸疑")) },
            searchHandler = { query, filters ->
                if (query.isEmpty() && filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..12).map { sourceManga("mystery-$it", "Mystery $it") }
                } else {
                    emptyList()
                }
            },
        )
        val repository = repository()

        val first = repository.observeRecommendations(
            source,
            sourceManga("first", "First", genre = "悬疑"),
            aniListId = null,
        ).toList().last()
        val requestsAfterFirst = source.requestCount.get()
        val second = repository.observeRecommendations(
            source,
            sourceManga("second", "Second", genre = "懸疑"),
            aniListId = null,
        ).toList().last()

        assertEquals(1, requestsAfterFirst)
        assertEquals(requestsAfterFirst + 1, source.requestCount.get())
        assertEquals(10, second.similarManga.size)
        assertFalse(
            first.similarManga.take(4).map(SManga::url) == second.similarManga.take(4).map(SManga::url),
        )

        val firstAgain = repository.observeRecommendations(
            source,
            sourceManga("first", "First", genre = "悬疑"),
            aniListId = null,
        ).toList().last()
        assertEquals(10, firstAgain.similarManga.size)
        assertEquals(requestsAfterFirst + 2, source.requestCount.get())
    }

    @Test
    fun `ordinary source search pages are diversified by target manga`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..24).map { sourceManga("result-$it", "Result $it") }
                } else {
                    emptyList()
                }
            },
        )
        val repository = repository()

        (1..8).forEach { index ->
            repository.observeRecommendations(
                source,
                sourceManga("target-$index", "Target $index", genre = "Romance"),
                aniListId = null,
            ).toList()
        }

        assertEquals(setOf(1, 2, 3, 4), source.searchPages.toSet())
    }

    @Test
    fun `absolute and relative source urls share one identity and cannot recommend the current manga`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("恋愛")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    listOf(
                        sourceManga("https://source.example/title/current/", "Current duplicate"),
                        sourceManga("/title/match/", "Match relative"),
                        sourceManga("https://source.example/title/match", "Match absolute duplicate"),
                    )
                } else {
                    emptyList()
                }
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("/title/current", "Current", genre = "恋愛"),
            aniListId = null,
        ).toList().last()

        assertEquals(1, result.similarManga.size)
        assertEquals("/title/match/", result.similarManga.single().url)
    }

    @Test
    fun `quality random sampling has no permanently anchored cards`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..12).map { sourceManga("rank-$it", "Rank $it") }
                } else {
                    emptyList()
                }
            },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            random = java.util.Random(1_234L),
        )
        val target = sourceManga("current", "Current", genre = "Romance")

        val first = repository.observeRecommendations(source, target, null).toList().last().similarManga
        repository.recordShown(source.id, target, first)
        val second = repository.observeRecommendations(source, target, null).toList().last().similarManga

        assertFalse(first.take(2).map(SManga::url) == listOf("rank-1", "rank-2"))
        assertFalse(second.take(2).map(SManga::url) == listOf("rank-1", "rank-2"))
        assertFalse(first.map(SManga::url) == second.map(SManga::url))
    }

    @Test
    fun `twenty fresh quality candidates produce disjoint consecutive displays`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..24).map { sourceManga("work-$it", "Work $it") }
                } else {
                    emptyList()
                }
            },
        )
        val repository = repository()
        val target = sourceManga("current", "Current", genre = "Romance")

        val first = repository.observeRecommendations(source, target, null).toList().last().similarManga
        repository.recordShown(source.id, target, first)
        val second = repository.observeRecommendations(source, target, null).toList().last().similarManga

        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertTrue(first.map(SManga::url).toSet().intersect(second.map(SManga::url).toSet()).isEmpty())
    }

    @Test
    fun `different targets refill oldest source history instead of shrinking the row`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..24).map { sourceManga("global-$it", "Global $it") }
                } else {
                    emptyList()
                }
            },
        )
        val repository = repository()
        val shown = linkedSetOf<String>()

        repeat(3) { index ->
            val target = sourceManga("target-$index", "Target $index", genre = "Romance")
            val result = repository.observeRecommendations(source, target, null)
                .toList().last().similarManga
            assertEquals(10, result.size)
            val repeated = result.count { it.url in shown }
            if (index < 2) assertEquals(0, repeated) else assertEquals(6, repeated)
            shown += result.map(SManga::url)
            repository.recordShown(source.id, target, result)
        }
    }

    @Test
    fun `configured recommendation keywords filter every source result`() = runTest {
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, filters ->
                if (!filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    emptyList()
                } else {
                    buildList {
                        (1..8).forEach { add(sourceManga("ai-$it", "AI generated $it")) }
                        (1..16).forEach { add(sourceManga("safe-$it", "Safe $it")) }
                    }
                }
            },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            recommendationFilterProvider = { "AI, AI生成, 3D" },
        )

        val result = repository.observeRecommendations(
            source,
            sourceManga("current", "Current", genre = "Romance"),
            null,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.all { it.url.startsWith("safe-") })
    }

    @Test
    fun `ehentai uses target specific exact multi tag queries instead of one broad category`() = runTest {
        val source = FakeEhentaiSource()
        val target = sourceManga("current", "Current", genre = "doujinshi").apply {
            description = """
                Tags:
                ・ language: <japanese>
                ・ parody: <original>
                ・ female: <big breasts> <gyaru>
                ・ character: <heroine>
            """.trimIndent()
        }

        val result = repository().observeRecommendations(source, target, null).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(source.searchQueries.isNotEmpty())
        assertTrue(source.searchQueries.all { query -> query.count { it == '$' } >= 2 })
        assertTrue(source.searchQueries.none { it.contains("language:", ignoreCase = true) })
        assertTrue(source.searchQueries.none { it.equals("Cosplay", ignoreCase = true) })
    }

    @Test
    fun `zero usable tags never use the same popular pool for every target`() = runTest {
        val popular = (1..12).map { sourceManga("popular-$it", "Popular $it") }
        val source = FakeSource(popular = popular)

        val result = repository().observeRecommendations(
            source,
            sourceManga("current", "Current", genre = "language:japanese"),
            null,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
        assertEquals(0, source.popularRequests.get())
    }

    @Test
    fun `quality threshold never admits a broad popular-only pool`() = runTest {
        val source = FakeSource(
            popular = (1..12).map { sourceManga("popular-$it", "Popular $it", genre = "romance") },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "romance"),
            aniListId = null,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
        assertEquals(1, source.popularRequests.get())
    }

    @Test
    fun `failed source routes never render cards from the previous local index`() = runTest {
        val local = buildList {
            (1..10).forEach { add(domainManga("local-match-$it", "Match $it", genre = "Mystery")) }
            (1..10).forEach { add(domainManga("local-noise-$it", "Noise $it", genre = "Romance")) }
        }
        val source = FakeSource(searchHandler = { _, _ -> emptyList() })

        val result = repository(local = local).observeRecommendations(
            source,
            sourceManga("current", "Current", genre = "Mystery"),
            null,
        ).toList().last()

        assertTrue(result.similarManga.isEmpty())
    }

    @Test
    fun `semantic ancestor identity excludes an alias url without title-only false positives`() = runTest {
        val parent = sourceManga("parent", "Shared title", author = "Alice", genre = "Romance")
        val parentIdentity = RecommendationMetadata.identity(42, parent)
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("Romance")) },
            searchHandler = { _, _ ->
                listOf(
                    sourceManga("parent-alias", "Shared title", author = "Alice"),
                    sourceManga("same-title-other-author", "Shared title", author = "Bob"),
                )
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("child", "Child", genre = "Romance"),
            aniListId = null,
            sessionExcludedUrls = setOf(parent.url),
            sessionExcludedWorkKeys = parentIdentity.exposureKeys,
        ).toList().last()

        assertEquals(listOf("same-title-other-author"), result.similarManga.map(SManga::url))
    }

    @Test
    fun `session exclusions apply to fresh cards and old cards are never reused`() = runTest {
        var fresh = (1..12).map { sourceManga("fresh-$it", "Fresh $it", genre = "Mystery") }
        val source = FakeSource(
            searchHandler = { query, _ -> if (query == "Mystery") fresh else emptyList() },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ ->
                (1..12).map { domainManga("stale-$it", "Stale $it", genre = "Mystery") }
            },
            aniListLoader = { emptyList() },
        )
        val current = sourceManga("current", "Current", genre = "Mystery")
        val excluded = setOf("https://example.org/fresh-1/", "/fresh-2/")
        val excludedRawUrls = setOf("fresh-1", "fresh-2")

        val first = repository.observeRecommendations(
            source = source,
            manga = current,
            aniListId = null,
            sessionExcludedUrls = excluded,
        ).toList().last()

        assertEquals(10, first.similarManga.size)
        assertTrue(first.similarManga.none { it.url in excludedRawUrls })
        assertTrue(first.similarManga.all { it.url.startsWith("fresh-") })
        val requestsAfterFirst = source.requestCount.get()

        fresh = (1..12).map { sourceManga("new-$it", "New $it", genre = "Mystery") }
        val second = repository.observeRecommendations(
            source = source,
            manga = current,
            aniListId = null,
            sessionExcludedUrls = excluded,
        ).toList().last()

        assertEquals(10, second.similarManga.size)
        assertTrue(second.similarManga.all { it.url.startsWith("new-") })
        assertTrue(source.requestCount.get() > requestsAfterFirst)
    }

    @Test
    fun `detail metadata cache never supplies cards for the next manga or another source`() = runTest {
        val candidates = (1..12).map { sourceManga("candidate-$it", "Candidate $it") }
        val source = FakeSource(
            id = 42,
            searchResults = mapOf("悬疑" to candidates),
            popular = candidates,
            details = candidates.associate { item ->
                item.url to SManga.create().apply { genre = "懸疑" }
            },
            filterFactory = { FilterList() },
        )
        val repository = repository()

        val first = repository.observeRecommendations(
            source = source,
            manga = sourceManga("first", "First", genre = "悬疑"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()
        assertEquals(10, first.similarManga.size)
        val requestsAfterFirst = source.requestCount.get()

        val emissions = repository.observeRecommendations(
            source = source,
            manga = sourceManga("second", "Second", genre = "懸疑"),
            aniListId = null,
        ).toList()
        assertTrue(emissions.first().similarManga.isEmpty())
        assertEquals(10, emissions.last().similarManga.size)
        assertTrue(source.requestCount.get() > requestsAfterFirst)

        val otherSource = FakeSource(id = 43)
        val isolated = repository.observeRecommendations(
            source = otherSource,
            manga = sourceManga("third", "Third", genre = "懸疑"),
            aniListId = null,
        ).toList().last()
        assertTrue(isolated.similarManga.isEmpty())
    }

    @Test
    fun `unsupported rare tags do not prevent a later supported genre route`() = runTest {
        val local = listOf(
            domainManga("local-1", "Local 1", genre = "懸疑"),
            domainManga("local-2", "Local 2", genre = "懸疑"),
        )
        val source = FakeSource(
            filterFactory = { FilterList(TestGenreCheckBox("懸疑")) },
            searchHandler = { _, filters ->
                if (filters.filterIsInstance<Filter.CheckBox>().single().state) {
                    (1..12).map { sourceManga("supported-$it", "Supported $it") }
                } else {
                    emptyList()
                }
            },
        )

        val result = repository(local = local).observeRecommendations(
            source,
            sourceManga("current", "Current", genre = "Unsupported A, Unsupported B, 悬疑"),
            aniListId = null,
        ).toList().last()

        assertTrue(result.similarManga.any { it.url.startsWith("supported-") })
        assertTrue(
            source.requestCount.get() <= 2,
            "expected one supported genre route and at most one popular backfill request",
        )
    }

    @Test
    fun `ordinary source verifies ten details in progressive batches within source limits`() = runTest {
        val candidates = (1..12).map { sourceManga("candidate-$it", "Candidate $it") }
        val source = FakeSource(
            searchResults = mapOf("懸疑" to candidates),
            popular = candidates,
            details = candidates.associate { item ->
                item.url to SManga.create().apply { genre = "懸疑" }
            },
            filterFactory = { FilterList() },
            requestDelayMillis = 25,
        )

        val emissions = repository().observeRecommendations(
            source,
            sourceManga("ordinary", "Ordinary", genre = "日本, 懸疑"),
            aniListId = null,
            forceRefresh = true,
        ).toList()

        val progressiveSizes = emissions.map { it.similarManga.size }.filter { it > 0 }.distinct()
        assertTrue(listOf(2, 4, 6, 8, 10).all { it in progressiveSizes }, "sizes were $progressiveSizes")
        assertEquals(10, emissions.last().similarManga.size)
        assertEquals(10, source.detailRequests.get())
        assertTrue(source.requestCount.get() <= 12)
        assertTrue(source.maxConcurrentRequests.get() <= 2)
    }

    @Test
    fun `tag query candidates receive the detail budget before generic popular candidates`() = runTest {
        val queried = (1..12).map { sourceManga("query-$it", "Query $it") }
        val popular = (1..12).map { sourceManga("popular-$it", "Popular $it") }
        val source = FakeSource(
            searchResults = mapOf("悬疑" to queried),
            popular = popular,
            details = (queried + popular).associate { item ->
                item.url to SManga.create().apply { genre = "懸疑" }
            },
            filterFactory = { FilterList() },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", genre = "悬疑"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.all { it.url.startsWith("query-") })
        assertEquals(10, source.detailRequests.get())
        assertTrue(source.requestCount.get() <= 12)
    }

    @Test
    fun `ordinary similar fallback is not starved by unverifiable creator searches`() = runTest {
        val similar = (1..12).map { sourceManga("similar-$it", "Similar $it") }
        val creator = (1..12).map { sourceManga("creator-$it", "Creator $it") }
        val source = FakeSource(
            searchResults = mapOf(
                "悬疑" to similar,
                "Alice" to creator,
            ),
            popular = similar,
            details = buildMap {
                similar.forEach { item -> put(item.url, SManga.create().apply { genre = "懸疑" }) }
                creator.forEach { item -> put(item.url, SManga.create().apply { author = "Alice" }) }
            },
            filterFactory = { FilterList() },
        )
        val requests = mutableListOf<String>()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            onSourceRequest = { synchronized(requests) { requests += it } },
        )

        val result = repository.observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice", genre = "悬疑"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(10, result.similarManga.size)
        assertTrue(result.similarManga.all { it.url.startsWith("similar-") })
        assertTrue(requests.size <= 12, "source requests were $requests")
        assertTrue(source.maxConcurrentRequests.get() <= 2)
    }

    @Test
    fun `a failed secondary creator search keeps verified partial results`() = runTest {
        val source = FakeSource(
            searchHandler = { query, _ ->
                when (query) {
                    "Alice" -> listOf(sourceManga("alice-work", "Alice Work", author = "Alice"))
                    else -> throw IOException("secondary creator unavailable")
                }
            },
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice; Bob"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(listOf("alice-work"), result.creatorWorks.map(SManga::url))
        assertFalse(result.creatorAuthoritative)
    }

    @Test
    fun `creator detail hydration uses both source concurrency slots`() = runTest {
        val unresolved = (1..2).map { sourceManga("work-$it", "Work $it") }
        val source = FakeSource(
            searchResults = mapOf("Alice" to unresolved),
            details = unresolved.associate { candidate ->
                candidate.url to SManga.create().apply { author = "Alice" }
            },
            requestDelayMillis = 25,
        )

        val result = repository().observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(2, result.creatorWorks.size)
        assertEquals(2, source.maxConcurrentRequests.get())
    }

    @Test
    fun `failed fresh refresh clears cards instead of retaining old results`() = runTest {
        var failSearch = false
        val cached = sourceManga("cached-work", "Cached Work", author = "Alice")
        val source = FakeSource(
            searchHandler = { _, _ ->
                if (failSearch) throw IOException("offline")
                listOf(cached)
            },
        )
        val repository = repository()
        val current = sourceManga("current", "Current", author = "Alice")

        val successful = repository.observeRecommendations(
            source,
            current,
            aniListId = null,
            forceRefresh = true,
        ).toList().last()
        assertEquals(listOf("cached-work"), successful.creatorWorks.map(SManga::url))
        assertTrue(successful.creatorAuthoritative)

        failSearch = true
        val failedRefresh = repository.observeRecommendations(
            source,
            current,
            aniListId = null,
            forceRefresh = true,
        ).toList()

        assertTrue(failedRefresh.all { !it.creatorAuthoritative })
        assertTrue(failedRefresh.last().creatorWorks.isEmpty())
    }

    @Test
    fun `an empty fresh response does not suppress the next current-source lookup`() = runTest {
        var searchResults = listOf(sourceManga("old-work", "Old Work", author = "Alice"))
        val source = FakeSource(searchHandler = { _, _ -> searchResults })
        val repository = repository()
        val current = sourceManga("current", "Current", author = "Alice")

        repository.observeRecommendations(source, current, null, forceRefresh = true).toList()
        searchResults = emptyList()
        val emptyRefresh = repository.observeRecommendations(
            source,
            current,
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertTrue(emptyRefresh.creatorWorks.isEmpty())
        assertTrue(emptyRefresh.creatorAuthoritative)

        searchResults = listOf(sourceManga("new-work", "New Work", author = "Alice"))
        val requestsBeforeNextLookup = source.requestCount.get()
        val freshResult = repository.observeRecommendations(
            source,
            current,
            aniListId = null,
            forceRefresh = false,
        ).toList().last()

        assertEquals(listOf("new-work"), freshResult.creatorWorks.map(SManga::url))
        assertTrue(freshResult.creatorAuthoritative)
        assertTrue(source.requestCount.get() > requestsBeforeNextLookup)
    }

    @Test
    fun `cancelling observation cancels an in flight slow source request`() = runTest {
        val source = FakeSource(requestDelayMillis = 60_000)
        val repository = repository()
        val job = backgroundScope.launch {
            repository.observeRecommendations(
                source = source,
                manga = sourceManga("current", "Current", author = "Alice"),
                aniListId = null,
                forceRefresh = true,
            ).collect()
        }

        runCurrent()
        assertTrue(source.activeRequests.get() > 0)

        job.cancelAndJoin()
        runCurrent()

        assertEquals(0, source.activeRequests.get())
        assertTrue(source.cancelledRequests.get() > 0)
    }

    @Test
    fun `source calls stay within hard budget and concurrency two`() = runTest {
        val search = (1..12).map { sourceManga("candidate-$it", "Candidate $it") }
        val source = FakeSource(
            searchResults = buildMap {
                put("Alice", search)
                put("Bob", search)
                (1..4).forEach { put("External $it", listOf(sourceManga("external-$it", "External $it"))) }
            },
            popular = search,
            requestDelayMillis = 25,
        )
        val requests = mutableListOf<String>()
        val recommendations = (1..4).map { aniListRecommendation(it.toLong(), "External $it") }
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { recommendations },
            onSourceRequest = { synchronized(requests) { requests += it } },
        )

        repository.observeRecommendations(
            source = source,
            manga = sourceManga(
                url = "current",
                title = "Current",
                author = "Alice; Bob",
                genre = "Fantasy",
            ),
            aniListId = 1,
            forceRefresh = true,
        ).toList()

        assertTrue(requests.size <= 12)
        assertTrue(source.maxConcurrentRequests.get() <= 2)
    }

    @Test
    fun `separate page observations share the same source concurrency gate`() = runTest {
        val source = FakeSource(requestDelayMillis = 25)
        val coordinator = RecommendationRequestCoordinator()
        val firstRepository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            requestCoordinator = coordinator,
        )
        val secondRepository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            requestCoordinator = coordinator,
        )

        listOf(
            async {
                firstRepository.observeRecommendations(
                    source = source,
                    manga = sourceManga("first", "First", author = "Alice; Bob"),
                    aniListId = null,
                    forceRefresh = true,
                ).toList()
            },
            async {
                secondRepository.observeRecommendations(
                    source = source,
                    manga = sourceManga("second", "Second", author = "Carol; Dave"),
                    aniListId = null,
                    forceRefresh = true,
                ).toList()
            },
        ).awaitAll()

        assertEquals(2, source.maxConcurrentRequests.get())
    }

    @Test
    fun `soft deadline prevents new source requests after four seconds`() = runTest {
        var monotonicNanos = 0L
        val startedRequests = mutableListOf<String>()
        val unresolved = (1..4).map { sourceManga("candidate-$it", "Candidate $it") }
        val source = FakeSource(
            searchResults = mapOf("Alice" to unresolved),
            details = unresolved.associate { item ->
                item.url to SManga.create().apply { author = "Alice" }
            },
        )
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
            monotonicNowNanos = { monotonicNanos },
            onSourceRequest = { request ->
                startedRequests += request
                monotonicNanos = 5_000_000_000L
            },
        )

        val result = repository.observeRecommendations(
            source = source,
            manga = sourceManga("current", "Current", author = "Alice"),
            aniListId = null,
            forceRefresh = true,
        ).toList().last()

        assertEquals(listOf("creator-search"), startedRequests)
        assertEquals(0, source.detailRequests.get())
        assertFalse(result.creatorAuthoritative)
    }

    @Test
    fun `empty results are queried fresh on every visit`() = runTest {
        val source = FakeSource()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> emptyList() },
            aniListLoader = { emptyList() },
        )
        val current = sourceManga("current", "Current", author = "Alice", genre = "Romance")

        repository.observeRecommendations(source, current, null, forceRefresh = false).toList()
        val firstCount = source.requestCount.get()
        repository.observeRecommendations(source, current, null, forceRefresh = false).toList()
        assertTrue(source.requestCount.get() > firstCount)

        repository.observeRecommendations(source, current, null, forceRefresh = true).toList()
        assertTrue(source.requestCount.get() > firstCount)
    }

    @Test
    fun `local metadata cannot satisfy a creator row`() = runTest {
        val source = FakeSource()
        val repository = MangaRecommendationRepository(
            localCandidateLoader = { _, _ ->
                listOf(domainManga("alice-work", "Alice Work", author = "Alice"))
            },
            aniListLoader = { emptyList() },
        )
        val current = sourceManga("current", "Current", author = "Alice")

        repository.observeRecommendations(source, current, null, forceRefresh = false).toList()
        val initialCount = source.requestCount.get()

        val result = repository.observeRecommendations(source, current, null, forceRefresh = false).toList().last()
        assertTrue(result.creatorWorks.isEmpty())
        assertTrue(source.requestCount.get() > initialCount)
    }

    private fun repository(
        local: List<Manga> = emptyList(),
        aniList: List<ALRecommendation> = emptyList(),
    ): MangaRecommendationRepository {
        return MangaRecommendationRepository(
            localCandidateLoader = { _, _ -> local },
            aniListLoader = { aniList },
        )
    }

    private inner class FakeEhentaiSource : HttpSource() {
        override val name: String = "E-Hentai"
        override val lang: String = "all"
        override val baseUrl: String = "https://e-hentai.org"
        override val supportsLatest: Boolean = false
        val searchQueries = mutableListOf<String>()

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            searchQueries += query
            return MangasPage(
                (1..24).map {
                    sourceManga("eh-$query-$it", "EH $it", genre = null).apply {
                        description = """
                            Tags:
                            ・ female: <big breasts> <gyaru>
                            ・ character: <heroine>
                        """.trimIndent()
                    }
                },
                false,
            )
        }

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = SMangaUpdate(manga, emptyList())
    }

    private class FakeSource(
        override val id: Long = 42,
        override val name: String = "Fake",
        private val searchResults: Map<String, List<SManga>> = emptyMap(),
        private val popular: List<SManga> = emptyList(),
        private val details: Map<String, SManga> = emptyMap(),
        private val requestDelayMillis: Long = 0,
        private val filterFactory: () -> FilterList = { FilterList() },
        private val searchHandler: ((String, FilterList) -> List<SManga>)? = null,
    ) : Source {
        override val supportsLatest: Boolean = false
        val requestCount = AtomicInteger()
        val filterListCalls = AtomicInteger()
        val detailRequests = AtomicInteger()
        val popularRequests = AtomicInteger()
        val latestRequests = AtomicInteger()
        val searchPages = mutableListOf<Int>()
        val activeRequests = AtomicInteger()
        val maxConcurrentRequests = AtomicInteger()
        val cancelledRequests = AtomicInteger()
        val searchQueries = mutableListOf<String>()
        val searchFilters = mutableListOf<FilterList>()
        val searchFilterInitialStates = mutableListOf<String>()

        override fun getFilterList(): FilterList {
            filterListCalls.incrementAndGet()
            return filterFactory()
        }

        override suspend fun getPopularManga(page: Int): MangasPage = trackedRequest {
            popularRequests.incrementAndGet()
            MangasPage(popular, false)
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage = trackedRequest {
            latestRequests.incrementAndGet()
            MangasPage(emptyList(), false)
        }

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = trackedRequest {
            val manga = synchronized(searchQueries) {
                searchQueries += query
                searchPages += page
                searchFilters += filters
                searchFilterInitialStates += filters.filterIsInstance<Filter.Text>().joinToString { it.state }
                val result = searchHandler?.invoke(query, filters) ?: searchResults[query].orEmpty()
                filters.filterIsInstance<Filter.Text>().forEach { it.state = "mutated" }
                result
            }
            MangasPage(manga, false)
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = trackedRequest {
            detailRequests.incrementAndGet()
            SMangaUpdate(details[manga.url] ?: manga, emptyList())
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        private suspend fun <T> trackedRequest(block: () -> T): T {
            requestCount.incrementAndGet()
            val active = activeRequests.incrementAndGet()
            maxConcurrentRequests.updateAndGet { maxOf(it, active) }
            try {
                delay(requestDelayMillis)
                return block()
            } catch (e: CancellationException) {
                cancelledRequests.incrementAndGet()
                throw e
            } finally {
                activeRequests.decrementAndGet()
            }
        }
    }

    private class MutableTextFilter : Filter.Text("marker")

    private data class TestRelatedTarget(
        override val sourceId: Long,
        override val hostKey: String,
        override val cacheKey: String,
    ) : NhentaiRelatedTarget

    private class TestTextFilter(name: String) : Filter.Text(name)

    private class TestGenreCheckBox(name: String) : Filter.CheckBox(name)

    private class TestGenreSelect(name: String, values: Array<String>) : Filter.Select<String>(name, values)

    private class TestSortFilter(name: String, values: Array<String>) : Filter.Sort(name, values)

    @Suppress("OVERRIDE_DEPRECATION")
    private class LegacyCatalogueSource(
        private val searchResult: SManga,
        private val details: SManga,
    ) : CatalogueSource {
        override val id: Long = 42
        override val name: String = "Legacy"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        val searchCalls = AtomicInteger()
        val detailCalls = AtomicInteger()

        @Suppress("DEPRECATION")
        override fun fetchPopularManga(page: Int): Observable<MangasPage> =
            Observable.just(MangasPage(emptyList(), false))

        @Suppress("DEPRECATION")
        override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
            Observable.just(MangasPage(emptyList(), false))

        @Suppress("DEPRECATION")
        override fun fetchSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): Observable<MangasPage> {
            searchCalls.incrementAndGet()
            return Observable.just(MangasPage(listOf(searchResult), false))
        }

        @Suppress("DEPRECATION")
        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            detailCalls.incrementAndGet()
            return Observable.just(details)
        }
    }

    private fun sourceManga(
        url: String,
        title: String,
        author: String? = null,
        artist: String? = null,
        description: String? = null,
        genre: String? = null,
    ): SManga {
        return SManga.create().apply {
            this.url = url
            this.title = title
            this.author = author
            this.artist = artist
            this.description = description
            this.genre = genre
            thumbnail_url = "https://example.org/$url.jpg"
            initialized = true
        }
    }

    private fun domainManga(
        url: String,
        title: String,
        author: String? = null,
        description: String? = null,
        genre: String? = null,
    ): Manga {
        return Manga.create().copy(
            source = 42,
            url = url,
            title = title,
            author = author,
            description = description,
            genre = genre?.let(::listOf),
            initialized = true,
        )
    }

    private fun aniListRecommendation(
        id: Long,
        title: String,
        genres: List<String> = emptyList(),
    ): ALRecommendation {
        return ALRecommendation(
            rating = 10,
            media = ALRecommendationMedia(
                id = id,
                type = "MANGA",
                title = ALRecommendationTitle(userPreferred = title),
                genres = genres,
            ),
        )
    }
}
