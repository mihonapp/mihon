package mihon.feature.protectedfolder

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository
import mihon.domain.protectedfolder.service.ProtectedFolderPin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException

class WipeProtectedFolderTest {

    private val repository = mockk<ProtectedMangaRepository>()
    private val pin = mockk<ProtectedFolderPin>(relaxUnitFun = true)
    private val session = mockk<ProtectedFolderSession>(relaxUnitFun = true)
    private val downloadManager = mockk<DownloadManager>()
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val sourceManager = mockk<SourceManager>()

    private val first = Manga.create().copy(id = 1, source = 10, title = "First")
    private val second = Manga.create().copy(id = 2, source = 10, title = "Second")

    private lateinit var wipeProtectedFolder: WipeProtectedFolder

    @BeforeEach
    fun beforeEach() {
        coEvery { repository.getProtectedManga() } returns listOf(first, second)
        coEvery { repository.deleteProtectedManga() } just Runs
        coEvery { sourceManager.getOrStub(any()) } returns mockk()
        coEvery { downloadManager.deleteManga(any(), any(), any()) } just Runs
        wipeProtectedFolder = WipeProtectedFolder(repository, pin, session, downloadManager, coverCache, sourceManager)
    }

    @Test
    fun `A wipe deletes the entries, then the PIN, then the files`() = runTest {
        wipeProtectedFolder.await() shouldBe WipeProtectedFolder.Result.Success

        coVerifyOrder {
            repository.deleteProtectedManga()
            pin.clear()
            session.lock()
            downloadManager.deleteManga(first, any(), any())
            downloadManager.deleteManga(second, any(), any())
        }
        verify { coverCache.deleteFromCache(first, true) }
        verify { coverCache.deleteFromCache(second, true) }
    }

    @Test
    fun `A wipe that fails to delete the entries changes nothing`() = runTest {
        coEvery { repository.deleteProtectedManga() } throws IllegalStateException("database is locked")

        wipeProtectedFolder.await() shouldBe WipeProtectedFolder.Result.Failed

        verify(exactly = 0) { pin.clear() }
        verify(exactly = 0) { session.lock() }
        coVerify(exactly = 0) { downloadManager.deleteManga(any(), any(), any()) }
        verify(exactly = 0) { coverCache.deleteFromCache(any(), any()) }
    }

    @Test
    fun `A wipe that cannot read the folder changes nothing`() = runTest {
        coEvery { repository.getProtectedManga() } throws IllegalStateException("database is locked")

        wipeProtectedFolder.await() shouldBe WipeProtectedFolder.Result.Failed

        coVerify(exactly = 0) { repository.deleteProtectedManga() }
        verify(exactly = 0) { pin.clear() }
        coVerify(exactly = 0) { downloadManager.deleteManga(any(), any(), any()) }
    }

    @Test
    fun `Files that cannot be deleted are reported once the folder is wiped`() = runTest {
        coEvery { downloadManager.deleteManga(first, any(), any()) } throws IOException("read-only storage")

        wipeProtectedFolder.await() shouldBe WipeProtectedFolder.Result.FilesLeft(1)

        verify { pin.clear() }
        coVerify { downloadManager.deleteManga(second, any(), any()) }
    }

    @Test
    fun `Wiping an empty folder still removes the PIN`() = runTest {
        coEvery { repository.getProtectedManga() } returns emptyList()

        wipeProtectedFolder.await() shouldBe WipeProtectedFolder.Result.Success

        verify { pin.clear() }
        coVerify(exactly = 0) { downloadManager.deleteManga(any(), any(), any()) }
    }
}
