package mihon.desktop.ui.upcoming

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.ui.library.TriStateFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class UpcomingPresenterTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)
    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Long = LocalDateTime.of(2025, 9, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `groups future chapters by month and day and resolves source labels`() = runBlocking {
        val sep20 = LocalDate.of(2025, 9, 20)
        val sep21 = LocalDate.of(2025, 9, 21)
        val oct2 = LocalDate.of(2025, 10, 2)
        val presenter = UpcomingPresenter(
            repository = repositoryWithTwoManga(sep20, sep21, oct2),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )

        val state = presenter.awaitState { !it.loading && it.hasAnyUpcoming }

        state.selectedMonth shouldBe YearMonth.of(2025, 9)
        state.entriesByDate.keys shouldBe setOf(sep20, sep21, oct2)
        state.monthDays shouldBe mapOf(sep20 to 1, sep21 to 1)
        state.monthEntries.map { it.chapterName } shouldContainExactly listOf("Alpha 1", "Alpha 2")
        state.entriesByDate.getValue(oct2).single().mangaTitle shouldBe "Beta"
        state.entriesByDate.getValue(oct2).single().sourceLabel shouldBe "Source Eight"
        state.categories.map { it.name } shouldContainExactly listOf("Action", "Drama")
        state.hasActiveFilters shouldBe false
        presenter.close()
    }

    @Test
    fun `day selection filters entries and changing month resets the selected day`() = runBlocking {
        val sep20 = LocalDate.of(2025, 9, 20)
        val sep21 = LocalDate.of(2025, 9, 21)
        val oct2 = LocalDate.of(2025, 10, 2)
        val presenter = UpcomingPresenter(
            repository = repositoryWithTwoManga(sep20, sep21, oct2),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )
        presenter.awaitState { !it.loading && it.visibleEntries.size == 2 }

        presenter.selectDay(sep20)
        val selectedDay = presenter.awaitState { it.selectedDate == sep20 && it.visibleEntries.size == 1 }
        selectedDay.visibleEntries.single().chapterName shouldBe "Alpha 1"
        selectedDay.selectedDay?.date shouldBe sep20

        presenter.selectDate(null)
        presenter.awaitState { it.selectedDate == null && it.visibleEntries.size == 2 }

        presenter.nextMonth()
        val nextMonth = presenter.awaitState {
            it.selectedMonth == YearMonth.of(2025, 10) && it.selectedDate == null
        }
        nextMonth.visibleEntries.single().chapterName shouldBe "Beta 1"

        presenter.previousMonth()
        presenter.awaitState { it.selectedMonth == YearMonth.of(2025, 9) && it.visibleEntries.size == 2 }
        presenter.close()
    }

    @Test
    fun `category filters cycle include exclude and clear`() = runBlocking {
        val sep20 = LocalDate.of(2025, 9, 20)
        val sep21 = LocalDate.of(2025, 9, 21)
        val sep25 = LocalDate.of(2025, 9, 25)
        val presenter = UpcomingPresenter(
            repository = repositoryWithTwoManga(sep20, sep21, sep25),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )
        presenter.awaitState { !it.loading && it.visibleEntries.size == 3 }

        presenter.cycleCategory(10L)
        presenter.awaitState {
            it.categoryFilters[10L] == TriStateFilter.Include &&
                it.visibleEntries.isNotEmpty() &&
                it.visibleEntries.all { entry -> entry.mangaId == 1L }
        }

        presenter.cycleCategory(10L)
        presenter.awaitState {
            it.categoryFilters[10L] == TriStateFilter.Exclude &&
                it.visibleEntries.map { entry -> entry.mangaId } == listOf(2L)
        }

        presenter.cycleCategory(10L)
        presenter.awaitState { it.categoryFilters.isEmpty() && it.visibleEntries.size == 3 }

        presenter.cycleCategory(10L)
        presenter.cycleCategory(20L)
        presenter.awaitState { it.visibleEntries.size == 3 && it.hasActiveFilters }

        presenter.clearFilters()
        presenter.awaitState { !it.hasActiveFilters && it.visibleEntries.size == 3 }
        presenter.close()
    }

    @Test
    fun `empty state is reported when no chapter has a future date`() = runBlocking {
        val pastDate = LocalDate.of(2025, 9, 1)
        val presenter = UpcomingPresenter(
            repository = FakeUpcomingRepository(
                library = flowOf(listOf(manga(1, "Alpha", 7))),
                chapters = mapOf(
                    1L to flowOf(
                        listOf(chapter(11, 1, "Alpha 1", epoch(pastDate, 10))),
                    ),
                ),
                mangaCategories = mapOf(1L to listOf(10L)),
                categories = flowOf(listOf(category(10, "Action"))),
            ),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )

        val state = presenter.awaitState { !it.loading }

        state.visibleEntries shouldBe emptyList()
        state.hasAnyUpcoming shouldBe false
        state.isEmpty shouldBe true
        state.emptyTitle shouldBe "No upcoming chapters"
        state.emptySubtitle shouldContain "Future chapter"
        presenter.close()
    }

    @Test
    fun `filters that remove every future chapter report a matching empty state`() = runBlocking {
        val sep20 = LocalDate.of(2025, 9, 20)
        val presenter = UpcomingPresenter(
            repository = FakeUpcomingRepository(
                library = flowOf(listOf(manga(1, "Alpha", 7))),
                chapters = mapOf(
                    1L to flowOf(listOf(chapter(11, 1, "Alpha 1", epoch(sep20, 10)))),
                ),
                mangaCategories = mapOf(1L to listOf(10L)),
                categories = flowOf(listOf(category(10, "Action"), category(20, "Drama"))),
            ),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )
        presenter.awaitState { !it.loading && it.hasAnyUpcoming }

        presenter.cycleCategory(20L)
        val state = presenter.awaitState { it.hasActiveFilters && it.visibleEntries.isEmpty() }

        state.hasAnyUpcomingBeforeFilters shouldBe true
        state.emptyTitle shouldBe "No matching upcoming chapters"
        state.emptySubtitle shouldContain "category filters"
        presenter.close()
    }

    @Test
    fun `selected day without chapters still shows the filtered empty message`() = runBlocking {
        val sep20 = LocalDate.of(2025, 9, 20)
        val presenter = UpcomingPresenter(
            repository = repositoryWithTwoManga(sep20, LocalDate.of(2025, 9, 21), LocalDate.of(2025, 10, 2)),
            scope = scope,
            clock = { now },
            zoneId = zone,
        )
        presenter.awaitState { !it.loading && it.hasAnyUpcoming }

        presenter.selectDay(LocalDate.of(2025, 9, 25))
        val state = presenter.awaitState { it.selectedDate == LocalDate.of(2025, 9, 25) }

        state.visibleEntries shouldBe emptyList()
        state.emptyTitle shouldBe "No chapters on this day"
        presenter.close()
    }

    private fun repositoryWithTwoManga(
        firstDate: LocalDate,
        secondDate: LocalDate,
        thirdDate: LocalDate,
    ): FakeUpcomingRepository = FakeUpcomingRepository(
        library = flowOf(listOf(manga(1, "Alpha", 7), manga(2, "Beta", 8))),
        chapters = mapOf(
            1L to flowOf(
                listOf(
                    chapter(11, 1, "Alpha 1", epoch(firstDate, 10)),
                    chapter(12, 1, "Alpha 2", epoch(secondDate, 11)),
                    chapter(13, 1, "Alpha Past", epoch(LocalDate.of(2025, 9, 1), 10)),
                ),
            ),
            2L to flowOf(listOf(chapter(21, 2, "Beta 1", epoch(thirdDate, 9)))),
        ),
        mangaCategories = mapOf(1L to listOf(10L), 2L to listOf(20L)),
        categories = flowOf(listOf(category(10, "Action"), category(20, "Drama"))),
        sources = listOf(source(7, "Source Seven"), source(8, "Source Eight")),
    )

    private suspend fun UpcomingPresenter.awaitState(predicate: (UpcomingUiState) -> Boolean): UpcomingUiState =
        withTimeout(5_000) { state.first(predicate) }

    private fun manga(id: Long, title: String, sourceId: Long) = LibraryManga(
        id = id,
        sourceId = sourceId,
        url = "/manga/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 3,
        unreadCount = 2,
        author = null,
    )

    private fun chapter(
        id: Long,
        mangaId: Long,
        name: String,
        dateUpload: Long,
    ) = LibraryChapter(
        id = id,
        mangaId = mangaId,
        url = "/chapter/$id",
        name = name,
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        dateUpload = dateUpload,
        chapterNumber = id.toDouble(),
        sourceOrder = id,
        lastModifiedAt = 0,
        version = 0,
        memoJson = "{}",
    )

    private fun category(id: Long, name: String) = CategoryRecord(id = id, name = name, sortOrder = id, flags = 0)

    private fun source(id: Long, name: String) = SourceRecord(sourceId = id, name = name, importedAt = 0)

    private fun epoch(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}

private class FakeUpcomingRepository(
    private val library: Flow<List<LibraryManga>> = flowOf(emptyList()),
    private val chapters: Map<Long, Flow<List<LibraryChapter>>> = emptyMap(),
    private val mangaCategories: Map<Long, List<Long>> = emptyMap(),
    private val categories: Flow<List<CategoryRecord>> = flowOf(emptyList()),
    private val sources: List<SourceRecord> = emptyList(),
) : LibraryRepository {
    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = library

    override fun observeManga(id: Long): Flow<MangaDetails?> = flowOf(null)

    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> =
        chapters[mangaId] ?: flowOf(emptyList())

    override fun observeCategories(): Flow<List<CategoryRecord>> = categories

    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = flowOf(emptyList())

    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = flowOf(emptyList())

    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = emptyList()

    override fun mangaSnapshot(id: Long): MangaDetails? = null

    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = emptyList()

    override fun categoriesSnapshot(): List<CategoryRecord> = emptyList()

    override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()

    override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()

    override fun latestImportReport(): ImportReport? = null

    override fun allMangaSnapshot(): List<MangaRecord> = emptyList()

    override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()

    override fun allCategoriesSnapshot(): List<CategoryRecord> = emptyList()

    override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = mangaCategories

    override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()

    override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()

    override fun allSourcesSnapshot(): List<SourceRecord> = sources

    override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()

    override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()

    override fun checkIntegrity(): List<String> = listOf("ok")
}
