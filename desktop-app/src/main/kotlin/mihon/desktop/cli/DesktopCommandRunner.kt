package mihon.desktop.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.DesktopRuntime
import mihon.desktop.library.backup.BackupDecodeException
import mihon.desktop.library.backup.BackupValidationException
import mihon.desktop.library.local.LocalImportRejected
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.reader.image.IntRect
import mihon.reader.memory.ReaderMemoryMetrics
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.PageLoadCoordinator
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

class DesktopCommandRunner(
    private val runtime: DesktopRuntime,
    private val output: OutputStream = System.out,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val readerVerification: suspend (Path) -> ReaderVerificationSummary = { fixtureRoot ->
        PackagedReaderVerifier(runtime, nowMillis).verify(fixtureRoot)
    },
) {
    fun run(command: DesktopCommand): Int = try {
        when (command) {
            DesktopCommand.LaunchUi -> error("LaunchUi is not a headless command")
            DesktopCommand.FoundationSmoke -> {
                writeUtf8Line(output, "MIHON_DESKTOP_SMOKE_OK ${runtime.directories.root}")
                0
            }
            is DesktopCommand.ImportBackup -> {
                writeReport("import-backup", command.path, runtime.backupImporter.import(command.path, nowMillis()))
                0
            }
            is DesktopCommand.ExportBackup -> {
                val exporter = mihon.desktop.library.backup.AndroidBackupExporter(runtime.library)
                exporter.export(command.path)
                writeJson(ExportOutput("export-backup", "SUCCEEDED", command.path.toString()))
                0
            }
            is DesktopCommand.ImportLocal -> {
                val report = runtime.localImporter.import(command.path, runtime.localLibraryRoot, nowMillis())
                writeReport("import-local", command.path, report)
                0
            }
            DesktopCommand.ListLibraryJson -> {
                writeJson(ListLibraryOutput(items = runtime.library.librarySnapshot().map(LibraryItemOutput::from)))
                0
            }
            is DesktopCommand.VerifyReader -> {
                writeJson(runBlocking { readerVerification(command.fixtureRoot) })
                0
            }
        }
    } catch (error: BackupDecodeException) {
        writeRejected(command, error.kind.name, command.sourcePath())
        2
    } catch (error: BackupValidationException) {
        writeRejected(command, "BACKUP_VALIDATION", error.path)
        2
    } catch (_: LocalImportRejected) {
        writeRejected(command, "LOCAL_IMPORT_REJECTED", command.sourcePath())
        2
    } catch (_: IOException) {
        writeRejected(command, "INPUT_IO", command.sourcePath())
        2
    } catch (_: Throwable) {
        writeFailure(command.commandName(), command.sourcePath())
        1
    }

    private fun writeReport(command: String, path: Path, report: ImportReport) {
        writeJson(
            ImportOutput(
                command = command,
                status = report.status.name,
                reportId = report.id,
                path = path.toString(),
                counts = CountsOutput.from(report.counts),
            ),
        )
    }

    private fun writeRejected(command: DesktopCommand, category: String, path: String?) {
        writeJson(ErrorOutput(command.commandName(), "REJECTED", category, path))
    }

    private fun writeFailure(command: String, path: String?) {
        writeJson(ErrorOutput(command, "FAILED", "UNEXPECTED", path))
    }

    private inline fun <reified T> writeJson(value: T) {
        writeUtf8Line(output, JSON.encodeToString(value))
    }

    companion object {
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        fun writeCommandLineError(output: OutputStream, error: CommandLineException) {
            writeUtf8Line(
                output,
                JSON.encodeToString(ErrorOutput("command-line", "REJECTED", "COMMAND_LINE", error.argument)),
            )
        }

        fun writeStartupFailure(output: OutputStream) {
            writeUtf8Line(output, JSON.encodeToString(ErrorOutput("startup", "FAILED", "UNEXPECTED", null)))
        }
    }
}

@Serializable
private data class ExportOutput(
    val command: String,
    val status: String,
    val path: String,
)

@Serializable
private data class ImportOutput(
    val command: String,
    val status: String,
    val reportId: Long,
    val path: String,
    val counts: CountsOutput,
)

