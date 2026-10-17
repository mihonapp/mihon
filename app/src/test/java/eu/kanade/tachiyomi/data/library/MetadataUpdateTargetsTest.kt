package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class MetadataUpdateTargetsTest {

    @Test
    fun `missing metadata refresh selects only uninitialized titles in library order`() {
        val first = libraryManga(Manga.create().copy(id = 1, source = 10))
        val initialized = libraryManga(Manga.create().copy(id = 2, source = 10, initialized = true))
        val last = libraryManga(Manga.create().copy(id = 3, source = 20))

        val targets = getMetadataUpdateTargets(listOf(first, initialized, last), onlyMissing = true)

        assertEquals(listOf(first, last), targets)
    }

    @Test
    fun `full metadata refresh still selects all library titles`() {
        val library = listOf(
            libraryManga(Manga.create().copy(id = 1)),
            libraryManga(Manga.create().copy(id = 2, initialized = true)),
        )

        assertEquals(library, getMetadataUpdateTargets(library, onlyMissing = false))
    }

    @Test
    fun `initialized titles with blank optional metadata are not refreshed`() {
        val initialized = libraryManga(
            Manga.create().copy(
                initialized = true,
                author = "",
                artist = null,
                description = "",
                genre = emptyList(),
                thumbnailUrl = null,
            ),
        )

        assertTrue(getMetadataUpdateTargets(listOf(initialized), onlyMissing = true).isEmpty())
    }

    @Test
    fun `uninitialized titles are refreshed even if some metadata is already present`() {
        val uninitialized = libraryManga(
            Manga.create().copy(
                title = "Title from browse",
                author = "Author",
                description = "Description from browse",
                thumbnailUrl = "https://example.com/cover.jpg",
            ),
        )

        assertEquals(listOf(uninitialized), getMetadataUpdateTargets(listOf(uninitialized), onlyMissing = true))
    }

    @Test
    fun `uninitialized fetch once titles with existing chapters are eligible`() {
        val fetchOnce = libraryManga(
            Manga.create().copy(updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE),
        ).copy(totalChapters = 12, readCount = 12)

        assertEquals(listOf(fetchOnce), getMetadataUpdateTargets(listOf(fetchOnce), onlyMissing = true))
    }

    @Test
    fun `empty libraries have no metadata update targets in either mode`() {
        assertTrue(getMetadataUpdateTargets(emptyList(), onlyMissing = true).isEmpty())
        assertTrue(getMetadataUpdateTargets(emptyList(), onlyMissing = false).isEmpty())
    }

    private fun libraryManga(manga: Manga) = LibraryManga(
        manga = manga.copy(favoriteAt = Manga.UNKNOWN_FAVORITE_AT),
        categories = emptyList(),
        totalChapters = 0,
        readCount = 0,
        bookmarkCount = 0,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    )
}
