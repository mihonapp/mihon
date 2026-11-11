package mihon.data.protectedfolder

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.manga.MangaRepositoryImpl
import java.util.Properties
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ProtectedMangaQueriesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private lateinit var mangaRepository: MangaRepositoryImpl
    private lateinit var protectedRepository: ProtectedMangaRepositoryImpl

    private val addedAt = Instant.fromEpochSeconds(1_000_000)

    @BeforeEach
    fun beforeEach() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        Database.Schema.awaitCreate(driver)
        database = DatabaseBindings.providesDatabase(driver)
        mangaRepository = MangaRepositoryImpl(database)
        protectedRepository = ProtectedMangaRepositoryImpl(database)
    }

    @AfterEach
    fun afterEach() {
        driver.close()
    }

    @Test
    fun `A protected entry leaves the library and shows in the protected folder only`() = runTest {
        val visible = insertManga("Visible")
        val hidden = insertManga("Hidden")

        protectedRepository.setProtected(listOf(hidden), protected = true)

        mangaRepository.getLibraryManga().map { it.id } shouldContainExactly listOf(visible)
        protectedRepository.getProtectedLibraryManga().map { it.id } shouldContainExactly listOf(hidden)
        protectedRepository.getProtectedMangaIds() shouldBe setOf(hidden)
    }

    @Test
    fun `Taking an entry out of the folder returns it to the library`() = runTest {
        val manga = insertManga("Manga")
        protectedRepository.setProtected(listOf(manga), protected = true)

        protectedRepository.setProtected(listOf(manga), protected = false)

        mangaRepository.getLibraryManga().map { it.id } shouldContainExactly listOf(manga)
        protectedRepository.getProtectedLibraryManga().shouldBeEmpty()
    }

    @Test
    fun `Protecting an entry twice keeps one membership`() = runTest {
        val manga = insertManga("Manga")

        protectedRepository.setProtected(listOf(manga), protected = true)
        protectedRepository.setProtected(listOf(manga), protected = true)

        protectedRepository.getProtectedManga().map { it.id } shouldContainExactly listOf(manga)
    }

    @Test
    fun `Favorites, source counts and duplicates leave protected entries out`() = runTest {
        val visible = insertManga("Shared title", source = 1)
        val hidden = insertManga("Shared title too", source = 2)
        val other = insertManga("Unrelated", source = 1)
        protectedRepository.setProtected(listOf(hidden), protected = true)

        mangaRepository.getFavorites().map { it.id } shouldContainExactlyInAnyOrder listOf(visible, other)
        database.mangaQueries.getFavoriteBySourceId(2).awaitAsList().shouldBeEmpty()
        database.mangaQueries.getSourceIdWithFavoriteCount().awaitAsList()
            .map { it.source_id to it.count } shouldContainExactly listOf(1L to 2L)
        mangaRepository.getDuplicateLibraryManga(other, "shared title")
            .map { it.manga.id } shouldContainExactly listOf(visible)
    }

    @Test
    fun `A protected entry outside the library is not listed as a read entry`() = runTest {
        val visible = insertManga("Visible", favorite = false)
        val hidden = insertManga("Hidden", favorite = false)
        insertChapter(visible, read = true)
        insertChapter(hidden, read = true)
        protectedRepository.setProtected(listOf(hidden), protected = true)

        mangaRepository.getReadMangaNotInLibrary().map { it.id } shouldContainExactly listOf(visible)
        database.mangaQueries.getSourceIdsWithNonLibraryManga().awaitAsList()
            .map { it.manga_count } shouldContainExactly listOf(1L)
    }

    @Test
    fun `Updates leave protected entries out`() = runTest {
        val visible = insertManga("Visible")
        val hidden = insertManga("Hidden")
        insertChapter(visible)
        insertChapter(hidden)
        protectedRepository.setProtected(listOf(hidden), protected = true)

        database.updatesViewQueries.getUnreadUpdates(after = Instant.DISTANT_PAST, limit = 10).awaitAsList()
            .map { it.mangaId } shouldContainExactly listOf(visible)
        database.updatesViewQueries
            .getRecentUpdatesWithFilters(
                after = Instant.DISTANT_PAST,
                read = null,
                started = null,
                bookmarked = null,
                hideExcludedScanlators = 0,
                includedEmpty = true,
                includedCategories = emptyList(),
                excludedEmpty = true,
                excludedCategories = emptyList(),
                limit = 10,
            )
            .awaitAsList()
            .map { it.mangaId } shouldContainExactly listOf(visible)
    }

    @Test
    fun `History and read duration leave protected entries out`() = runTest {
        val visible = insertManga("Visible")
        val hidden = insertManga("Hidden")
        val visibleChapter = insertChapter(visible)
        val hiddenChapter = insertChapter(hidden)
        database.historyQueries.upsert(chapterId = visibleChapter, readAt = addedAt + 2.days, readDuration = 100)
        // The protected entry was read last, so it would be the one to resume
        database.historyQueries.upsert(chapterId = hiddenChapter, readAt = addedAt + 3.days, readDuration = 40)
        protectedRepository.setProtected(listOf(hidden), protected = true)

        database.historyViewQueries.history("").awaitAsList().map { it.mangaId } shouldContainExactly listOf(visible)
        database.historyViewQueries.history("hidden").awaitAsList().shouldBeEmpty()
        database.historyViewQueries.getLatestHistory().awaitAsOneOrNull()?.mangaId shouldBe visible
        database.historyQueries.getReadDuration().awaitAsOne() shouldBe 100
    }

    @Test
    fun `Wiping the folder deletes its entries with their data and nothing else`() = runTest {
        val visible = insertManga("Visible")
        val hidden = insertManga("Hidden")
        val unfavorited = insertManga("Hidden and removed", favorite = false)
        val visibleChapter = insertChapter(visible)
        val hiddenChapter = insertChapter(hidden)
        database.historyQueries.upsert(chapterId = visibleChapter, readAt = addedAt + 2.days, readDuration = 100)
        database.historyQueries.upsert(chapterId = hiddenChapter, readAt = addedAt + 3.days, readDuration = 40)
        protectedRepository.setProtected(listOf(hidden, unfavorited), protected = true)

        protectedRepository.deleteProtectedManga()

        allMangaIds() shouldContainExactly listOf(visible)
        database.chapterQueries.getChaptersByMangaId(hidden, applyScanlatorFilter = 0).awaitAsList().shouldBeEmpty()
        database.historyQueries.getHistoryByMangaId(hidden).awaitAsList().shouldBeEmpty()
        database.historyQueries.getHistoryByMangaId(visible).awaitAsList().size shouldBe 1
        protectedRepository.getProtectedMangaIds().shouldBeEmpty()
    }

    @Test
    fun `A wipe that fails part way deletes nothing`() = runTest {
        val first = insertManga("First")
        val second = insertManga("Second")
        insertChapter(first)
        insertChapter(second)
        protectedRepository.setProtected(listOf(first, second), protected = true)
        driver.execute(
            identifier = null,
            sql = """
                CREATE TEMP TRIGGER fail_wipe BEFORE DELETE ON manga
                WHEN old.id = $second
                BEGIN SELECT RAISE(ABORT, 'wipe failed'); END
            """.trimIndent(),
            parameters = 0,
        )

        shouldThrowAny { protectedRepository.deleteProtectedManga() }

        protectedRepository.getProtectedManga().map { it.id } shouldContainExactlyInAnyOrder listOf(first, second)
        database.chapterQueries.getChaptersByMangaId(first, applyScanlatorFilter = 0).awaitAsList().size shouldBe 1
        protectedRepository.getProtectedMangaIds() shouldBe setOf(first, second)
    }

    private suspend fun allMangaIds(): List<Long> {
        return mangaRepository.getFavorites().map { it.id } + mangaRepository.getReadMangaNotInLibrary().map { it.id }
    }

    private suspend fun insertManga(title: String, favorite: Boolean = true, source: Long = 1): Long {
        return database.mangaQueries
            .insertReturningId(
                sourceId = source,
                remoteUrl = "/manga/$title",
                remoteTitle = title,
                remoteAuthor = null,
                remoteArtist = null,
                remoteDescription = null,
                remoteGenre = null,
                remoteStatus = 0,
                remoteCover = null,
                remoteUpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                remoteMemo = JsonObject(emptyMap()),
                userFavoriteAt = addedAt.takeIf { favorite },
                userNotes = "",
                userReaderFlags = 0,
                userChapterFlags = 0,
                stateChapterLastUpdate = null,
                stateChapterNextUpdate = null,
                stateChapterFetchInterval = 0,
                stateCoverLastModified = null,
                stateInitialized = true,
            )
            .awaitAsOne()
    }

    private suspend fun insertChapter(mangaId: Long, read: Boolean = false): Long {
        return database.chapterQueries
            .insertReturningId(
                mangaId = mangaId,
                remoteUrl = "/chapter/1",
                remoteName = "Chapter 1",
                remoteScanlator = null,
                userRead = read,
                userBookmark = false,
                userLastPageRead = 0,
                remoteChapterNumber = 1.0,
                remoteOrder = 0,
                stateDateFetch = addedAt + 1.days,
                remoteDateUpload = addedAt + 1.days,
                remoteMemo = JsonObject(emptyMap()),
            )
            .awaitAsOne()
    }
}