@Serializable
private data class CountsOutput(
    val mangaInserted: Long = 0,
    val mangaMerged: Long = 0,
    val chaptersInserted: Long = 0,
    val chaptersMerged: Long = 0,
    val categoriesLinked: Long = 0,
    val preferencesImported: Long = 0,
    val preferencesSkipped: Long = 0,
) {
    companion object {
        fun from(counts: ImportCounts) = CountsOutput(
            mangaInserted = counts.mangaInserted,
            mangaMerged = counts.mangaMerged,
            chaptersInserted = counts.chaptersInserted,
            chaptersMerged = counts.chaptersMerged,
            categoriesLinked = counts.categoriesLinked,
            preferencesImported = counts.preferencesImported,
            preferencesSkipped = counts.preferencesSkipped,
        )
    }
}

@Serializable
private data class ListLibraryOutput(
    val command: String = "list-library",
    val items: List<LibraryItemOutput>,
)

@Serializable
private data class LibraryItemOutput(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val chapterCount: Long,
    val unreadCount: Long,
) {
    companion object {
        fun from(manga: LibraryManga) = LibraryItemOutput(
            id = manga.id,
            sourceId = manga.sourceId,
            url = manga.url,
            title = manga.title,
            thumbnailUrl = manga.thumbnailUrl,
            chapterCount = manga.chapterCount,
            unreadCount = manga.unreadCount,
        )
    }
}

@Serializable
private data class ErrorOutput(
    val command: String,
    val status: String,
    val category: String,
    val path: String?,
)

@Serializable
data class ReaderVerificationSummary(
    val command: String = "verify-reader",
    val status: String = "SUCCEEDED",
    val phase: String,
    val fixtureManifestSha256: String,
    val verifiedAssets: List<String>,
    val verifiedModes: List<String>,
    val decodedTileCount: Int,
    val gifFrameHashes: List<String>,
    val cacheResidentHighWaterBytes: Long,
    val coreResidentAndInFlightHighWaterBytes: Long,
    val progressRows: List<ReaderProgressRow>,
)

@Serializable
data class ReaderProgressRow(
    val chapterId: Long,
    val chapterName: String,
    val pageIndex: Long,
    val pageCount: Int,
    val completed: Boolean,
)

@Serializable
internal data class ReaderFixtureManifest(
    val format: String,
    val mangaDirectory: String,
    val standaloneImage: String,
    val committedRarPath: String,
    val committedRarSha256: String,
    val committedSourcePath: String,
    val committedSourceSha256: String,
    val files: List<ReaderFixtureFileRecord>,
)

