package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContain
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
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class DuplicateMangaDetectionTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `normalizes case whitespace and punctuation while keeping unicode letters`() {
        normalizeDuplicateTitle("The  Apothecary-Diaries!") shouldBe "theapothecarydiaries"
        normalizeDuplicateTitle("葬送 的 芙莉莲") shouldBe "葬送的芙莉莲"
        normalizeDuplicateTitle("ONE_PIECE:  Vol. 1") shouldBe "onepiecevol1"
    }

    @Test
    fun `detects duplicates only across different sources`() {
        val items = listOf(
            manga(1, "One Piece", sourceId = 100),
            manga(2, "one-piece!", sourceId = 200),
            manga(3, "One Piece", sourceId = 100),
            manga(4, "Unrelated", sourceId = 300),
        )

        val groups = findDuplicateGroups(items)

        groups.keys shouldContainExactly listOf("onepiece")
        groups.getValue("onepiece").map { it.id } shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `presenter exposes duplicate dialog for a newly added manga from another source`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(2, "one-piece!", sourceId = 200))
        repository.addManga(mangaRecord(3, "Unrelated", sourceId = 300))
        val presenter = LibraryPresenter(repository, scope)

        val state = presenter.awaitState { it.duplicateDialog != null }
        val dialog = state.duplicateDialog ?: error("expected duplicate dialog")
        dialog.target.id shouldBe 1L
        dialog.candidates.map { it.id } shouldContainExactly listOf(2L)
        dialog.candidates.single().chapterCount shouldBe 0L
        presenter.close()
    }

    @Test
    fun `presenter only offers candidates from a different source`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(2, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(3, "One Piece", sourceId = 200))
        val presenter = LibraryPresenter(repository, scope)

        val dialog = presenter.awaitState { it.duplicateDialog != null }.duplicateDialog
            ?: error("expected duplicate dialog")
        dialog.target.id shouldBe 1L
        dialog.candidates.map { it.id } shouldContainExactly listOf(3L)
        presenter.close()
    }

    @Test
    fun `add anyway and dismiss keep both manga and do not show the group again`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(2, "One Piece", sourceId = 200))
        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { it.duplicateDialog != null }

        presenter.addDuplicateAnyway()
        presenter.awaitState { it.duplicateDialog == null && it.items.size == 2 }

        val updatedRecord = repository.mangaSnapshot(1)!!.toRecord().copy(author = "Changed")
        repository.updateManga(updatedRecord)
        presenter.awaitState { state -> state.items.firstOrNull { it.id == 1L }?.author == "Changed" }
        presenter.state.value.duplicateDialog shouldBe null
        presenter.state.value.items.map { it.id } shouldContainExactly listOf(1L, 2L)
        presenter.close()
    }

    @Test
    fun `open existing selects the candidate manga`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece", sourceId = 100))
        repository.addManga(mangaRecord(2, "One Piece", sourceId = 200))
        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { it.duplicateDialog != null }

        presenter.openDuplicateManga(2)

        presenter.awaitState { it.selectedMangaId == 2L && it.duplicateDialog == null }
        presenter.close()
    }

    @Test
    fun `migrate moves chapters categories and tracking to the existing manga`() = runBlocking {
        val repository = TestLibraryRepository()
        repository.addManga(
            mangaRecord(1, "One Piece", sourceId = 100),
            chapters = listOf(
                chapterRecord(11, 1, "/a", read = false, number = 1.0),
                chapterRecord(12, 1, "/b", read = false, number = 2.0),
            ),
        )
        repository.addManga(
            mangaRecord(2, "One Piece", sourceId = 200),
            chapters = listOf(
                chapterRecord(21, 2, "/a", read = true, number = 1.0),
            ),
        )
        repository.addCategoryLink(1, 5)
        repository.addCategoryLink(2, 6)
        repository.addTracking(TrackingRecord(id = 1, mangaId = 1, trackerId = 7, remoteId = 70))
        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { it.duplicateDialog != null }

        presenter.migrateDuplicateTo(2)

        presenter.awaitState { it.items.size == 1 && it.items.single().id == 2L }
        repository.mangaSnapshot(1)?.favorite shouldBe false
        val targetChapters = repository.chapterSnapshot(2).associateBy { it.url }
        targetChapters.getValue("/a").read shouldBe true
        targetChapters.getValue("/b").mangaId shouldBe 2L
        repository.mangaCategoryLinksSnapshot().getValue(2) shouldContain 5L
        repository.findTracking(2, 7)?.remoteId shouldBe 70L
        repository.findTracking(1, 7) shouldBe null
        presenter.close()
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private fun manga(id: Long, title: String, sourceId: Long) = LibraryManga(
        id = id,
        sourceId = sourceId,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 1,
        unreadCount = 0,
    )

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
        read: Boolean,
        number: Double,
    ) = ChapterRecord(
        id = id,
        mangaId = mangaId,
        url = url,
        name = "Chapter $number",
        read = read,
        chapterNumber = number,
        sourceOrder = number.toLong(),
    )

    private fun mihon.desktop.library.model.MangaDetails.toRecord() = MangaRecord(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        favorite = favorite,
        chapterFlags = chapterFlags,
        excludedScanlatorsJson = excludedScanlatorsJson,
        memoJson = memoJson,
    )
}
