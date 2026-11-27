package tachiyomi.data.anilist

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Chapter
import tachiyomi.data.Database
import tachiyomi.data.History
import tachiyomi.data.InstantColumnAdapter
import tachiyomi.data.IntColumnAdapter
import tachiyomi.data.Manga
import tachiyomi.data.Manga_track
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.model.AnilistWishlistMappingWithMangaIdentity
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository.InsertResult

class AnilistWishlistMappingDatabaseTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: Database
    private lateinit var repository: AnilistWishlistRepositoryImpl

    @BeforeEach
    fun setUp() {
        driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.Memory,
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        database = Database(
            driver = driver,
            historyAdapter = History.Adapter(read_atAdapter = InstantColumnAdapter),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )
        repository = AnilistWishlistRepositoryImpl(database)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `primary key rejects duplicate AniList media ID inserts`() = runTest {
        insertManga(1)
        insertManga(2)
        insertMapping(10, 1)

        val failure = runCatching { insertMapping(10, 2) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `unique constraint rejects duplicate manga ID inserts`() = runTest {
        insertManga(1)
        insertMapping(10, 1)

        val failure = runCatching { insertMapping(11, 1) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `failed mapping transaction rolls back earlier inserts`() = runTest {
        insertManga(1)
        insertManga(2)
        insertMapping(10, 1)

        val failure = runCatching {
            database.transaction {
                insertMapping(11, 2)
                insertMapping(12, 1)
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `repository insert conflict on a manga leaves its existing mapping intact`() = runTest {
        insertManga(1)
        repository.upsert(AnilistWishlistMapping(10, 1))

        val failure = runCatching {
            repository.upsert(AnilistWishlistMapping(11, 1))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `new mapping returns inserted`() = runTest {
        insertManga(1)

        assertEquals(InsertResult.Inserted, repository.insertIfAbsent(AnilistWishlistMapping(10, 1)))
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `mapping snapshot joins manga source and URL without exposing local manga ID`() = runTest {
        insertManga(17)
        repository.upsert(AnilistWishlistMapping(10, 17))

        assertEquals(
            listOf(AnilistWishlistMappingWithMangaIdentity(10, 17, "url-17")),
            repository.getAllWithMangaIdentity(),
        )
    }

    @Test
    fun `safe insert reports invalid IDs and missing manga without touching mappings`() = runTest {
        insertManga(1)

        assertEquals(InsertResult.Invalid, repository.insertIfAbsent(AnilistWishlistMapping(0, 1)))
        assertEquals(InsertResult.Invalid, repository.insertIfAbsent(AnilistWishlistMapping(10, 999)))
        assertEquals(emptyList<AnilistWishlistMapping>(), repository.getAll())
    }

    @Test
    fun `exact mapping returns already mapped without changing the row`() = runTest {
        insertManga(1)
        repository.insertIfAbsent(AnilistWishlistMapping(10, 1))

        assertEquals(InsertResult.AlreadyMapped, repository.insertIfAbsent(AnilistWishlistMapping(10, 1)))
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `media ID conflict returns conflict and preserves the existing mapping`() = runTest {
        insertManga(1)
        insertManga(2)
        repository.insertIfAbsent(AnilistWishlistMapping(10, 1))

        assertEquals(InsertResult.Conflict, repository.insertIfAbsent(AnilistWishlistMapping(10, 2)))
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `manga ID conflict returns conflict and preserves the existing mapping`() = runTest {
        insertManga(1)
        repository.insertIfAbsent(AnilistWishlistMapping(10, 1))

        assertEquals(InsertResult.Conflict, repository.insertIfAbsent(AnilistWishlistMapping(11, 1)))
        assertEquals(listOf(AnilistWishlistMapping(10, 1)), repository.getAll())
    }

    @Test
    fun `existing upsert behavior still replaces the manga for an existing media ID`() = runTest {
        insertManga(1)
        insertManga(2)
        repository.upsert(AnilistWishlistMapping(10, 1))

        repository.upsert(AnilistWishlistMapping(10, 2))

        assertEquals(listOf(AnilistWishlistMapping(10, 2)), repository.getAll())
    }

    @Test
    fun `concurrent inserts preserve both unique identities`() = runTest {
        insertManga(1)
        insertManga(2)
        insertManga(3)

        val outcomes = coroutineScope {
            listOf(
                async(Dispatchers.Default) { repository.insertIfAbsent(AnilistWishlistMapping(100, 1)) },
                async(Dispatchers.Default) { repository.insertIfAbsent(AnilistWishlistMapping(101, 1)) },
                async(Dispatchers.Default) { repository.insertIfAbsent(AnilistWishlistMapping(200, 2)) },
                async(Dispatchers.Default) { repository.insertIfAbsent(AnilistWishlistMapping(200, 3)) },
            ).awaitAll()
        }

        assertEquals(2, outcomes.count { it == InsertResult.Inserted })
        assertEquals(2, outcomes.count { it == InsertResult.Conflict })
        val mappings = repository.getAll()
        assertEquals(2, mappings.size)
        assertEquals(mappings.size, mappings.map { it.anilistMediaId }.toSet().size)
        assertEquals(mappings.size, mappings.map { it.mangaId }.toSet().size)
    }

    private suspend fun insertManga(id: Long) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO manga (
                    id, source_id, remote_url, remote_title, remote_genre, remote_status,
                    remote_update_strategy, remote_memo, user_notes, user_reader_flags,
                    user_chapter_flags, state_chapter_fetch_interval, state_initialized
                ) VALUES ($id, $id, 'url-$id', 'title-$id', '', 0, 0, X'', '', 0, 0, 0, 0)
            """.trimIndent(),
            parameters = 0,
        ).await()
    }

    private suspend fun insertMapping(mediaId: Long, mangaId: Long) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO anilist_wishlist_mapping(anilist_media_id, manga_id) VALUES ($mediaId, $mangaId)",
            parameters = 0,
        ).await()
    }
}