@Serializable
internal data class ReaderFixtureFileRecord(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

internal class PackagedReaderVerifier(
    private val runtime: DesktopRuntime,
    private val nowMillis: () -> Long,
) {
    suspend fun verify(requestedRoot: Path): ReaderVerificationSummary {
        val fixture = validateFixture(requestedRoot)
        val importReport = runtime.localImporter.import(fixture.mangaRoot, runtime.localLibraryRoot, nowMillis())
        check(importReport.status.name == "SUCCEEDED") { "reader fixture import failed" }

        val manga = runtime.library.librarySnapshot().singleOrNull { it.title == READER_FIXTURE_MANGA }
            ?: error("reader fixture manga was not imported")
        val chapters = runtime.library.chapterSnapshot(manga.id).sortedBy { it.name }
        check(chapters.map { it.name }.toSet() == EXPECTED_CHAPTER_NAMES) {
            "reader fixture chapter matrix is incomplete"
        }
        val assets = chapters.associateWith { chapter ->
            runtime.library.chapterAsset(chapter.id) ?: error("reader fixture chapter has no local asset")
        }
        val factory = requireNotNull(runtime.readerFactory) { "reader verification runtime has no reader factory" }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val memoryHighWater = CoreMemoryHighWater { factory.memoryBudget.metrics }
        val pageCounts = linkedMapOf<Long, Int>()
        val verifiedAssets = linkedSetOf<String>()
        val gifHashes = linkedSetOf<String>()
        var decodedTileCount = 0
        var corruptPageObserved = false
        var decodedAfterCorrupt = false
        try {
            val standaloneAsset = ReaderChapterAsset(
                mangaId = manga.id,
                chapterId = STANDALONE_CHAPTER_ID,
                mangaTitle = READER_FIXTURE_MANGA,
                chapterName = "standalone",
                storageRoot = fixture.root,
                relativePath = Path.of(READER_FIXTURE_STANDALONE),
                assetKind = "IMAGE",
                sizeBytes = Files.size(fixture.standalone),
                modifiedAt = Files.getLastModifiedTime(fixture.standalone, LinkOption.NOFOLLOW_LINKS).toMillis(),
                lastPageRead = 0,
                read = false,
            )
            verifyAsset("standalone", standaloneAsset, factory, scope).also { result ->
                verifiedAssets += "standalone"
                decodedTileCount += result.decodedTileCount
                gifHashes += result.gifFrameHashes
                corruptPageObserved = corruptPageObserved || result.corruptPageObserved
                decodedAfterCorrupt = decodedAfterCorrupt || result.decodedAfterCorrupt
            }
            assets.entries.sortedBy { it.key.name }.forEach { (chapter, asset) ->
                val label = assetLabel(chapter.name)
                verifyAsset(label, asset, factory, scope).also { result ->
                    verifiedAssets += label
                    decodedTileCount += result.decodedTileCount
                    gifHashes += result.gifFrameHashes
                    corruptPageObserved = corruptPageObserved || result.corruptPageObserved
                    decodedAfterCorrupt = decodedAfterCorrupt || result.decodedAfterCorrupt
                    pageCounts[chapter.id] = result.pageCount
                }
            }
            check(verifiedAssets == EXPECTED_ASSET_LABELS) { "reader asset matrix was not fully verified" }
            check(gifHashes.size == 2) { "animated GIF frames did not produce two distinct hashes" }
            check(corruptPageObserved && decodedAfterCorrupt) { "corrupt-page isolation was not observed" }

            val targetBefore = chapters.single { it.name == TARGET_CHAPTER_NAME }
            val targetPageCount = checkNotNull(pageCounts[targetBefore.id])
            check(targetPageCount >= 5) { "progress chapter does not contain enough pages" }
            val (phase, desiredPage) = when {
                targetBefore.read -> "final-completion" to targetPageCount - 1
                targetBefore.lastPageRead == 0L -> "initial-open" to 1
                targetBefore.lastPageRead == 1L -> "reopen-continue" to 2
                else -> "final-completion" to targetPageCount - 1
            }
            exerciseSessions(
                chapters = chapters,
                pageCounts = pageCounts,
                targetChapterId = targetBefore.id,
                expectedRestoredPage = targetBefore.lastPageRead.toInt().coerceAtMost(targetPageCount - 1),
                desiredPage = desiredPage,
                factory = factory,
            )

            val rows = runtime.library.chapterSnapshot(manga.id).sortedBy { it.name }.map { chapter ->
                ReaderProgressRow(
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    pageIndex = chapter.lastPageRead,
                    pageCount = checkNotNull(pageCounts[chapter.id]),
                    completed = chapter.read,
                )
            }
            val targetAfter = rows.single { it.chapterId == targetBefore.id }
            check(targetAfter.pageIndex == desiredPage.toLong()) { "reader progress page was not persisted" }
            check(targetAfter.completed == (desiredPage == targetPageCount - 1)) {
                "reader progress completion state was not persisted"
            }
            val coreHighWater = memoryHighWater.stop()
            check(coreHighWater in 1..factory.memoryBudget.metrics.limitBytes) {
                "core resident and in-flight high-water is outside the reader budget"
            }
            return ReaderVerificationSummary(
                phase = phase,
                fixtureManifestSha256 = fixture.manifestSha256,
                verifiedAssets = verifiedAssets.toList(),
                verifiedModes = ReadingMode.entries.map { it.name },
                decodedTileCount = decodedTileCount,
                gifFrameHashes = gifHashes.toList(),
                cacheResidentHighWaterBytes = factory.cache.metrics.highWaterBytes,
                coreResidentAndInFlightHighWaterBytes = coreHighWater,
                progressRows = rows,
            )
        } finally {
            memoryHighWater.close()
            scope.cancel()
        }
    }

    private suspend fun exerciseSessions(
        chapters: List<LibraryChapter>,
        pageCounts: Map<Long, Int>,
        targetChapterId: Long,
        expectedRestoredPage: Int,
        desiredPage: Int,
        factory: mihon.desktop.reader.DesktopReaderFactory,
    ) {
        check(chapters.size == ReadingMode.entries.size) { "fixture must map one chapter to each reading mode" }
        val session = factory.createSession()
        try {
            chapters.zip(ReadingMode.entries).forEach { (chapter, mode) ->
                session.open(chapter.id)
                val ready = session.awaitReady(chapter.id)
                check(ready.pages.size == pageCounts.getValue(chapter.id)) { "session/source page counts disagree" }
                session.dispatch(ReaderAction.ChangeMode(mode))
                session.awaitState {
                    it.chapterId == chapter.id && it.mode == mode &&
                        it.loadState is ReaderLoadState.Ready
                }
            }

            val transition = chapters.single { it.name == TRANSITION_CHAPTER_NAME }
            session.open(transition.id)
            val transitionReady = session.awaitReady(transition.id)
            check(transitionReady.hasNextChapter) { "transition chapter has no next readable chapter" }
            session.dispatch(ReaderAction.SelectPage(transitionReady.pages.lastIndex))
            session.awaitState { it.chapterId == transition.id && it.selectedIndex == transitionReady.pages.lastIndex }
            session.dispatch(ReaderAction.Next)
            session.awaitState {
                it.chapterId != transition.id && it.chapterId != null && it.loadState is ReaderLoadState.Ready
            }

            session.open(targetChapterId)
            val restored = session.awaitReady(targetChapterId)
            check(restored.selectedIndex == expectedRestoredPage) { "reader did not restore persisted progress" }
            session.dispatch(ReaderAction.SelectPage(desiredPage))
            session.awaitState { it.chapterId == targetChapterId && it.selectedIndex == desiredPage }
            session.flushProgress()
        } finally {
            session.closeAndFlush()
        }

        val reopened = factory.createSession()
        try {
            reopened.open(targetChapterId)
            check(reopened.awaitReady(targetChapterId).selectedIndex == desiredPage) {
                "reader did not reopen at the flushed page"
            }
        } finally {
            reopened.closeAndFlush()
        }
    }

    private suspend fun ReaderSession.awaitReady(chapterId: Long) =
        awaitState { it.chapterId == chapterId && it.loadState is ReaderLoadState.Ready }

    private suspend fun ReaderSession.awaitState(predicate: (mihon.reader.session.ReaderState) -> Boolean) =
        withTimeout(10_000) { state.first(predicate) }

    private suspend fun verifyAsset(
        label: String,
        asset: ReaderChapterAsset,
        factory: mihon.desktop.reader.DesktopReaderFactory,
        scope: CoroutineScope,
    ): AssetVerification {
        val source = factory.sourceFactory.create(asset)
        source.use { activeSource ->
            PageLoadCoordinator(factory.decoder, factory.cache, scope).use { coordinator ->
                val pages = coordinator.openChapter(activeSource)
                check(pages.isNotEmpty()) { "$label exposed no pages" }
                var decoded = 0
                var corrupt = false
                var successfulAfterCorrupt = false
                val gifHashes = linkedSetOf<String>()
                pages.forEach { page ->
                    val lowerName = page.id.entryName.lowercase()
                    val expectedCorrupt = lowerName.substringAfterLast('/').startsWith("35-corrupt.") || label == "cbr"
                    if (expectedCorrupt) {
                        val outcome = runCatching { coordinator.loadVisible(page.id) }
                        val failure = outcome.exceptionOrNull()
                        check(failure is ReaderFailure.CorruptImage || failure is ReaderFailure.UnsupportedImage) {
                            "$label corrupt page did not fail as a typed image error; actual=" +
                                "${failure?.javaClass?.name}:${failure?.message}; page=${page.id}; " +
                                "decoded=${outcome.getOrNull()?.tile?.image?.let(::hashImage)}"
                        }
                        corrupt = true
                        return@forEach
                    }
                    if (lowerName.contains("extreme")) {
                        val loaded = coordinator.loadRegion(
                            page.id,
                            IntRect(0, 0, EXTREME_TILE_SIZE, EXTREME_TILE_SIZE),
                        )
                        check(loaded.tile.image.width == EXTREME_TILE_SIZE)
                        check(loaded.tile.image.height == EXTREME_TILE_SIZE)
                        check(
                            (loaded.tile.image.getRGB(0, EXTREME_TILE_SIZE - 1) and 0xff) ==
                                (EXTREME_TILE_SIZE - 1) % 251,
                        )
                        decoded++
                        if (corrupt) successfulAfterCorrupt = true
                        return@forEach
                    }

                    val metadata = factory.decoder.probe(activeSource.open(page.id))
                    if (metadata.frameCount > 1) {
                        val first = coordinator.loadVisible(page.id, 0)
                        gifHashes += hashImage(first.tile.image)
                        delay(metadata.frameDurationsMillis.first().coerceAtLeast(20L))
                        val second = coordinator.loadVisible(page.id, 1)
                        gifHashes += hashImage(second.tile.image)
                        check(gifHashes.size == 2) { "$label GIF frames were identical" }
                        decoded += 2
                    } else {
                        val loaded = coordinator.loadVisible(page.id)
                        check(loaded.tile.image.width > 0 && loaded.tile.image.height > 0)
                        if (lowerName.contains("transparent")) {
                            check((loaded.tile.image.getRGB(0, 0) ushr 24) == 0) {
                                "$label transparent page lost alpha"
                            }
                        }
                        if (lowerName.contains("opaque")) {
                            check((loaded.tile.image.getRGB(0, 0) ushr 24) == 0xff) {
                                "$label opaque page gained transparency"
                            }
                        }
                        decoded++
                    }
                    if (corrupt) successfulAfterCorrupt = true
                }
                return AssetVerification(pages.size, decoded, gifHashes.toList(), corrupt, successfulAfterCorrupt)
            }
        }
    }

    private fun validateFixture(requestedRoot: Path): ValidatedReaderFixture {
        val root = requestedRoot.toAbsolutePath().normalize()
        check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "reader fixture root is not a directory" }
        val manifestPath = root.resolve(READER_FIXTURE_MANIFEST)
        check(Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) { "reader fixture manifest is missing" }
        val manifest = FIXTURE_JSON.decodeFromString<ReaderFixtureManifest>(Files.readString(manifestPath, UTF_8))
        check(manifest.format == READER_FIXTURE_FORMAT)
        check(manifest.mangaDirectory == READER_FIXTURE_MANGA)
        check(manifest.standaloneImage == READER_FIXTURE_STANDALONE)
        check(manifest.committedRarPath == COMMITTED_RAR_PATH)
        check(manifest.committedRarSha256 == COMMITTED_RAR_SHA256)
        check(manifest.committedSourcePath == COMMITTED_SOURCE_PATH)
        check(manifest.committedSourceSha256 == COMMITTED_SOURCE_SHA256)
        check(manifest.files == manifest.files.sortedBy { it.relativePath }) { "fixture manifest files are not sorted" }
        check(manifest.files.map { it.relativePath }.distinct().size == manifest.files.size) {
            "fixture manifest contains duplicate paths"
        }
        manifest.files.forEach { record ->
            val relative = safeRelativePath(record.relativePath)
            val file = root.resolve(relative).normalize()
            check(file.startsWith(root) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "fixture file is missing or unsafe"
            }
            check(Files.size(file) == record.sizeBytes) { "fixture file size mismatch" }
            check(sha256(file) == record.sha256) { "fixture file SHA-256 mismatch" }
        }
        val paths = manifest.files.mapTo(mutableSetOf()) { it.relativePath }
        check(paths.containsAll(REQUIRED_FIXTURE_PATHS)) { "fixture manifest does not contain the required matrix" }
        val rarRecord = manifest.files.single { it.relativePath == "$READER_FIXTURE_MANGA/05-pages.cbr" }
        check(rarRecord.sha256 == COMMITTED_RAR_SHA256) { "fixture RAR is not the committed Task 3 resource" }
        val mangaRoot = root.resolve(READER_FIXTURE_MANGA)
        val standalone = root.resolve(READER_FIXTURE_STANDALONE)
        check(Files.isDirectory(mangaRoot, LinkOption.NOFOLLOW_LINKS))
        check(Files.isRegularFile(standalone, LinkOption.NOFOLLOW_LINKS))
        return ValidatedReaderFixture(root, mangaRoot, standalone, sha256(manifestPath))
    }
}

