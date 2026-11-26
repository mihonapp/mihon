package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.createWishlistBackup
import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlist
import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlistMapping
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupWishlistCategory
import eu.kanade.tachiyomi.data.backup.restore.WishlistRestoreReport
import eu.kanade.tachiyomi.data.backup.restore.planWishlistMappingRestore
import eu.kanade.tachiyomi.data.backup.restore.processWishlistMappings
import eu.kanade.tachiyomi.data.backup.restore.resolveWishlistCategory
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.shouldRestoreWishlistMappings
import eu.kanade.tachiyomi.data.backup.shouldNotifySkippedWishlistMappings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.model.AnilistWishlistMappingWithMangaIdentity
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository.InsertResult
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

class AnilistWishlistBackupRestoreTest {

    @Test
    fun `backup includes only exact emitted source and URL identities`() {
        val snapshot = listOf(
            AnilistWishlistMappingWithMangaIdentity(1, 10, "/same"),
            AnilistWishlistMappingWithMangaIdentity(2, 10, "/not-emitted"),
            AnilistWishlistMappingWithMangaIdentity(3, 11, "/same"),
        )

        val result = createWishlistBackup(snapshot, setOf(10L to "/same"), null)

        assertEquals(listOf(BackupAnilistWishlistMapping(1, 10, "/same")), result.metadata?.mappings)
        assertEquals(2, result.skippedMappings)
    }

    @Test
    fun `backup inclusion and skipped count use the same start snapshot`() {
        val startSnapshot = listOf(
            AnilistWishlistMappingWithMangaIdentity(1, 10, "/present-at-start"),
            AnilistWishlistMappingWithMangaIdentity(2, 20, "/present-at-start"),
        )

        val result = createWishlistBackup(startSnapshot, setOf(10L to "/present-at-start"), null)

        assertEquals(1, result.metadata?.mappings?.size)
        assertEquals(1, result.skippedMappings)
    }

    @Test
    fun `backup without applicable mappings omits wishlist section`() {
        val result = createWishlistBackup(
            listOf(AnilistWishlistMappingWithMangaIdentity(1, 10, "/not-emitted")),
            emptySet(),
            null,
        )

        assertNull(result.metadata)
        assertEquals(1, result.skippedMappings)
    }

    @Test
    fun `mapping restore collapses identical duplicates and skips invalid or ambiguous records`() {
        val identical = BackupAnilistWishlistMapping(1, 10, "/one")
        val mappings = listOf(
            identical,
            identical,
            BackupAnilistWishlistMapping(0, 20, "/invalid"),
            BackupAnilistWishlistMapping(2, 30, "/shared"),
            BackupAnilistWishlistMapping(3, 30, "/shared"),
            BackupAnilistWishlistMapping(4, 40, "/ambiguous-a"),
            BackupAnilistWishlistMapping(4, 41, "/ambiguous-b"),
        )

        val plan = planWishlistMappingRestore(BackupAnilistWishlist(mappings))

        assertEquals(listOf(identical), plan.mappings)
        assertEquals(5, plan.skipped)
    }

    @Test
    fun `invalid mappings are skipped before manga resolution or repository insertion`() = runTest {
        val report = WishlistRestoreReport()
        var repositoryCalls = 0
        val wishlist = BackupAnilistWishlist(
            listOf(
                BackupAnilistWishlistMapping(0, 10, "/invalid-id"),
                BackupAnilistWishlistMapping(2, 10, " "),
            ),
        )

        processWishlistMappings(
            wishlist,
            report,
            resolveMangaId = { _, _ -> error("invalid mapping should not resolve manga") },
            insertIfAbsent = {
                repositoryCalls++
                InsertResult.Inserted
            },
        )

        assertEquals(2, report.skipped)
        assertEquals(0, repositoryCalls)
    }

    @Test
    fun `library restore option gates mappings and an absent old backup section is a no-op`() {
        val wishlist = BackupAnilistWishlist(listOf(BackupAnilistWishlistMapping(1, 10, "/manga")))

        assertTrue(shouldRestoreWishlistMappings(true, wishlist))
        assertFalse(shouldRestoreWishlistMappings(false, wishlist))
        assertFalse(shouldRestoreWishlistMappings(true, null))
    }

    @Test
    fun `mapping restore counts inserted exact existing and conflicting outcomes`() = runTest {
        val report = WishlistRestoreReport()
        val outcomes = listOf(
            InsertResult.Inserted,
            InsertResult.AlreadyMapped,
            InsertResult.Conflict,
            InsertResult.Invalid,
        )
        var index = 0

        processWishlistMappings(
            BackupAnilistWishlist(List(4) { BackupAnilistWishlistMapping(it + 1L, 10, "/m$it") }),
            report,
            resolveMangaId = { _, _ -> 99 },
            insertIfAbsent = { _: AnilistWishlistMapping -> outcomes[index++] },
        )

        assertEquals(2, report.restored)
        assertEquals(1, report.conflicted)
        assertEquals(1, report.skipped)
        assertTrue(report.complete)
    }

    @Test
    fun `missing manga is skipped and later mappings continue`() = runTest {
        val report = WishlistRestoreReport()
        val wishlist = BackupAnilistWishlist(
            listOf(
                BackupAnilistWishlistMapping(1, 10, "/missing"),
                BackupAnilistWishlistMapping(2, 10, "/present"),
            ),
        )

        processWishlistMappings(
            wishlist,
            report,
            resolveMangaId = { _, url -> if (url == "/present") 5 else null },
            insertIfAbsent = { InsertResult.Inserted },
        )

        assertEquals(1, report.restored)
        assertEquals(1, report.skipped)
        assertTrue(report.complete)
    }

