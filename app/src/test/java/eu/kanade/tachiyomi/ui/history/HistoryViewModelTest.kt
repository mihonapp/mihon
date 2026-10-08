package eu.kanade.tachiyomi.ui.history

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import java.util.Date

class HistoryViewModelTest {

    private lateinit var getHistory: GetHistory
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadCache: DownloadCache
    private lateinit var historyPreferences: HistoryPreferences

    private lateinit var filterDownloaded: FakePreference<TriState>
    private lateinit var filterUnread: FakePreference<TriState>
    private lateinit var filterStarted: FakePreference<TriState>
    private lateinit var filterBookmarked: FakePreference<TriState>
    private lateinit var filterExcludedScanlators: FakePreference<Boolean>
    private lateinit var filterIncludedCategories: FakePreference<List<Long>>
    private lateinit var filterExcludedCategories: FakePreference<List<Long>>

    private val allHistory = listOf(
        historyItem(
            id = 1L,
            chapterId = 11L,
            mangaId = 100L,
            title = "Naruto",
            chapterName = "Ch 1",
            scanlator = "ScanA",
            read = true,
            bookmark = true,
        ),
        historyItem(
            id = 2L,
            chapterId = 12L,
            mangaId = 200L,
            title = "One Piece",
            chapterName = "Ch 2",
            scanlator = "ScanB",
            read = false,
            bookmark = false,
            lastPageRead = 5L,
        ),
        historyItem(
            id = 3L,
            chapterId = 13L,
            mangaId = 300L,
            title = "Bleach",
            chapterName = "Ch 3",
            scanlator = "ScanA",
            read = false,
            bookmark = false,
        ),
    )

    @BeforeEach
    fun beforeEach() {
        filterDownloaded = FakePreference(TriState.DISABLED)
        filterUnread = FakePreference(TriState.DISABLED)
        filterStarted = FakePreference(TriState.DISABLED)
        filterBookmarked = FakePreference(TriState.DISABLED)
        filterExcludedScanlators = FakePreference(false)
        filterIncludedCategories = FakePreference(emptyList())
        filterExcludedCategories = FakePreference(emptyList())

        historyPreferences = mockk {
            every { filterDownloaded } returns this@HistoryViewModelTest.filterDownloaded
            every { filterUnread } returns this@HistoryViewModelTest.filterUnread
            every { filterStarted } returns this@HistoryViewModelTest.filterStarted
            every { filterBookmarked } returns this@HistoryViewModelTest.filterBookmarked
            every { filterExcludedScanlators } returns this@HistoryViewModelTest.filterExcludedScanlators
            every { filterIncludedCategories } returns this@HistoryViewModelTest.filterIncludedCategories
            every { filterExcludedCategories } returns this@HistoryViewModelTest.filterExcludedCategories
        }

        downloadManager = mockk {
            every { queueState } returns MutableStateFlow(emptyList())
            every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns false
        }
        downloadCache = mockk {
            every { changes } returns MutableStateFlow(Unit)
        }

        // Fake the SQL layer: apply query + tri-state filters like historyView.sq does.
        getHistory = mockk {
            every { subscribe(any(), any(), any(), any(), any(), any(), any()) } answers {
                val query = args[0] as String
                val unread = args[1] as Boolean?
                val started = args[2] as Boolean?
                val bookmarked = args[3] as Boolean?
                flowOf(
                    allHistory
                        .filter { it.title.contains(query, ignoreCase = true) }
                        .filter { unread == null || it.read == !unread }
                        .filter {
                            when (started) {
                                null -> true
                                true -> it.lastPageRead > 0 && !it.read
                                false -> it.lastPageRead == 0L && !it.read
                            }
                        }
                        .filter { bookmarked == null || it.bookmark == bookmarked },
                )
            }
        }
    }