private data class ValidatedReaderFixture(
    val root: Path,
    val mangaRoot: Path,
    val standalone: Path,
    val manifestSha256: String,
)

private data class AssetVerification(
    val pageCount: Int,
    val decodedTileCount: Int,
    val gifFrameHashes: List<String>,
    val corruptPageObserved: Boolean,
    val decodedAfterCorrupt: Boolean,
)

private class CoreMemoryHighWater(
    private val snapshot: () -> ReaderMemoryMetrics,
) : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val highWater = AtomicLong(0)
    private val poller = thread(name = "mihon-reader-memory-verifier", isDaemon = true) {
        while (running.get()) {
            sample()
            LockSupport.parkNanos(500_000)
        }
        sample()
    }

    fun stop(): Long {
        close()
        return highWater.get()
    }

    override fun close() {
        if (running.compareAndSet(true, false)) poller.join()
    }

    private fun sample() {
        val metrics = snapshot()
        val total = Math.addExact(metrics.reservedBytes, metrics.cacheBytes)
        highWater.accumulateAndGet(total, ::maxOf)
    }
}

private fun assetLabel(name: String): String = when {
    name == TARGET_CHAPTER_NAME -> "directory"
    name.endsWith(".cbz") -> "cbz"
    name.endsWith(".cbt") -> "cbt"
    name.endsWith(".cb7") -> "cb7"
    name.endsWith(".cbr") -> "cbr"
    name.endsWith(".epub") -> "epub"
    else -> error("unknown reader fixture chapter")
}

