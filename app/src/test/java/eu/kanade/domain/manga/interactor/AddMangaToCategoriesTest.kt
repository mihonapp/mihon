package eu.kanade.domain.manga.interactor

import eu.kanade.domain.track.interactor.AddTracks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Instant

class AddMangaToCategoriesTest {
    private val mangas = mutableMapOf<Long, Manga>()
    private val categories = mutableMapOf<Long, List<Long>>()
    private val initialized = mutableListOf<Long>()
    private var rejectUpdates = false
    private val repository = object : MangaRepository by mockk() {
        override suspend fun getMangaById(id: Long) = mangas.getValue(id)

        override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
            categories[mangaId] = categoryIds
        }

        override suspend fun update(update: MangaUpdate): Boolean {
            if (rejectUpdates) return false
            mangas[update.id] = mangas.getValue(update.id).copy(favoriteAt = update.favoriteAt)
            return true
        }
    }
    private val categoryRepository = object : CategoryRepository by mockk() {
        override suspend fun getCategoriesByMangaId(mangaId: Long) = categories[mangaId].orEmpty()
            .map { Category(it, "Category $it", it, 0) }
    }
    private val defaultFlags = mockk<SetMangaDefaultChapterFlags> {
        coEvery { await(any()) } coAnswers {
            initialized.add(firstArg<Manga>().id)
            Unit
        }
    }
    private val tracks = mockk<AddTracks> {
        coEvery { bindEnhancedTrackers(any(), any()) } returns Unit
    }
    private val sources = mockk<SourceManager> {
        coEvery { getOrStub(any()) } returns mockk()
    }
    private val adder = AddMangaToCategories(repository, categoryRepository, defaultFlags, tracks, sources)

    @Test
    fun `adds every selected manga to the library and chosen category`() = runBlocking {
        mangas[11] = Manga.create().copy(id = 11, title = "First")
        mangas[22] = Manga.create().copy(id = 22, title = "Second")

        assertEquals(2, adder.await(listOf(11, 22), listOf(7)))

        assertNotNull(mangas.getValue(11).favoriteAt)
        assertNotNull(mangas.getValue(22).favoriteAt)
        assertEquals(listOf(7L), categories[11])
        assertEquals(listOf(7L), categories[22])
        assertEquals(listOf(11L, 22L), initialized)
    }

    @Test
    fun `preserves existing favorites and their other categories`() = runBlocking {
        val addedAt = Instant.fromEpochMilliseconds(1234)
        mangas[11] = Manga.create().copy(id = 11, favoriteAt = addedAt, chapterFlags = 123)
        categories[11] = listOf(3)

        adder.await(listOf(11), listOf(7))

        assertEquals(addedAt, mangas.getValue(11).favoriteAt)
        assertEquals(123L, mangas.getValue(11).chapterFlags)
        assertEquals(listOf(3L, 7L), categories[11])
        assertEquals(emptyList<Long>(), initialized)
    }

    @Test
    fun `deduplicates selected mangas and excludes the system category`() = runBlocking {
        mangas[11] = Manga.create().copy(id = 11)

        assertEquals(1, adder.await(listOf(11, 11), listOf(0, 7, 7)))

        assertEquals(listOf(7L), categories[11])
        assertEquals(listOf(11L), initialized)
    }

    @Test
    fun `does not report success when saving a favorite fails`() {
        mangas[11] = Manga.create().copy(id = 11)
        rejectUpdates = true

        assertThrows<IllegalStateException> {
            runBlocking { adder.await(listOf(11), listOf(7)) }
        }
    }
}
