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
import mihon.desktop.library.repository.LibraryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
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

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

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
}

private class FakeLibraryRepository(
    private val libraryFlow: () -> Flow<List<LibraryManga>>,
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<LibraryManga>> = libraryFlow()
    override fun observeManga(id: Long): Flow<MangaDetails?> = error("Not used")
    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = error("Not used")
    override fun librarySnapshot(): List<LibraryManga> = error("Not used")
    override fun mangaSnapshot(id: Long): MangaDetails? = error("Not used")
    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = error("Not used")
    override fun latestImportReport(): ImportReport? = error("Not used")
}