private fun hashImage(image: BufferedImage): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val row = IntArray(image.width)
    repeat(image.height) { y ->
        image.getRGB(0, y, image.width, 1, row, 0, image.width)
        row.forEach { pixel ->
            digest.update((pixel ushr 24).toByte())
            digest.update((pixel ushr 16).toByte())
            digest.update((pixel ushr 8).toByte())
            digest.update(pixel.toByte())
        }
    }
    return digest.digest().toHex()
}

private fun safeRelativePath(value: String): Path {
    check(value.isNotBlank() && '\\' !in value) { "fixture path is not portable" }
    val path = Path.of(value)
    check(!path.isAbsolute && path.normalize() == path && path.none { it.toString() == ".." }) {
        "fixture path is unsafe"
    }
    return path
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun DesktopCommand.commandName(): String = when (this) {
    DesktopCommand.LaunchUi -> "launch-ui"
    DesktopCommand.FoundationSmoke -> "foundation-smoke"
    is DesktopCommand.ImportBackup -> "import-backup"
    is DesktopCommand.ExportBackup -> "export-backup"
    is DesktopCommand.ImportLocal -> "import-local"
    DesktopCommand.ListLibraryJson -> "list-library"
    is DesktopCommand.VerifyReader -> "verify-reader"
}

private fun DesktopCommand.sourcePath(): String? = when (this) {
    is DesktopCommand.ImportBackup -> path.toString()
    is DesktopCommand.ExportBackup -> path.toString()
    is DesktopCommand.ImportLocal -> path.toString()
    is DesktopCommand.VerifyReader -> fixtureRoot.toString()
    else -> null
}

private fun writeUtf8Line(output: OutputStream, value: String) {
    output.write(value.toByteArray(UTF_8))
    output.write('\n'.code)
    output.flush()
}

private const val READER_FIXTURE_FORMAT = "mihon-w-reader-fixture-v1"
private const val READER_FIXTURE_MANIFEST = "reader-fixture-manifest.json"
private const val READER_FIXTURE_MANGA = "reader-fixture-manga"
private const val READER_FIXTURE_STANDALONE = "standalone.png"
private const val COMMITTED_RAR_PATH =
    "reader-core/src/test/resources/mihon/reader/source/fixtures/valid-rar4.rar"
private const val COMMITTED_RAR_SHA256 = "ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693"
private const val COMMITTED_SOURCE_PATH = ".superpowers/sdd/task-3-fixture-source/page.png"
private const val COMMITTED_SOURCE_SHA256 = "0197651b31b314f8ee97130efa7427813db8520c65a373a0edc48cda884b0317"
private const val TARGET_CHAPTER_NAME = "01-directory"
private const val TRANSITION_CHAPTER_NAME = "06-pages.epub"
private const val STANDALONE_CHAPTER_ID = 9_000_000_000L
private const val EXTREME_TILE_SIZE = 1024
private val EXPECTED_CHAPTER_NAMES = setOf(
    TARGET_CHAPTER_NAME,
    "02-pages.cbz",
    "03-pages.cbt",
    "04-pages.cb7",
    "05-pages.cbr",
    TRANSITION_CHAPTER_NAME,
)
private val EXPECTED_ASSET_LABELS = linkedSetOf("standalone", "directory", "cbz", "cbt", "cb7", "cbr", "epub")
private val REQUIRED_FIXTURE_PATHS = setOf(
    READER_FIXTURE_STANDALONE,
    "$READER_FIXTURE_MANGA/01-directory/40-extreme.png",
    "$READER_FIXTURE_MANGA/02-pages.cbz",
    "$READER_FIXTURE_MANGA/03-pages.cbt",
    "$READER_FIXTURE_MANGA/04-pages.cb7",
    "$READER_FIXTURE_MANGA/05-pages.cbr",
    "$READER_FIXTURE_MANGA/06-pages.epub",
)
private val FIXTURE_JSON = Json {
    encodeDefaults = true
    explicitNulls = true
}