    @AfterEach
    fun afterEach() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(
            addTracks = mockk<AddTracks>(relaxed = true),
            downloadCache = downloadCache,
            downloadManager = downloadManager,
            getCategories = mockk<GetCategories>(relaxed = true),
            getDuplicateLibraryManga = mockk<GetDuplicateLibraryManga>(relaxed = true),
            getHistory = getHistory,
            getManga = mockk<GetManga>(relaxed = true),
            getNextChapters = mockk<GetNextChapters>(relaxed = true),
            historyPreferences = historyPreferences,
            libraryPreferences = mockk<LibraryPreferences>(relaxed = true),
            removeHistory = mockk<RemoveHistory>(relaxed = true),
            setMangaCategories = mockk<SetMangaCategories>(relaxed = true),
            updateManga = mockk<UpdateManga>(relaxed = true),
            sourceManager = mockk<SourceManager>(relaxed = true),
        )
    }

    private suspend fun awaitItemIds(
        viewModel: HistoryViewModel,
        ids: List<Long>,
    ): List<HistoryUiModel> {
        // The DB flow runs on Dispatchers.IO (real threads, not the test scheduler),
        // so await the emission instead of relying on advanceUntilIdle().
        return viewModel.state.first { state ->
            state.list
                ?.filterIsInstance<HistoryUiModel.Item>()
                ?.map { it.item.id }
                ?.sorted() == ids.sorted()
        }.list!!
    }

    @Test
    fun `no filters shows all history and hasActiveFilters false`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        awaitItemIds(viewModel, listOf(1L, 2L, 3L))
        viewModel.state.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `bookmarked filter is forwarded to GetHistory and flags active filters`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        awaitItemIds(viewModel, listOf(1L, 2L, 3L))

        filterBookmarked.set(TriState.ENABLED_IS)

        awaitItemIds(viewModel, listOf(1L))
        viewModel.state.value.hasActiveFilters shouldBe true
        verify { getHistory.subscribe(any(), any(), any(), true, any(), any(), any()) }
    }

    @Test
    fun `unread filter is forwarded to GetHistory`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        awaitItemIds(viewModel, listOf(1L, 2L, 3L))

        filterUnread.set(TriState.ENABLED_IS)

        awaitItemIds(viewModel, listOf(2L, 3L))
        verify { getHistory.subscribe(any(), true, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `downloaded filter applies in Kotlin layer via DownloadManager`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            downloadManager.isChapterDownloaded("Ch 1", any(), any(), any(), any())
        } returns true

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        awaitItemIds(viewModel, listOf(1L, 2L, 3L))

        filterDownloaded.set(TriState.ENABLED_IS)

        awaitItemIds(viewModel, listOf(1L))
        viewModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `search query is forwarded to GetHistory`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        awaitItemIds(viewModel, listOf(1L, 2L, 3L))

        viewModel.updateSearchQuery("naruto")

        awaitItemIds(viewModel, listOf(1L))
        verify { getHistory.subscribe("naruto", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `showFilterDialog emits FilterSheet dialog`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        awaitItemIds(viewModel, listOf(1L, 2L, 3L))

        viewModel.showFilterDialog()
        advanceUntilIdle()

        viewModel.state.value.dialog shouldBe HistoryViewModel.Dialog.FilterSheet
    }

    private fun historyItem(
        id: Long,
        chapterId: Long,
        mangaId: Long,
        title: String,
        chapterName: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long = 0L,
    ): HistoryWithRelations {
        return HistoryWithRelations(
            id = id,
            chapterId = chapterId,
            mangaId = mangaId,
            title = title,
            chapterName = chapterName,
            chapterNumber = 1.0,
            scanlator = scanlator,
            chapterUrl = "https://example.com/$chapterId",
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            sourceId = 1L,
            readAt = Date(1_697_247_357_000L),
            readDuration = 10L,
            coverData = MangaCover(
                mangaId = mangaId,
                sourceId = 1L,
                isMangaFavorite = true,
                url = null,
                lastModified = 0L,
            ),
        )
    }

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)

        override fun key(): String = "test"

        override fun get(): T = state.value

        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() {}

        override fun defaultValue(): T = state.value

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }
}
