package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.reader.session.ProgressWriteResult
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class LibraryPresenterTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `starts loading then exposes database rows without changing their order`() = runBlocking {
        val rows = MutableStateFlow<List<LibraryManga>>(emptyList())
        val presenter = LibraryPresenter(FakeLibraryRepository { rows }, scope)

        presenter.state.value.loading shouldBe true
        rows.value = listOf(manga(2, "漫画 二"), manga(1, "Alpha"))

        presenter.awaitState { !it.loading && it.items.size == 2 }.items.map(LibraryManga::id)
            .shouldContainExactly(2L, 1L)
        presenter.close()
    }

    @Test
    fun `query matches title and author case insensitively and preserves Unicode`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "The Apothecary Diaries", author = "Natsu Hyuuga"),
                manga(2, "葬送的芙莉莲", author = "Kanehito Yamada"),
                manga(3, "Other", author = "Someone"),
            ),
        )
        val presenter = LibraryPresenter(FakeLibraryRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        presenter.setQuery("HYUUGA")
        presenter.awaitState { it.query == "HYUUGA" && it.items.size == 1 }.items.single().id shouldBe 1L
        presenter.setQuery("芙莉莲")
        presenter.awaitState { it.query == "芙莉莲" && it.items.size == 1 }.items.single().title shouldBe "葬送的芙莉莲"
        presenter.close()
    }

    @Test
    fun `selected manga is retained only while its row remains present`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One"), manga(2, "Two")))
        val presenter = LibraryPresenter(FakeLibraryRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 2 }

        presenter.selectManga(2)
        presenter.awaitState { it.selectedMangaId == 2L }
        rows.value = listOf(manga(1, "One"))
        presenter.awaitState { it.items.size == 1 && it.selectedMangaId == null }
        rows.value = listOf(manga(1, "One"), manga(2, "Two"))

        presenter.awaitState { it.items.size == 2 }.selectedMangaId shouldBe null
        presenter.close()
    }

    @Test
    fun `selection request for a missing row is not restored if that id appears later`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One")))
        val presenter = LibraryPresenter(FakeLibraryRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 1 }

        presenter.selectManga(99)
        rows.value = listOf(manga(1, "One"), manga(99, "Later"))

        presenter.awaitState { it.items.size == 2 }.selectedMangaId shouldBe null
        presenter.close()
    }

    @Test
    fun `repository exception is visible and retry resubscribes without fallback rows`() = runBlocking {
        val attempts = AtomicInteger()
        val repository = FakeLibraryRepository {
            flow {
                if (attempts.incrementAndGet() == 1) error("database unavailable")
                emit(listOf(manga(7, "Recovered")))
            }
        }
        val presenter = LibraryPresenter(repository, scope)

        val failed = presenter.awaitState { it.errorMessage != null }
        failed.items shouldBe emptyList()
        failed.errorMessage shouldBe "database unavailable"
        presenter.retry()

        val recovered = presenter.awaitState { !it.loading && it.items.singleOrNull()?.id == 7L }
        recovered.errorMessage shouldBe null
        attempts.get() shouldBe 2
        presenter.close()
    }

    @Test
    fun `exception while creating repository flow is visible`() = runBlocking {
        val presenter = LibraryPresenter(
            FakeLibraryRepository { error("database already closed") },
            scope,
        )

        val failed = presenter.awaitState { it.errorMessage != null }

        failed.errorMessage shouldBe "database already closed"
        failed.items shouldBe emptyList()
        presenter.close()
    }

    @Test
    fun `close cancels only presenter work`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One")))
        val presenter = LibraryPresenter(FakeLibraryRepository { rows }, scope)
        presenter.awaitState { !it.loading }

        presenter.close()

        parentJob.isActive shouldBe true
    }

    @Test
    fun `selection observes matching manga and chapters and preserves repository chapter order`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One"), manga(2, "Two")))
        val details = mapOf(
            1L to MutableStateFlow(details(1, "One")),
            2L to MutableStateFlow(details(2, "Two")),
        )
        val chapters = mapOf(
            1L to MutableStateFlow(listOf(chapter(12, 1), chapter(11, 1))),
            2L to MutableStateFlow(listOf(chapter(22, 2), chapter(21, 2))),
        )
        val presenter = LibraryPresenter(
            FakeLibraryRepository(details = details, chapters = chapters, libraryFlow = { rows }),
            scope,
        )
        presenter.awaitState { !it.loading && it.items.size == 2 }

        presenter.selectManga(2)

        val selected = presenter.awaitDetail { it.manga?.id == 2L && it.chapters.size == 2 }
        selected.manga?.title shouldBe "Two"
        selected.chapters.map(LibraryChapter::id).shouldContainExactly(22L, 21L)
        presenter.close()
    }

    @Test
    fun `switching selection detaches old detail flow and missing selected manga clears selection`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One"), manga(2, "Two")))
        val first = MutableStateFlow<MangaDetails?>(details(1, "One"))
        val second = MutableStateFlow<MangaDetails?>(details(2, "Two"))
        val presenter = LibraryPresenter(
            FakeLibraryRepository(
                details = mapOf(1L to first, 2L to second),
                chapters = mapOf(1L to MutableStateFlow(emptyList()), 2L to MutableStateFlow(emptyList())),
                libraryFlow = { rows },
            ),
            scope,
        )
        presenter.awaitState { !it.loading }
        presenter.selectManga(1)
        presenter.awaitDetail { it.manga?.id == 1L }

        presenter.selectManga(2)
        presenter.awaitDetail { it.manga?.id == 2L }
        first.value = null
        presenter.awaitDetail { it.manga?.id == 2L }.manga?.title shouldBe "Two"
        second.value = null

        presenter.awaitState { it.selectedMangaId == null }
        presenter.awaitDetail { !it.loading && it.manga == null }.chapters shouldBe emptyList()
        presenter.close()
    }

    @Test
    fun `detail flow failure is visible and retry resubscribes for the selected id`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "One")))
        val attempts = AtomicInteger()
        val detailFlow = flow<MangaDetails?> {
            if (attempts.incrementAndGet() == 1) error("detail database unavailable")
            emit(details(1, "Recovered detail"))
        }
        val presenter = LibraryPresenter(
            FakeLibraryRepository(
                details = mapOf(1L to detailFlow),
                chapters = mapOf(1L to MutableStateFlow(emptyList())),
                libraryFlow = { rows },
            ),
            scope,
        )
        presenter.awaitState { !it.loading }
        presenter.selectManga(1)

        presenter.awaitDetail { it.errorMessage == "detail database unavailable" }
        presenter.retryDetail()

        presenter.awaitDetail { it.manga?.title == "Recovered detail" }
        attempts.get() shouldBe 2
        presenter.close()
    }

    @Test
    fun `local reader availability and returned progress are derived off the detail flow`() = runBlocking {
        val file = Files.createTempFile("reader-presenter", ".cbz")
        val chapters = MutableStateFlow(listOf(chapter(12, 1)))
        val asset = ReaderChapterAsset(
            mangaId = 1,
            chapterId = 12,
            mangaTitle = "One",
            chapterName = "Chapter 12",
            storageRoot = file.parent,
            relativePath = file.fileName,
            assetKind = "cbz",
            sizeBytes = 1,
            modifiedAt = 0,
            lastPageRead = 0,
            read = false,
        )
        val presenter = LibraryPresenter(
            FakeLibraryRepository(
                details = mapOf(1L to MutableStateFlow(details(1, "One"))),
                chapters = mapOf(1L to chapters),
                libraryFlow = { MutableStateFlow(listOf(manga(1, "One"))) },
            ),
            scope,
            FakeReaderLibraryPort(mapOf(12L to asset)),
        )
        presenter.awaitState { !it.loading }
        presenter.selectManga(1)

        presenter.awaitDetail { it.readerAvailability[12L] == ChapterReaderAvailability.Readable }
        chapters.value = listOf(chapter(12, 1).copy(lastPageRead = 4, read = true))
        presenter.awaitDetail { it.chapters.singleOrNull()?.lastPageRead == 4L }.chapters.single().read shouldBe true
        presenter.close()
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun LibraryPresenter.awaitDetail(
        predicate: (MangaDetailUiState) -> Boolean,
    ): MangaDetailUiState = withTimeout(5_000) { detailState.first(predicate) }

    private fun manga(
        id: Long,
        title: String,
        author: String? = null,
    ) = LibraryManga(
        id = id,
        sourceId = 100 + id,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 12,
        unreadCount = 3,
        author = author,
    )

    private fun details(id: Long, title: String) = MangaDetails(
        id, 100 + id, "/$id", title, null, "Author", "Description", "[]", 0, null, true,
        0, 0, 0, "ALWAYS_UPDATE", 0, null, "[]", 0, "Notes", true, "{}", emptyList(),
    )

    private fun chapter(id: Long, mangaId: Long) = LibraryChapter(
        id, mangaId, "/$id", "Chapter $id", null, false, false, 0, 0, 0, id.toDouble(), id, 0, 0, "{}",
    )
}

