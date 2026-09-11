package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ChapterSettingsTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `default settings match the android chapter defaults`() {
        val defaults = ChapterSettings()

        defaults.displayMode shouldBe ChapterDisplayMode.Name
        defaults.sortMode shouldBe ChapterSortMode.SourceOrder
        defaults.sortAscending shouldBe false
        defaults.unreadFilter shouldBe TriStateFilter.Disabled
        defaults.downloadedFilter shouldBe TriStateFilter.Disabled
        defaults.bookmarkedFilter shouldBe TriStateFilter.Disabled
        defaults.excludedScanlators shouldBe emptySet()
        defaults.showMissingChapters shouldBe true
        chapterSettingsFromFlags(chapterFlags = 0L) shouldBe defaults
    }

    @Test
    fun `chapter flags round trip display sort direction and filters`() {
        val settings = ChapterSettings(
            displayMode = ChapterDisplayMode.Number,
            sortMode = ChapterSortMode.UploadDate,
            sortAscending = true,
            unreadFilter = TriStateFilter.Include,
            downloadedFilter = TriStateFilter.Exclude,
            bookmarkedFilter = TriStateFilter.Include,
            excludedScanlators = setOf("Group B"),
            showMissingChapters = false,
        )

        val flags = encodeChapterFlags(existingFlags = 0L, settings = settings)
        val decoded = chapterSettingsFromFlags(
            chapterFlags = flags,
            showMissingChapters = false,
            excludedScanlators = setOf("Group B"),
        )

        decoded shouldBe settings
        encodeChapterFlags(existingFlags = flags, settings = settings) shouldBe flags
    }

    @Test
    fun `memo json keeps unrelated keys when storing missing chapter indicator`() {
        val memo = encodeShowMissingChapters("""{"other":42}""", showMissingChapters = false)

        parseShowMissingChapters(memo) shouldBe false
        Json.parseToJsonElement(memo).jsonObject.getValue("other").jsonPrimitive.int shouldBe 42
    }

    @Test
    fun `missing chapter gap calculation matches android semantics`() {
        calculateChapterGap(higherChapterNumber = 5.0, lowerChapterNumber = 2.0) shouldBe 2
        calculateChapterGap(higherChapterNumber = 5.0, lowerChapterNumber = 4.9) shouldBe 0
        calculateChapterGap(higherChapterNumber = 1.0, lowerChapterNumber = 0.0) shouldBe 0
        calculateChapterGap(higherChapterNumber = -1.0, lowerChapterNumber = 2.0) shouldBe 0
        calculateChapterGap(higherChapterNumber = Double.NaN, lowerChapterNumber = 2.0) shouldBe 0

        calculateMissingChapterCount(
            listOf(
                chapter(1, "One", 1.0, order = 0, scanlator = null),
                chapter(3, "Three", 3.0, order = 1, scanlator = null),
                chapter(5, "Five", 5.0, order = 2, scanlator = null),
            ),
        ) shouldBe 2
        calculateMissingChapterCount(emptyList()) shouldBe 0
    }

    @Test
    fun `build chapter list items inserts missing indicators and applies display mode`() {
        val chapters = listOf(
            chapter(1, "One", 1.0, order = 0, scanlator = "Group A"),
            chapter(3, "Three", 3.0, order = 1, scanlator = "Group A"),
            chapter(5, "Five", 5.0, order = 2, scanlator = "Group A"),
        )

        val withMissing = buildChapterListItems(
            chapters = chapters,
            settings = ChapterSettings(displayMode = ChapterDisplayMode.Number),
        )
        withMissing.map { it.key } shouldContainExactly listOf(
            "chapter-1",
            "missing-1-3",
            "chapter-3",
            "missing-3-5",
            "chapter-5",
        )
        withMissing.filterIsInstance<ChapterListItem.Chapter>().map { it.label } shouldContainExactly
            listOf("1", "3", "5")
        withMissing.filterIsInstance<ChapterListItem.MissingCount>().map { it.count } shouldContainExactly listOf(1, 1)

        val withoutMissing = buildChapterListItems(
            chapters = chapters,
            settings = ChapterSettings(showMissingChapters = false),
        )
        withoutMissing.filterIsInstance<ChapterListItem.MissingCount>() shouldBe emptyList()
    }

    @Test
    fun `presenter persists per manga chapter settings and applies them to the detail list`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(
            mangaRecord(1, "One Piece", sourceId = 100),
            chapters = listOf(
                chapterRecord(11, 1, "/a", "Group A", 1.0),
                chapterRecord(12, 1, "/b", "Group B", 2.0),
                chapterRecord(13, 1, "/c", "Group A", 4.0),
            ),
        )
        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { !it.loading && it.items.size == 1 }
        presenter.selectManga(1)
        val initial = presenter.awaitDetail { it.manga?.id == 1L && it.chapters.size == 3 }
        initial.availableScanlators shouldBe setOf("Group A", "Group B")

        presenter.setExcludedScanlators(setOf("Group B"))
        presenter.setChapterDisplayMode(ChapterDisplayMode.Number)
        presenter.setChapterSort(ChapterSortState(ChapterSortMode.ChapterNumber, ascending = true))
        presenter.setShowMissingChapters(false)

        val updated = presenter.awaitDetail {
            it.chapters.size == 2 &&
                it.chapterSettings.displayMode == ChapterDisplayMode.Number &&
                it.chapterSettings.sortMode == ChapterSortMode.ChapterNumber &&
                it.chapterSettings.sortAscending &&
                !it.chapterSettings.showMissingChapters
        }
        updated.chapters.map(LibraryChapter::id) shouldContainExactly listOf(11L, 13L)
        updated.chapterListItems
            .filterIsInstance<ChapterListItem.Chapter>()
            .map { it.label } shouldContainExactly listOf("1", "4")

        val persisted = repository.mangaSnapshot(1) ?: error("missing manga")
        persisted.excludedScanlatorsJson shouldBe encodeExcludedScanlators(setOf("Group B"))
        persisted.memoJson shouldBe encodeShowMissingChapters("{}", showMissingChapters = false)
        persisted.chapterFlags shouldBe encodeChapterFlags(
            existingFlags = 0L,
            settings = initial.chapterSettings.copy(
                displayMode = ChapterDisplayMode.Number,
                sortMode = ChapterSortMode.ChapterNumber,
                sortAscending = true,
                excludedScanlators = setOf("Group B"),
                showMissingChapters = false,
            ),
        )
        presenter.close()
    }

    @Test
    fun `set as default persists defaults and can apply them to existing manga`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(2, "Naruto", sourceId = 200))
        val preferenceFile = Files.createTempFile("chapter-settings", ".properties")
        val preferences = DesktopPreferenceStore(preferenceFile)
        val presenter = LibraryPresenter(repository, scope, preferences = preferences)
        presenter.awaitState { !it.loading && it.items.size == 2 }
        presenter.selectManga(1)
        presenter.awaitDetail { it.manga?.id == 1L }

        presenter.setChapterDisplayMode(ChapterDisplayMode.Number)
        presenter.setShowMissingChapters(false)
        presenter.setChapterSettingsAsDefault(applyToExisting = true)

        val first = repository.mangaSnapshot(1) ?: error("missing manga 1")
        val second = repository.mangaSnapshot(2) ?: error("missing manga 2")
        first.chapterFlags shouldBe encodeChapterFlags(
            existingFlags = 0L,
            settings = ChapterSettings(
                displayMode = ChapterDisplayMode.Number,
                showMissingChapters = false,
            ),
        )
        second.chapterFlags shouldBe first.chapterFlags
        second.memoJson shouldBe encodeShowMissingChapters("{}", showMissingChapters = false)
        preferences.property(CHAPTER_DEFAULT_FLAGS_KEY) shouldBe first.chapterFlags.toString()
        preferences.property(CHAPTER_DEFAULT_SHOW_MISSING_KEY) shouldBe "false"
        presenter.close()
    }

    @Test
    fun `reset restores persisted defaults and clears the scanlator filter`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(
            mangaRecord(1, "One Piece", sourceId = 100),
            chapters = listOf(chapterRecord(11, 1, "/a", "Group A", 1.0)),
        )
        val preferenceFile = Files.createTempFile("chapter-settings-reset", ".properties")
        val preferences = DesktopPreferenceStore(preferenceFile)
        preferences.update {
            setProperty(CHAPTER_DEFAULT_FLAGS_KEY, CHAPTER_DISPLAY_NUMBER.toString())
            setProperty(CHAPTER_DEFAULT_SHOW_MISSING_KEY, "false")
        }
        val presenter = LibraryPresenter(repository, scope, preferences = preferences)
        presenter.awaitState { !it.loading && it.items.size == 1 }
        presenter.selectManga(1)
        presenter.awaitDetail { it.manga?.id == 1L }

        presenter.setExcludedScanlators(setOf("Group A"))
        presenter.setChapterDisplayMode(ChapterDisplayMode.SourceOrder)
        presenter.setShowMissingChapters(true)
        presenter.resetChapterSettingsToDefault()

        val reset = presenter.awaitDetail {
            it.chapterSettings.displayMode == ChapterDisplayMode.Number &&
                !it.chapterSettings.showMissingChapters &&
                it.chapterSettings.excludedScanlators.isEmpty() &&
                it.chapters.size == 1
        }
        reset.chapterSettings.sortMode shouldBe ChapterSortMode.SourceOrder
        reset.chapterSettings.sortAscending shouldBe false
        val persisted = repository.mangaSnapshot(1) ?: error("missing manga")
        persisted.excludedScanlatorsJson shouldBe "[]"
        persisted.chapterFlags shouldBe CHAPTER_DISPLAY_NUMBER
        persisted.memoJson shouldBe encodeShowMissingChapters("{}", showMissingChapters = false)
        presenter.close()
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun LibraryPresenter.awaitDetail(predicate: (MangaDetailUiState) -> Boolean): MangaDetailUiState =
        withTimeout(5_000) { detailState.first(predicate) }

    private fun mangaRecord(id: Long, title: String, sourceId: Long) = MangaRecord(
        id = id,
        sourceId = sourceId,
        url = "/$id",
        title = title,
        favorite = true,
    )

    private fun chapterRecord(
        id: Long,
        mangaId: Long,
        url: String,
        scanlator: String?,
        number: Double,
    ) = ChapterRecord(
        id = id,
        mangaId = mangaId,
        url = url,
        name = "Chapter $number",
        scanlator = scanlator,
        chapterNumber = number,
        sourceOrder = number.toLong(),
    )

    private fun chapter(
        id: Long,
        name: String,
        number: Double,
        order: Long,
        scanlator: String?,
    ) = LibraryChapter(
        id = id,
        mangaId = 1L,
        url = "/$id",
        name = name,
        scanlator = scanlator,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = 0L,
        chapterNumber = number,
        sourceOrder = order,
        lastModifiedAt = 0L,
        version = 0L,
        memoJson = "{}",
    )
}
