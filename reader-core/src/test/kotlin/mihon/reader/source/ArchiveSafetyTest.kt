package mihon.reader.source

import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.UnsupportedRarMethodException
import com.github.junrar.exception.UnsupportedRarVersionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

class ArchiveSafetyTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `ZIP rejects traversal UNC drive and duplicate normalized names`() {
        listOf("../escape.png", "/absolute.png", "C:/drive.png", "//server/share.png").forEachIndexed { index, name ->
            val path = temporaryDirectory.resolve("unsafe-$index.zip")
            ArchiveFixtures.writeZip(path, listOf(name to byteArrayOf(1)))
            shouldThrow<ReaderFailure.UnsafePath> {
                LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, path.fileName.toString()))
            }
        }
        val duplicate = temporaryDirectory.resolve("duplicate.zip")
        ArchiveFixtures.writeZip(duplicate, listOf("pages/a.png" to byteArrayOf(1), "pages/./a.png" to byteArrayOf(2)))
        shouldThrow<ReaderFailure.DuplicateEntry> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, duplicate.fileName.toString()))
        }
    }

    @Test
    fun `ZIP and TAR reject archive links devices and hardlinks`() {
        val zipLink = temporaryDirectory.resolve("link.zip")
        ArchiveFixtures.writeUnixZip(zipLink, "page.png", "target".toByteArray(), 0xA1FF)
        shouldThrow<ReaderFailure.UnsafePath> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "link.zip"))
        }

        listOf(TarConstants.LF_SYMLINK, TarConstants.LF_LINK, TarConstants.LF_CHR).forEachIndexed { index, flag ->
            val path = temporaryDirectory.resolve("link-$index.tar")
            val entry = TarArchiveEntry("page.png", flag)
            ArchiveFixtures.writeTar(path, listOf(entry to ByteArray(0)))
            shouldThrow<ReaderFailure.UnsafePath> {
                LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, path.fileName.toString()))
            }
        }
    }

    @Test
    fun `7z rejects reparse and Unix link metadata`() {
        val path = temporaryDirectory.resolve("link.7z")
        ArchiveFixtures.writeSevenZSpecial(path, "page.png", 0xA1FF)
        shouldThrow<ReaderFailure.UnsafePath> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "link.7z"))
        }
    }

    @Test
    fun `7z rejects a decoder dictionary above its fixed memory ceiling`(): Unit = runBlocking {
        ArchiveFixtures.copyCommittedFixture(temporaryDirectory, "malicious-large-dictionary.7z")
        LocalChapterSourceFactory().create(
            ArchiveFixtures.asset(temporaryDirectory, "malicious-large-dictionary.7z"),
        ).use { source ->
            shouldThrow<ReaderFailure.LimitExceeded> {
                source.open(source.pages().single().id).use { it.input.read() }
            }
        }
    }

    @Test
    fun `standalone encoded size limit is checked from the opened file`() {
        val path = temporaryDirectory.resolve("huge.png")
        Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            channel.write(ByteBuffer.wrap(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
            channel.position(ReaderLimits.MAX_STANDALONE_BYTES)
            channel.write(ByteBuffer.wrap(byteArrayOf(1)))
        }
        shouldThrow<ReaderFailure.LimitExceeded> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "huge.png", "IMAGE"))
        }
    }

    @Test
    fun `compressed magic must contain positive TAR evidence`() {
        val path = temporaryDirectory.resolve("not-a-tar.gz")
        Files.newOutputStream(path).use { file ->
            GzipCompressorOutputStream(file).use { it.write("not a tar".toByteArray()) }
        }
        shouldThrow<ReaderFailure.UnsupportedFormat> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "not-a-tar.gz"))
        }
    }

    @Test
    fun `TAR enumeration charges decompressed skipped bytes to the lifetime budget`() {
        val path = temporaryDirectory.resolve("charged.tar")
        ArchiveFixtures.writeTar(
            path,
            listOf(
                TarArchiveEntry("notes.txt") to ByteArray(600),
                TarArchiveEntry("page.png") to byteArrayOf(1),
            ),
        )
        shouldThrow<ReaderFailure.LimitExceeded> {
            TarChapterSource(
                ArchiveFixtures.asset(temporaryDirectory, "charged.tar"),
                expansionBudget = ChapterExpansionBudget(1024),
            )
        }
    }

    @Test
    fun `committed encrypted containers are rejected with typed failures`() {
        listOf("encrypted.zip", "encrypted.7z", "encrypted-rar5.rar").forEach { name ->
            ArchiveFixtures.copyCommittedFixture(temporaryDirectory, name)
            shouldThrow<ReaderFailure.EncryptedContainer> {
                LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, name))
            }
        }
    }

    @Test
    fun `corrupt containers and unsupported RAR signatures do not fall through`() {
        val corruptZip = temporaryDirectory.resolve("corrupt.zip")
        ArchiveFixtures.writeZip(corruptZip, listOf("page.png" to byteArrayOf(1, 2, 3)))
        val truncated = Files.readAllBytes(corruptZip).copyOf(12)
        Files.write(corruptZip, truncated)
        shouldThrow<ReaderFailure.CorruptContainer> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "corrupt.zip"))
        }

        val unsupportedRar = temporaryDirectory.resolve("future.rar")
        Files.write(unsupportedRar, byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x02))
        shouldThrow<ReaderFailure.UnsupportedFormat> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "future.rar"))
        }
    }

    @Test
    fun `junrar unsupported and encrypted failures retain typed classifications`() {
        classifyRarFailure(UnsupportedRarVersionException())::class shouldBe ReaderFailure.UnsupportedFormat::class
        classifyRarFailure(UnsupportedRarMethodException())::class shouldBe ReaderFailure.UnsupportedFormat::class
        classifyRarFailure(UnsupportedRarEncryptedException())::class shouldBe ReaderFailure.EncryptedContainer::class
    }

    @Test
    fun `secure opening detects deterministic open-time replacement`() {
        val original = temporaryDirectory.resolve("page.png")
        Files.write(original, byteArrayOf(1, 2, 3))
        val guard = SecureLocalPath(temporaryDirectory) { path ->
            val old = path.resolveSibling("old.png")
            Files.move(path, old)
            Files.write(path, byteArrayOf(4, 5, 6, 7))
        }
        shouldThrow<ReaderFailure.ResourceChanged> { guard.openRegularFile(Path.of("page.png")) }
    }

    @Test
    fun `secure opening rejects an ancestor ABA substituted handle`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows native-handle test")
        val chapter = Files.createDirectory(temporaryDirectory.resolve("chapter"))
        Files.write(chapter.resolve("page.png"), byteArrayOf(1, 2, 3))
        val attacker = Files.write(temporaryDirectory.resolve("attacker.png"), byteArrayOf(9, 9, 9))
        val guard = SecureLocalPath.forWindowsTesting(temporaryDirectory) {
            WindowsNativePathAccess.openFileChannel(attacker)
        }

        shouldThrow<ReaderFailure.ResourceChanged> {
            guard.openRegularFile(Path.of("chapter/page.png"))
        }
    }

    @Test
    fun `Windows guarded channel preserves size position and positional reads`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows native-handle test")
        val bytes = ByteArray(1024) { it.toByte() }
        Files.write(temporaryDirectory.resolve("channel.bin"), bytes)

        SecureLocalPath(temporaryDirectory).openRegularFile(Path.of("channel.bin")).use { channel ->
            channel.size() shouldBe bytes.size.toLong()
            channel.position(11)
            val destination = ByteBuffer.allocate(16)
            channel.read(destination, 100)
            destination.array().shouldBe(bytes.copyOfRange(100, 116))
            channel.position() shouldBe 11
        }
    }

    @Test
    fun `Windows directory enumeration rejects a substituted opened directory handle`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows native-handle test")
        val chapter = Files.createDirectory(temporaryDirectory.resolve("chapter"))
        Files.write(chapter.resolve("page.png"), byteArrayOf(1))
        val attacker = Files.createDirectory(temporaryDirectory.resolve("attacker"))
        Files.write(attacker.resolve("outside.png"), byteArrayOf(9))
        val guard = SecureLocalPath.forWindowsDirectoryTesting(temporaryDirectory) {
            WindowsNativePathAccess.listDirectory(attacker)
        }

        shouldThrow<ReaderFailure.ResourceChanged> {
            guard.listDirectory(Path.of("chapter"))
        }
    }

    @Test
    fun `Windows directory enumeration rejects limit plus one before returning names`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows native-handle test")
        val directory = Files.createDirectory(temporaryDirectory.resolve("bounded-directory"))
        repeat(3) { index -> Files.write(directory.resolve("$index.png"), byteArrayOf(index.toByte())) }

        shouldThrow<ReaderFailure.TooManyEntries> {
            SecureLocalPath(temporaryDirectory).listDirectory(Path.of("bounded-directory"), maxEntries = 2)
        }
    }

    @Test
    fun `directory and container symlinks are rejected without following them`() {
        val realDirectory = Files.createDirectory(temporaryDirectory.resolve("real"))
        Files.write(realDirectory.resolve("page.png"), byteArrayOf(1))
        val directoryLink = temporaryDirectory.resolve("linked-directory")
        val realArchive = temporaryDirectory.resolve("real.zip")
        ArchiveFixtures.writeZip(realArchive, listOf("page.png" to byteArrayOf(1)))
        val archiveLink = temporaryDirectory.resolve("linked.zip")
        try {
            Files.createSymbolicLink(directoryLink, realDirectory.fileName)
            Files.createSymbolicLink(archiveLink, realArchive.fileName)
        } catch (_: AccessDeniedException) {
            assumeTrue(false, "Symbolic-link creation is unavailable for this Windows account")
        } catch (error: FileSystemException) {
            val reason = error.reason.orEmpty()
            val missingPrivilege = reason.contains("privilege", ignoreCase = true) || reason.contains("特权")
            if (!missingPrivilege) throw error
            assumeTrue(false, "Symbolic-link privilege is unavailable for this Windows account")
        } catch (_: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are unsupported by this file-system provider")
        }
        shouldThrow<ReaderFailure.UnsafePath> {
            LocalChapterSourceFactory().create(
                ArchiveFixtures.asset(temporaryDirectory, "linked-directory", "DIRECTORY"),
            )
        }
        shouldThrow<ReaderFailure.UnsafePath> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "linked.zip"))
        }
    }

    @Test
    fun `actual reads enforce exact per-page and cumulative limits despite declared sizes`(): Unit = runBlocking {
        val asset = ArchiveFixtures.asset(temporaryDirectory, "unused")
        val exact = FakeSource(asset, ChapterExpansionBudget(100), pageLimit = 3, bytes = byteArrayOf(1, 2, 3))
        exact.open(exact.pages().single().id).use { it.input.readBytes().size shouldBe 3 }

        val oversized = FakeSource(asset, ChapterExpansionBudget(100), pageLimit = 3, bytes = byteArrayOf(1, 2, 3, 4))
        shouldThrow<ReaderFailure.LimitExceeded> {
            oversized.open(oversized.pages().single().id).use { it.input.readBytes() }
        }

        ArchiveFixtures.writeZip(
            temporaryDirectory.resolve("cumulative.zip"),
            listOf("page.png" to byteArrayOf(1, 2, 3)),
        )
        val cumulative = ZipChapterSource(
            ArchiveFixtures.asset(temporaryDirectory, "cumulative.zip"),
            expansionBudget = ChapterExpansionBudget(5),
        )
        cumulative.open(cumulative.pages().single().id).use { it.input.readBytes() }
        shouldThrow<ReaderFailure.LimitExceeded> {
            cumulative.open(cumulative.pages().single().id).use { it.input.readBytes() }
        }
    }

    @Test
    fun `budget charge failure closes the managed page input`(): Unit = runBlocking {
        val closed = AtomicBoolean(false)
        val source = ClosingFakeSource(
            ArchiveFixtures.asset(temporaryDirectory, "unused"),
            ChapterExpansionBudget(0),
            closed,
        )
        val input = source.open(source.pages().single().id)

        shouldThrow<ReaderFailure.LimitExceeded> { input.input.read() }

        closed.get() shouldBe true
    }

    @Test
    fun `forged ZIP uncompressed size cannot bypass actual expansion accounting`(): Unit = runBlocking {
        val path = temporaryDirectory.resolve("forged.zip")
        ArchiveFixtures.writeZip(path, listOf("page.png" to byteArrayOf(1, 2, 3, 4)))
        ArchiveFixtures.forgeZipDeclaredSize(path, declaredSize = 0)
        val source = ZipChapterSource(
            ArchiveFixtures.asset(temporaryDirectory, "forged.zip"),
            expansionBudget = ChapterExpansionBudget(3),
        )
        val page = source.pages().single()
        shouldThrow<ReaderFailure.LimitExceeded> {
            source.open(page.id).use { input ->
                input.byteCount shouldBe 0
                input.input.readBytes()
            }
        }
    }

    @Test
    fun `entry limit uses checked metadata accounting`() {
        val entries = MutableList(ReaderLimits.MAX_ENTRIES) { index -> SourceEntry("$index.png", "$index.png", 0) }
        val keys = entries.mapTo(mutableSetOf()) { ImageEntryPolicy.duplicateKey(it.logicalName) }
        shouldThrow<ReaderFailure.TooManyEntries> {
            entries.addChecked("overflow.png", 0, keys)
        }
        val budget = ChapterExpansionBudget(Long.MAX_VALUE)
        budget.charge(Long.MAX_VALUE)
        shouldThrow<ReaderFailure.LimitExceeded> { budget.charge(1) }
    }

    @Test
    fun `real container rejects limit plus one metadata entries`() {
        val path = temporaryDirectory.resolve("too-many.zip")
        ArchiveFixtures.writeZipWithEmptyEntries(path, ReaderLimits.MAX_ENTRIES + 1)
        shouldThrow<ReaderFailure.TooManyEntries> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "too-many.zip"))
        }
    }

    @Test
    fun `closing inputs and sources releases archive handles immediately`(): Unit = runBlocking {
        val path = temporaryDirectory.resolve("release.zip")
        ArchiveFixtures.writeZip(path, listOf("page.png" to byteArrayOf(1, 2, 3)))
        val source = LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "release.zip"))
        val input = source.open(source.pages().single().id)
        source.close()
        input.close()
        Files.move(path, temporaryDirectory.resolve("renamed.zip"), StandardCopyOption.ATOMIC_MOVE)
        Files.delete(temporaryDirectory.resolve("renamed.zip"))
    }

    @Test
    fun `cancelled caller cannot start source work`(): Unit = runBlocking {
        val asset = ArchiveFixtures.asset(temporaryDirectory, "unused")
        val source = FakeSource(asset, ChapterExpansionBudget(10), pageLimit = 10, bytes = byteArrayOf(1))
        val cancelled = Job().apply { cancel() }
        shouldThrow<CancellationException> {
            runBlocking { withContext(cancelled) { source.pages() } }
        }
    }

    @Test
    fun `cancellation during an archive scan closes the container handle`(): Unit = runBlocking {
        val path = temporaryDirectory.resolve("cancel-scan.tar")
        ArchiveFixtures.writeTar(
            path,
            listOf(
                TarArchiveEntry("notes.txt") to ByteArray(1024),
                TarArchiveEntry("page.png") to byteArrayOf(1),
            ),
        )
        val source = TarChapterSource(ArchiveFixtures.asset(temporaryDirectory, path.fileName.toString()))
        val page = source.pages().single()
        val scanJob = Job()
        source.scanObserver = { scanJob.cancel() }

        shouldThrow<CancellationException> {
            withContext(scanJob) { source.open(page.id) }
        }

        source.close()
        Files.move(path, temporaryDirectory.resolve("cancelled-scan.tar"), StandardCopyOption.ATOMIC_MOVE)
    }
}

private class FakeSource(
    asset: ReaderChapterAsset,
    budget: ChapterExpansionBudget,
    pageLimit: Long,
    private val bytes: ByteArray,
) : ManagedChapterSource(asset, budget, pageLimit) {
    private val entry = SourceEntry("page.png", "page.png", 0)

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return listOf(entry.descriptor(asset))
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        requireEntry(pageId, listOf(entry))
        return boundedInput(ByteArrayInputStream(bytes), declaredSize = 0)
    }
}

private class ClosingFakeSource(
    asset: ReaderChapterAsset,
    budget: ChapterExpansionBudget,
    private val inputClosed: AtomicBoolean,
) : ManagedChapterSource(asset, budget) {
    private val entry = SourceEntry("page.png", "page.png", 0)

    override suspend fun pages(): List<PageDescriptor> = listOf(entry.descriptor(asset))

    override suspend fun open(pageId: PageId): BoundedPageInput {
        requireEntry(pageId, listOf(entry))
        val input = object : InputStream() {
            override fun read(): Int = 1

            override fun close() {
                inputClosed.set(true)
            }
        }
        return boundedInput(input, 0)
    }
}
