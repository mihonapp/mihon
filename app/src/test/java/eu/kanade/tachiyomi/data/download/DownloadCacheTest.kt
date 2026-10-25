package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadCacheTest {
    @TempDir
    lateinit var cacheDir: File

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns dispatcher
        mockkConstructor(AtomicFile::class)
        every { anyConstructed<AtomicFile>().baseFile } returns File(cacheDir, "dl_index_cache_v3")
        every { anyConstructed<AtomicFile>().delete() } just Runs
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `refresh lists only affected physical directory once and updates every cached alias`() = runTest(dispatcher) {
        val fixture = Fixture(this)
        val aliases = listOf(
            fixture.manga,
            fixture.manga.copy(id = 2, source = 8, title = "Other title"),
            fixture.manga.copy(id = 3, source = 7, title = "Case alias"),
            fixture.manga.copy(id = 4, source = 9, title = "Cached owner outside request"),
        )
        val aliasDir = directory("Other title")
        every { aliasDir.uri } returns fixture.mangaDir.uri
        aliases.forEachIndexed { index, owner ->
            fixture.seed(owner, if (index == 1) aliasDir else fixture.mangaDir, "Stale")
        }
        every { fixture.sourceDir.findFile("Other title") } returns aliasDir
        fixture.seed(fixture.unrelatedManga, fixture.unrelatedDir, "Unrelated")
        val sibling = file("Sibling", directory = true)
        val protected = file("Protected.cbz")
        val temporary = file("Unfinished_tmp", directory = true)
        val foreign = file("cover.jpg")
        every { fixture.mangaDir.listFiles() } returns arrayOf(sibling, protected, temporary, foreign)
        clearMocks(fixture.root, fixture.sourceDir, fixture.unrelatedDir, answers = false)

        fixture.cache.refreshMangaDirectories(aliases.take(3))

        aliases.forEach { fixture.cache.getDownloadCount(it) shouldBe 2 }
        fixture.cache.getDownloadCount(fixture.unrelatedManga) shouldBe 1
        verify(exactly = 1) { fixture.mangaDir.listFiles() }
        verify(exactly = 0) { aliasDir.listFiles() }
        verify(exactly = 1) { fixture.root.findFile("Source") }
        verify(exactly = 0) { fixture.root.listFiles() }
        verify(exactly = 0) { fixture.sourceDir.listFiles() }
        verify(exactly = 0) { fixture.unrelatedDir.listFiles() }
        verify(exactly = 0) { fixture.unrelatedDir.canRead() }
        verify(exactly = 0) { sibling.delete() }
        verify(exactly = 0) { protected.delete() }
        verify(exactly = 0) { foreign.delete() }
        verify(exactly = 0) { fixture.root.findFile("Unrelated source") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["empty", "manga absent", "source absent"])
    fun `empty or absent target clears stale chapters on all cached physical aliases`(state: String) =
        runTest(dispatcher) {
            val fixture = Fixture(this)
            val alias = fixture.manga.copy(id = 2, source = 8, title = "Alias")
            fixture.seed(fixture.manga, fixture.mangaDir, "Stale")
            fixture.seed(alias, fixture.mangaDir, "Stale")
            when (state) {
                "manga absent" -> every { fixture.sourceDir.findFile(fixture.manga.title) } returns null
                "source absent" -> every { fixture.root.findFile("Source") } returns null
            }

            fixture.cache.refreshMangaDirectories(listOf(fixture.manga))

            fixture.cache.getDownloadCount(fixture.manga) shouldBe 0
            fixture.cache.getDownloadCount(alias) shouldBe 0
            verify(exactly = if (state == "empty") 1 else 0) { fixture.mangaDir.listFiles() }
            verify(exactly = 0) { fixture.root.listFiles() }
        }

    @ParameterizedTest
    @ValueSource(strings = ["root", "source", "manga", "listing", "lost parent", "second target"])
    fun `discovery failure preserves every cached entry`(failure: String) = runTest(dispatcher) {
        val fixture = Fixture(this)
        fixture.seed(fixture.manga, fixture.mangaDir, "Stale")
        fixture.seed(fixture.unrelatedManga, fixture.unrelatedDir, "Unrelated")
        every { fixture.mangaDir.listFiles() } returns arrayOf(file("Remaining", directory = true))
        var targets = listOf(fixture.manga)
        when (failure) {
            "root" -> every { fixture.root.canRead() } returns false
            "source" -> every { fixture.sourceDir.canRead() } returns false
            "manga" -> every { fixture.mangaDir.canRead() } returns false
            "listing" -> every { fixture.mangaDir.listFiles() } returns null
            "lost parent" -> {
                every { fixture.sourceDir.findFile(fixture.manga.title) } answers {
                    every { fixture.sourceDir.canRead() } returns false
                    null
                }
            }
            "second target" -> {
                targets = targets + fixture.unrelatedManga
                every { fixture.unrelatedDir.listFiles() } throws IOException("Unreadable second target")
            }
        }

        assertThrows<IOException> { fixture.cache.refreshMangaDirectories(targets) }

        fixture.cache.getDownloadCount(fixture.manga) shouldBe 1
        fixture.cache.getDownloadCount(fixture.unrelatedManga) shouldBe 1
        fixture.downloaded("Stale") shouldBe true
        fixture.downloaded("Remaining") shouldBe false
    }

    @Test
    fun `partial deletion refresh keeps chapters still present and removes successfully deleted entries`() =
        runTest(dispatcher) {
            val fixture = Fixture(this)
            fixture.seed(fixture.manga, fixture.mangaDir, "Deleted")
            fixture.seed(fixture.manga, fixture.mangaDir, "Failed deletion")
            fixture.seed(fixture.manga, fixture.mangaDir, "Sibling")
            every { fixture.mangaDir.listFiles() } returns arrayOf(
                file("Failed deletion", directory = true),
                file("Sibling.cbz"),
            )

            fixture.cache.refreshMangaDirectories(listOf(fixture.manga))

            fixture.cache.getDownloadCount(fixture.manga) shouldBe 2
            fixture.downloaded("Deleted") shouldBe false
            fixture.downloaded("Failed deletion") shouldBe true
            fixture.downloaded("Sibling") shouldBe true
        }

    @Test
    fun `refresh adds actual downloads missing from cache`() = runTest(dispatcher) {
        val fixture = Fixture(this)
        every { fixture.mangaDir.listFiles() } returns arrayOf(file("Chapter", directory = true))

        fixture.cache.refreshMangaDirectories(listOf(fixture.manga))

        fixture.cache.getDownloadCount(fixture.manga) shouldBe 1
        fixture.downloaded("Chapter") shouldBe true
    }

    @Test
    fun `successful refresh notifies observers and persists updated disk index`() = runTest(dispatcher) {
        val fixture = Fixture(this)
        val index = File(cacheDir, "written-index")
        every { anyConstructed<AtomicFile>().startWrite() } answers { FileOutputStream(index) }
        every { anyConstructed<AtomicFile>().finishWrite(any()) } answers {
            firstArg<FileOutputStream>().close()
        }
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.cache.changes.collect { changes++ }
        }
        runCurrent()
        val before = changes

        fixture.cache.refreshMangaDirectories(listOf(fixture.manga))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        (changes > before) shouldBe true
        (index.length() > 0) shouldBe true
        verify(exactly = 1) { anyConstructed<AtomicFile>().finishWrite(any()) }
        verify(exactly = 0) { anyConstructed<AtomicFile>().delete() }
    }

    @Test
    fun `refresh waits for pending disk initialization before discovering directories`() = runTest(dispatcher) {
        val fixture = Fixture(this)
        val initialization = Job()
        DownloadCache::class.java.getDeclaredField("initJob").apply {
            isAccessible = true
            set(fixture.cache, initialization)
        }
        val refresh = launch { fixture.cache.refreshMangaDirectories(listOf(fixture.manga)) }
        runCurrent()
        verify(exactly = 0) { fixture.mangaDir.listFiles() }
        refresh.isCompleted shouldBe false

        initialization.complete()
        runCurrent()

        refresh.isCompleted shouldBe true
        verify(exactly = 1) { fixture.mangaDir.listFiles() }
    }

    @Test
    fun `pending full renewal reads current disk after scoped refresh rather than overwriting with old snapshot`() =
        runTest(dispatcher) {
            val fixture = Fixture(this)
            fixture.seed(fixture.manga, fixture.mangaDir, "Stale")
            val sourcesReady = CompletableDeferred<Unit>()
            val sourcesEntered = CompletableDeferred<Unit>()
            coEvery { fixture.sources.getOnlineSources() } coAnswers {
                sourcesEntered.complete(Unit)
                sourcesReady.await()
                listOf(fixture.source)
            }
            every { fixture.root.listFiles() } returns arrayOf(fixture.sourceDir)
            every { fixture.sourceDir.listFiles() } returns arrayOf(fixture.mangaDir)
            every { fixture.mangaDir.listFiles() } returns arrayOf(file("Remaining", directory = true))
            fixture.cache.invalidateCache()
            runCurrent()
            sourcesEntered.isCompleted shouldBe true
            val renewal = requireNotNull(
                Job::class.java.cast(
                    DownloadCache::class.java.getDeclaredField("renewalJob").apply { isAccessible = true }
                        .get(fixture.cache),
                ),
            )
            var renewalFailure: Throwable? = null
            renewal.invokeOnCompletion { renewalFailure = it }

            fixture.cache.refreshMangaDirectories(listOf(fixture.manga))
            sourcesReady.complete(Unit)
            runCurrent()

            renewalFailure shouldBe null
            renewal.isCompleted shouldBe true
            fixture.cache.getDownloadCount(fixture.manga) shouldBe 1
            fixture.downloaded("Stale") shouldBe false
            fixture.downloaded("Remaining") shouldBe true
            verify(exactly = 2) { fixture.mangaDir.listFiles() }
        }

    private inner class Fixture(scope: TestScope) {
        val root = directory("Downloads")
        val sourceDir = directory("Source")
        val mangaDir = directory("Manga")
        val unrelatedDir = directory("Unrelated manga")
        val manga = Manga.create().copy(id = 1, source = 7, title = "Manga")
        val unrelatedManga = manga.copy(id = 90, source = 90, title = "Unrelated manga")
        val source = mockk<HttpSource>()
        val sources = mockk<SourceManager>()
        val provider = mockk<DownloadProvider>()
        private val context = mockk<Context>()
        private val storage = mockk<StorageManager>()
        val cache: DownloadCache

        init {
            every { context.cacheDir } returns cacheDir
            every { storage.changes } returns MutableSharedFlow()
            every { storage.getDownloadsDirectory() } returns root
            every { source.id } returns 7
            every { root.findFile(any()) } returns sourceDir
            every { sourceDir.findFile(any()) } answers {
                if (firstArg<String>() == unrelatedManga.title) unrelatedDir else mangaDir
            }
            coEvery { sources.get(any()) } returns source
            coEvery { sources.getOrStub(any()) } returns source
            coEvery { sources.getOnlineSources() } returns listOf(source)
            coEvery { sources.getStubSources() } returns emptyList()
            coEvery { provider.findSourceDir(any()) } returns sourceDir
            every { provider.getSourceDirName(any()) } returns "Source"
            every { provider.getMangaDirName(any<String>()) } answers { firstArg() }
            every { provider.getValidChapterDirNames(any<String>(), any(), any()) } answers { listOf(firstArg()) }
            cache = DownloadCache(scope.backgroundScope, context, provider, sources, storage)
            scope.runCurrent()
            DownloadCache::class.java.getDeclaredField("lastRenew").apply {
                isAccessible = true
                setLong(cache, System.currentTimeMillis())
            }
        }

        suspend fun seed(owner: Manga, directory: UniFile, chapter: String) = cache.addChapter(
            chapter,
            directory,
            owner,
        )

        fun downloaded(name: String) = cache.isChapterDownloaded(name, null, "/chapter", manga.title, manga.source)
    }

    private fun directory(name: String) = file(name, directory = true).also {
        every { it.listFiles() } returns emptyArray()
        every { it.exists() } returns true
        every { it.canRead() } returns true
    }

    private fun file(name: String, directory: Boolean = false): UniFile {
        val identity = mockk<Uri> { every { scheme } returns "content" }
        return mockk {
            every { uri } returns identity
            every { this@mockk.name } returns name
            every { isDirectory } returns directory
            every { isFile } returns !directory
        }
    }
}
