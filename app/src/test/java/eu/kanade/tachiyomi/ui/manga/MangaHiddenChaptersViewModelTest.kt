package eu.kanade.tachiyomi.ui.manga

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks

@OptIn(ExperimentalCoroutinesApi::class)
class MangaHiddenChaptersViewModelTest {
    private val viewModels = mutableListOf<MangaViewModel>()
    private val hide = MutableStateFlow(false)
    private val preference = mockk<Preference<Boolean>>()
    private val preferences = spyk(LibraryPreferences(InMemoryPreferenceStore()))
    private val manga = Manga.create().copy(
        id = 1,
        source = 1,
        favoriteAt = kotlin.time.Instant.fromEpochMilliseconds(1),
        initialized = true,
    )
    private val visible = Chapter.create().copy(id = 1, mangaId = 1, chapterNumber = 1.0)

    // Absent from the normal subscription because its scanlator is excluded.
    private val hidden = Chapter.create().copy(id = 2, mangaId = 1, chapterNumber = 2.0, hidden = true, read = true)
    private val normalData = MutableStateFlow(manga to listOf(visible))
    private val hiddenData = MutableStateFlow(listOf(hidden))
    private val chapters = mockk<ChapterRepository>()
    private val getChapters = GetChaptersByMangaId(chapters)
    private val getManga = mockk<GetMangaWithChapters>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        mockkStatic("eu.kanade.domain.manga.model.MangaKt")
        every { any<Manga>().downloadedFilter } returns TriState.DISABLED
        every { preference.get() } answers { hide.value }
        every { preference.changes() } returns hide
        every { preferences.hideHiddenChapters } returns preference
        coEvery { getManga.awaitManga(1) } returns manga
        coEvery { getManga.subscribe(1, true) } returns normalData
        every { chapters.getHiddenChaptersByMangaIdAsFlow(1) } returns hiddenData
    }

    @AfterEach
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        unmockkStatic("eu.kanade.domain.manga.model.MangaKt")
    }

    @Test
    fun `recovery uses unfiltered hidden data and back preserves global visibility`() = runBlocking {
        val vm = createViewModel()
        var state = vm.awaitState { it.hasHiddenChapters }
        assertEquals(listOf(1L), state.processedChapters.map { it.id })
        assertEquals(listOf(2L), state.hiddenChapters.map { it.id })
        vm.toggleAllSelection(true)
        vm.awaitState { it.selectedChapters.size == 1 }
        vm.showHiddenChapters()
        state = vm.awaitState { it.dialog == MangaViewModel.Dialog.HiddenChapters && !it.isAnySelected }
        assertEquals(listOf(2L), state.hiddenChapters.map { it.id })
        assertFalse(state.hideHiddenChapters)
        vm.dismissDialog()
        state = vm.awaitState { it.dialog == null }
        assertFalse(state.hideHiddenChapters)
        assertFalse(hide.value)

        // The unhide write emits through both database subscriptions, without fetching the source.
        val unhidden = hidden.copy(hidden = false)
        hiddenData.value = emptyList()
        normalData.value = manga to listOf(visible, unhidden)
        state = vm.awaitState { !it.hasHiddenChapters && it.processedChapters.size == 2 }
        assertTrue(state.hiddenChapters.isEmpty())
        verify(exactly = 1) { chapters.getHiddenChaptersByMangaIdAsFlow(1) }
        coVerify(exactly = 0) { getManga.subscribe(1, false) }
    }

    @Test
    fun `preference changes update normal rows clear invisible ids and apply to fresh screens`() = runBlocking {
        normalData.value = manga to listOf(visible, hidden)
        val vm = createViewModel()
        vm.awaitState { it.processedChapters.size == 2 }
        vm.toggleAllSelection(true)
        vm.awaitState { it.selectedChapters.size == 2 }
        hide.value = true
        var state = vm.awaitState { it.hideHiddenChapters && it.processedChapters.size == 1 }
        assertEquals(listOf(1L), state.selectedChapters.map { it.id })
        vm.toggleAllSelection(false)
        vm.awaitState { !it.isAnySelected }
        hide.value = false
        state = vm.awaitState { !it.hideHiddenChapters && it.processedChapters.size == 2 }
        assertTrue(state.selectedChapters.isEmpty())
        hide.value = true
        vm.awaitState { it.hideHiddenChapters }
        val fresh = createViewModel().awaitState { it.hideHiddenChapters }
        assertEquals(listOf(1L), fresh.processedChapters.map { it.id })
        assertEquals(listOf(2L), fresh.hiddenChapters.map { it.id })
    }

    @Test
    fun `dedicated hidden subscription ignores all ordinary filters and updates live`() = runBlocking {
        val filtered = manga.copy(chapterFlags = Manga.CHAPTER_SHOW_UNREAD or Manga.CHAPTER_SHOW_NOT_BOOKMARKED)
        normalData.value = filtered to emptyList()
        every { any<Manga>().downloadedFilter } returns TriState.ENABLED_IS
        val filteredHidden = hidden.copy(bookmark = true, scanlator = "Excluded")
        hiddenData.value = listOf(filteredHidden)
        val vm = createViewModel()
        var state = vm.awaitState { it.hasHiddenChapters }
        assertTrue(state.processedChapters.isEmpty())
        assertEquals(listOf(filteredHidden), state.hiddenChapters.map { it.chapter })
        hiddenData.value = emptyList()
        state = vm.awaitState { !it.hasHiddenChapters }
        assertTrue(state.hiddenChapters.isEmpty())
        verify(exactly = 1) { chapters.getHiddenChaptersByMangaIdAsFlow(1) }
        coVerify(exactly = 0) { getManga.subscribe(1, false) }
    }

    private suspend fun MangaViewModel.awaitState(predicate: (MangaViewModel.State.Success) -> Boolean) =
        withTimeout(10_000) { state.filterIsInstance<MangaViewModel.State.Success>().first(predicate) }

    private fun createViewModel(): MangaViewModel {
        val downloads = mockk<DownloadManager>()
        every { downloads.queueState } returns MutableStateFlow(emptyList<Download>())
        every { downloads.statusFlow() } returns emptyFlow()
        every { downloads.progressFlow() } returns emptyFlow()
        every { downloads.getQueuedDownloadsByChapterId() } returns emptyMap()
        every { downloads.getDownloadedChapterIds(any(), any()) } returns emptySet()
        val cache = mockk<DownloadCache>()
        every { cache.changes } returns MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val available = mockk<GetAvailableScanlators>()
        every { available.subscribe(1) } returns flowOf(emptySet())
        val excluded = mockk<GetExcludedScanlators>()
        every { excluded.subscribe(1) } returns flowOf(emptySet())
        val source = mockk<Source>()
        val sources = mockk<SourceManager>()
        coEvery { sources.getOrStub(1) } returns source
        val trackers = mockk<TrackerManager>()
        every { trackers.loggedInTrackersFlow() } returns flowOf(emptyList())
        val tracks = mockk<GetTracks>()
        every { tracks.subscribe(1) } returns flowOf(emptyList())
        val reader = mockk<ReaderPreferences>()
        every { reader.skipFiltered } returns InMemoryPreferenceStore().getBoolean("skip_filtered", false)
        return MangaViewModel(
            mangaId = 1,
            isFromSource = false,
            appScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            context = mockk(),
            libraryPreferences = preferences,
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            readerPreferences = reader,
            trackerManager = trackers,
            trackChapter = mockk(),
            downloadManager = downloads,
            downloadCache = cache,
            getMangaAndChapters = getManga,
            getChaptersByMangaId = getChapters,
            getDuplicateLibraryManga = mockk(),
            getAvailableScanlators = available,
            getExcludedScanlators = excluded,
            setExcludedScanlators = mockk(),
            setMangaChapterFlags = mockk(),
            setMangaDefaultChapterFlags = mockk(),
            setReadStatus = mockk(),
            setChapterHidden = mockk(),
            updateChapter = mockk(),
            updateManga = mockk(),
            getCategories = mockk(),
            getTracks = tracks,
            addTracks = mockk(),
            setMangaCategories = mockk(),
            filterChaptersForDownload = mockk(),
            updateMangaFromRemote = mockk(),
            sourceManager = sources,
            refreshTracks = mockk(),
            coverCache = mockk(),
        ).also(viewModels::add)
    }
}
