package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class LargeLibraryWorkflowTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `ten thousand manga retain real filters and sorting without repeated full snapshots`(): Unit = runBlocking {
        val timings = linkedMapOf<String, Long>()
        val snapshots = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        DesktopLibraryDatabaseFactory.open(directory.resolve("library.db")).use { library ->
            var started = System.nanoTime()
            library.transaction {
                repeat(10_000) { offset ->
                    val i = offset + 1
                    val id =
                        insertManga(
                            MangaRecord(
                                sourceId = 1,
                                url = "/$i",
                                title = "分组%02d 漫画%05d".format(offset / 100, i),
                                dateAdded = 20_000L - i,
                                author = "Author ${i % 20}",
                            ),
                        )
                    val chapter = insertChapter(
                        ChapterRecord(
                            mangaId = id,
                            url = "/$i/1",
                            name = "Chapter 1",
                            read =
                            i % 2 == 0,
                            bookmark = i % 10 == 0,
                            dateFetch = i * 10L,
                            chapterNumber = 1.0,
                        ),
                    )
                    if (i % 25 == 0) insertLocalChapter(LocalChapterRecord(chapter, "$chapter.cbz", "archive", 1, 1))
                    if (i % 2 == 0) upsertHistory(HistoryRecord(chapter, i * 100L))
                }
            }
            timings["seed_10000_ms"] = (System.nanoTime() - started) / 1_000_000
            if (System.getenv("MIHON_W_WRITE_LARGE_LIBRARY_FIXTURE") == "1") {
                val fixture = Path.of("build", "verification", "large-library", "library-10000.tachibk")
                Files.createDirectories(fixture.parent)
                mihon.desktop.library.backup.AndroidBackupExporter(library).export(fixture)
            }
            val measured = object : LibraryRepository by library {
                override fun librarySnapshot(categoryId: Long?): List<LibraryManga> {
                    snapshots.incrementAndGet()
                    return library.librarySnapshot(categoryId)
                }
            }
            started = System.nanoTime()
            val presenter = LibraryPresenter(measured, scope, readerLibrary = library, mutationPort = library)
            suspend fun await(
                predicate: (LibraryUiState) -> Boolean,
            ) = withTimeout(30_000) { presenter.state.first(predicate) }
            try {
                await { !it.loading && it.items.size == 10_000 }
                timings["initial_library_ms"] = (System.nanoTime() - started) / 1_000_000
                started = System.nanoTime()
                presenter.setQuery("分组01")
                await { it.query == "分组01" && it.items.size == 100 }
                timings["search_ms"] = (System.nanoTime() - started) / 1_000_000
                val repeatedSearches = (2..31).map { group ->
                    val query = "分组%02d".format(group)
                    val searchStarted = System.nanoTime()
                    presenter.setQuery(query)
                    await { it.query == query && it.items.size == 100 }
                    (System.nanoTime() - searchStarted) / 1_000_000
                }.sorted()
                timings["search_30_samples_p95_ms"] = repeatedSearches[28]
                timings["search_30_samples_max_ms"] = repeatedSearches.last()
                presenter.setQuery("分组01")
                await { it.query == "分组01" && it.items.size == 100 }
                presenter.setFilterState(LibraryFilterState(bookmarked = TriStateFilter.Include))
                await { it.filterState.bookmarked == TriStateFilter.Include }.items.size shouldBe 10
                presenter.setFilterState(LibraryFilterState(downloaded = TriStateFilter.Include))
                await { it.filterState.downloaded == TriStateFilter.Include }.items.size shouldBe 4
                presenter.setFilterState(LibraryFilterState())
                presenter.setSortState(LibrarySortState(LibrarySortMode.DateAdded, true))
                await {
                    it.sortState.mode == LibrarySortMode.DateAdded && it.items.size == 100
                }.items.first().id shouldBe
                    200
                presenter.setSortState(LibrarySortState(LibrarySortMode.LastRead, false))
                await { it.sortState.mode == LibrarySortMode.LastRead }.items.first().id shouldBe 200
                presenter.selectAll()
                await { it.selectionState.selectedMangaIds.size == 100 }
                started = System.nanoTime()
                presenter.batchRemoveFromLibrary()
                await { it.items.isEmpty() }
                timings["remove_100_ms"] = (System.nanoTime() - started) / 1_000_000
                snapshots.get() shouldBe 1
                library.librarySnapshot().size shouldBe 9_900
                library.allMangaSnapshot().size shouldBe 10_000
            } finally {
                presenter.close()
                scope.cancel()
                val destination = Path.of("build", "reports", "suwayomi", "large-library.txt")
                Files.createDirectories(destination.parent)
                Files.writeString(
                    destination,
                    timings.entries.joinToString("\n") { "${it.key}=${it.value}" } +
                        "\nfull_snapshots=${snapshots.get()}\n",
                )
            }
        }
    }
}
