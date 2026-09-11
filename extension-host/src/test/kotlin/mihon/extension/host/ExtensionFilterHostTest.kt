package mihon.extension.host

import eu.kanade.tachiyomi.source.CatalogueSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.encodeFilterList
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.source.model.Filter as TFilter
import eu.kanade.tachiyomi.source.model.FilterList as TFilterList
import eu.kanade.tachiyomi.source.model.MangasPage as TMangasPage
import eu.kanade.tachiyomi.source.model.Page as TPage
import eu.kanade.tachiyomi.source.model.SChapter as TSChapter
import eu.kanade.tachiyomi.source.model.SManga as TSManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate as TSMangaUpdate

class ExtensionFilterHostTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class CustomCheckBox(name: String, state: Boolean) : Filter.CheckBox(name, state)

    private class FilterCapturingSource : WindowsCatalogueSource {
        override val id: Long = 5005L
        override val name: String = "Filter Capturing Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var receivedPage: Int? = null
        var receivedQuery: String? = null
        var receivedFilters: FilterList? = null

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
            receivedPage = page
            receivedQuery = query
            receivedFilters = filters
            return MangasPage(
                mangas = listOf(SManga(url = "/manga/$page", title = "Result for $query")),
                hasNextPage = false,
            )
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        override fun getFilterList(): FilterList = FilterList(
            Filter.Header("Header"),
            Filter.Separator("Separator"),
            Filter.Text("Author", "Oda"),
            CustomCheckBox("Completed", true),
            Filter.Select("Status", arrayOf("Any", "Ongoing", "Completed"), 2),
            Filter.TriState("Genre", Filter.TriState.STATE_INCLUDE),
            Filter.Group(
                "Group",
                listOf(
                    Filter.CheckBox("Action", true),
                    Filter.TriState("Romance", Filter.TriState.STATE_EXCLUDE),
                ),
            ),
            Filter.Sort("Sort", arrayOf("Title", "Date"), Filter.Sort.Selection(1, true)),
        )
    }

    @Test
    fun `get filter list returns the full serialized definition and state`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        engine.registerSource(FilterCapturingSource())

        val response = engine.handleRequest(
            IpcRequest(1, IpcCommands.GET_FILTER_LIST, json.encodeToString(SourcePayload(5005L))),
        )

        response.success shouldBe true
        val filters = decodeFilterList(response.payloadJson)
        filters.filters.size shouldBe 8

        filters.filters[2].shouldBeInstanceOf<Filter.Text>().state shouldBe "Oda"
        filters.filters[3].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
        filters.filters[4].shouldBeInstanceOf<Filter.Select<*>>().state shouldBe 2
        filters.filters[5].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_INCLUDE

        val group = filters.filters[6].shouldBeInstanceOf<Filter.Group<*>>()
        group.state[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
        group.state[1].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_EXCLUDE

        filters.filters[7].shouldBeInstanceOf<Filter.Sort>().state shouldBe Filter.Sort.Selection(1, true)
    }

    @Test
    fun `search manga receives decoded filters`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val source = FilterCapturingSource()
        engine.registerSource(source)

        val selectedFilters = FilterList(
            Filter.Text("Author", "Oda"),
            Filter.CheckBox("Completed", true),
            Filter.Select("Status", arrayOf("Any", "Ongoing", "Completed"), 1),
            Filter.TriState("Genre", Filter.TriState.STATE_EXCLUDE),
            Filter.Group(
                "Group",
                listOf(
                    Filter.CheckBox("Action", false),
                    Filter.TriState("Romance", Filter.TriState.STATE_INCLUDE),
                ),
            ),
            Filter.Sort("Sort", arrayOf("Title", "Date"), Filter.Sort.Selection(0, true)),
        )

        val response = engine.handleRequest(
            IpcRequest(
                requestId = 1,
                command = IpcCommands.SEARCH_MANGA,
                payloadJson = json.encodeToString(
                    SearchPayload(
                        sourceId = 5005L,
                        page = 2,
                        query = "one piece",
                        filtersJson = encodeFilterList(selectedFilters),
                    ),
                ),
            ),
        )

        response.success shouldBe true
        source.receivedPage shouldBe 2
        source.receivedQuery shouldBe "one piece"

        val received = source.receivedFilters!!
        received.filters.size shouldBe 8
        received.filters[2].shouldBeInstanceOf<Filter.Text>().state shouldBe "Oda"
        received.filters[3].shouldBeInstanceOf<CustomCheckBox>().state shouldBe true
        received.filters[4].shouldBeInstanceOf<Filter.Select<*>>().state shouldBe 1
        received.filters[5].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_EXCLUDE

        val group = received.filters[6].shouldBeInstanceOf<Filter.Group<*>>()
        group.state[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe false
        group.state[1].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_INCLUDE

        received.filters[7].shouldBeInstanceOf<Filter.Sort>().state shouldBe Filter.Sort.Selection(0, true)
    }

    @Test
    fun `search manga without filters keeps backward compatibility`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val source = FilterCapturingSource()
        engine.registerSource(source)

        val response = engine.handleRequest(
            IpcRequest(
                requestId = 1,
                command = IpcCommands.SEARCH_MANGA,
                payloadJson = json.encodeToString(SearchPayload(5005L, 1, "query")),
            ),
        )

        response.success shouldBe true
        source.receivedFilters!!.isEmpty() shouldBe true
    }

    private class MockTachiyomiGroupFilter(
        name: String,
        state: List<TFilter<*>>,
    ) : TFilter.Group<TFilter<*>>(name, state)

    private class MockTachiyomiCheckBox(name: String, state: Boolean) : TFilter.CheckBox(name, state)

    private class MockTachiyomiTriState(name: String, state: Int) : TFilter.TriState(name, state)

    private class MockTachiyomiSource : CatalogueSource {
        override val id: Long = 9001L
        override val name: String = "Mock Tachiyomi Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var lastSearchFilters: TFilterList? = null

        override suspend fun getPopularManga(page: Int): TMangasPage = TMangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): TMangasPage = TMangasPage(emptyList(), false)

        override suspend fun getSearchManga(page: Int, query: String, filters: TFilterList): TMangasPage {
            lastSearchFilters = filters
            return TMangasPage(
                listOf(
                    TSManga.create().apply {
                        url = "/search"
                        title = query
                    },
                ),
                false,
            )
        }

        override suspend fun getMangaUpdate(
            manga: TSManga,
            chapters: List<TSChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): TSMangaUpdate = TSMangaUpdate(manga, chapters)

        override suspend fun getPageList(chapter: TSChapter): List<TPage> = emptyList()

        override fun getFilterList(): TFilterList = TFilterList(
            MockTachiyomiGroupFilter(
                "Genres",
                listOf(
                    MockTachiyomiCheckBox("Action", false),
                    MockTachiyomiTriState("Romance", TFilter.TriState.STATE_IGNORE),
                ),
            ),
        )
    }

    @Test
    fun `tachiyomi group filters are serialized and applied to search`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val mockSource = MockTachiyomiSource()
        engine.registerSource(TachiyomiCatalogueSourceAdapter(mockSource))

        val filtersResponse = engine.handleRequest(
            IpcRequest(1, IpcCommands.GET_FILTER_LIST, json.encodeToString(SourcePayload(9001L))),
        )
        filtersResponse.success shouldBe true

        val filters = decodeFilterList(filtersResponse.payloadJson)
        val group = filters.filters.single().shouldBeInstanceOf<Filter.Group<*>>()
        group.name shouldBe "Genres"
        group.state.size shouldBe 2
        group.state[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe false
        group.state[1].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_IGNORE

        val selected = FilterList(
            Filter.Group(
                "Genres",
                listOf(
                    Filter.CheckBox("Action", true),
                    Filter.TriState("Romance", Filter.TriState.STATE_EXCLUDE),
                ),
            ),
        )
        val searchResponse = engine.handleRequest(
            IpcRequest(
                requestId = 2,
                command = IpcCommands.SEARCH_MANGA,
                payloadJson = json.encodeToString(
                    SearchPayload(9001L, 1, "query", encodeFilterList(selected)),
                ),
            ),
        )
        searchResponse.success shouldBe true

        val applied = mockSource.lastSearchFilters!!
        val appliedGroup = applied.filterIsInstance<MockTachiyomiGroupFilter>().single()
        appliedGroup.state[0].shouldBeInstanceOf<MockTachiyomiCheckBox>().state shouldBe true
        appliedGroup.state[1].shouldBeInstanceOf<MockTachiyomiTriState>().state shouldBe TFilter.TriState.STATE_EXCLUDE
    }

    private class NoFiltersCapturingSource : WindowsCatalogueSource {
        override val id: Long = 5006L
        override val name: String = "No Filters Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var receivedFilters: FilterList? = null

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
            receivedFilters = filters
            return MangasPage(emptyList(), false)
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        override fun getFilterList(): FilterList = FilterList()
    }

    @Test
    fun `search manga falls back to decoded filters when source has no filters`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val source = NoFiltersCapturingSource()
        engine.registerSource(source)

        val selected = FilterList(
            Filter.CheckBox("Completed", true),
            Filter.Text("Author", "Oda"),
        )
        val response = engine.handleRequest(
            IpcRequest(
                requestId = 1,
                command = IpcCommands.SEARCH_MANGA,
                payloadJson = json.encodeToString(
                    SearchPayload(5006L, 1, "query", encodeFilterList(selected)),
                ),
            ),
        )

        response.success shouldBe true
        val received = source.receivedFilters!!
        received.filters.size shouldBe 2
        received.filters[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
        received.filters[1].shouldBeInstanceOf<Filter.Text>().state shouldBe "Oda"
    }
}