    @Test
    fun `partial failure keeps completed counts and leaves remaining records uncounted`() = runTest {
        val report = WishlistRestoreReport()
        var calls = 0

        try {
            processWishlistMappings(
                BackupAnilistWishlist(List(3) { BackupAnilistWishlistMapping(it + 1L, 10, "/m$it") }),
                report,
                resolveMangaId = { _, _ -> 7 },
                insertIfAbsent = {
                    if (calls++ == 0) InsertResult.Inserted else error("database failure")
                },
            )
        } catch (_: IllegalStateException) {
            // The operation must retain the committed first result and propagate the later failure.
        }

        assertEquals(1, report.restored)
        assertEquals(0, report.skipped)
        assertEquals(0, report.conflicted)
        assertFalse(report.complete)
    }

    @Test
    fun `cancellation propagates and marks mapping report incomplete`() = runTest {
        val report = WishlistRestoreReport()
        var cancelled = false
        try {
            processWishlistMappings(
                BackupAnilistWishlist(listOf(BackupAnilistWishlistMapping(1, 10, "/manga"))),
                report,
                resolveMangaId = { _, _ -> 1 },
                insertIfAbsent = { throw CancellationException("stop") },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertFalse(report.complete)
        assertEquals(0, report.restored)
    }

    @Test
    fun `category restore uses explicit backup reference mapping`() {
        val reference = BackupWishlistCategory(42, "Wishlist")
        val categories = listOf(BackupCategory(name = "Wishlist", id = 42))

        val resolved = resolveWishlistCategory(reference, true, true, categories, mapOf(42L to 7L), emptyList())
        val unresolved = resolveWishlistCategory(reference, true, true, categories, emptyMap(), emptyList())

        assertEquals(7L, resolved.destinationId)
        assertFalse(resolved.warning)
        assertNull(unresolved.destinationId)
        assertTrue(unresolved.warning)
    }

    @Test
    fun `category restore disabled requires one exact name match`() {
        val reference = BackupWishlistCategory(null, "Wishlist")
        val unique = listOf(Category(4, "Wishlist", 0, 0))
        val duplicate = unique + Category(5, "Wishlist", 1, 0)

        assertEquals(4L, resolveWishlistCategory(reference, true, false, emptyList(), emptyMap(), unique).destinationId)
        assertTrue(resolveWishlistCategory(reference, true, false, emptyList(), emptyMap(), emptyList()).warning)
        assertTrue(resolveWishlistCategory(reference, true, false, emptyList(), emptyMap(), duplicate).warning)
        assertTrue(
            resolveWishlistCategory(
                BackupWishlistCategory(null, "Renamed"),
                true,
                false,
                emptyList(),
                emptyMap(),
                unique,
            ).warning,
        )
        val appSettingsDisabled = resolveWishlistCategory(reference, false, true, emptyList(), emptyMap(), unique)
        assertNull(appSettingsDisabled.destinationId)
        assertFalse(appSettingsDisabled.warning)
    }

    @Test
    fun `categories enabled creates a missing category through normal restore and maps its backup reference`() =
        runTest {
            val categories = mutableListOf<Category>()
            val categoryRepository = mockk<CategoryRepository>()
            coEvery { categoryRepository.getAll() } coAnswers { categories.toList() }
            coEvery { categoryRepository.insertAll(any()) } coAnswers {
                val newCategories = firstArg<List<NewCategory>>()
                val firstId = (categories.maxOfOrNull { it.id } ?: 0L) + 1
                categories += newCategories.mapIndexed { index, category ->
                    Category(firstId + index, category.name, index.toLong(), category.flags)
                }
            }
            val restorer = CategoriesRestorer(
                categoryRepository,
                GetCategories(categoryRepository),
                mockk<LibraryPreferences>(relaxed = true),
            )

            val mapping = restorer(listOf(BackupCategory(name = "Wishlist", id = 42)))

            assertEquals(1, categories.size)
            assertEquals("Wishlist", categories.single().name)
            assertEquals(1L, mapping[42L])
        }

    @Test
    fun `categories enabled leaves duplicate exact-name destinations unresolved`() = runTest {
        val categories = mutableListOf(
            Category(1, "Wishlist", 0, 0),
            Category(2, "Wishlist", 1, 0),
        )
        val categoryRepository = mockk<CategoryRepository>()
        coEvery { categoryRepository.getAll() } coAnswers { categories.toList() }
        coEvery { categoryRepository.insertAll(any()) } returns Unit
        val restorer = CategoriesRestorer(
            categoryRepository,
            GetCategories(categoryRepository),
            mockk<LibraryPreferences>(relaxed = true),
        )

        val mapping = restorer(listOf(BackupCategory(name = "Wishlist", id = 42)))

        assertTrue(mapping.isEmpty())
        assertEquals(2, categories.size)
    }

    @Test
    fun `automatic backup warning is emitted only for a positive skipped count`() {
        assertFalse(shouldNotifySkippedWishlistMappings(0))
        assertTrue(shouldNotifySkippedWishlistMappings(1))
    }
}