private class FakeReaderLibraryPort(
    private val assets: Map<Long, ReaderChapterAsset>,
) : ReaderLibraryPort {
    override fun chapterAsset(chapterId: Long): ReaderChapterAsset? = assets[chapterId]

    override fun adjacentReadableChapter(chapterId: Long, direction: ChapterDirection): ReaderChapterAsset? = null

    override suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult = ProgressWriteResult.APPLIED
}

private class FakeLibraryRepository(
    private val details: Map<Long, Flow<MangaDetails?>> = emptyMap(),
    private val chapters: Map<Long, Flow<List<LibraryChapter>>> = emptyMap(),
    private val libraryFlow: () -> Flow<List<LibraryManga>>,
) : LibraryRepository {
    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = libraryFlow()
    override fun observeManga(id: Long): Flow<MangaDetails?> = details[id] ?: error("Missing manga flow for $id")
    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> =
        chapters[mangaId] ?: error("Missing chapter flow for $mangaId")
    override fun observeCategories(): Flow<List<mihon.desktop.library.model.CategoryRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeHistory(query: String): Flow<List<mihon.desktop.library.model.HistoryWithDetails>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeTracking(mangaId: Long): Flow<List<mihon.desktop.library.model.TrackingRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = error("Not used")
    override fun mangaSnapshot(id: Long): MangaDetails? = error("Not used")
    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = error("Not used")
    override fun categoriesSnapshot(): List<mihon.desktop.library.model.CategoryRecord> = emptyList()
    override fun historySnapshot(query: String): List<mihon.desktop.library.model.HistoryWithDetails> = emptyList()
    override fun trackingSnapshot(mangaId: Long): List<mihon.desktop.library.model.TrackingRecord> = emptyList()
    override fun latestImportReport(): ImportReport? = error("Not used")
}
