package mihon.desktop.library.local

import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItem
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import java.nio.file.Files
import java.nio.file.Path

const val LOCAL_SOURCE_ID = 0L

interface LocalImportCheckpoint {
    fun afterScan(manifest: LocalImportManifest) = Unit
    fun afterPromotion(staged: StagedLocalManga) = Unit
    fun beforeReport() = Unit

    companion object {
        val NONE = object : LocalImportCheckpoint {}
    }
}

class LocalMangaImporter(
    private val mutations: LibraryMutationPort,
    private val scanner: LocalImportScanner = LocalImportScanner(),
    private val stager: LocalImportStager = LocalImportStager(scanner),
    private val checkpoint: LocalImportCheckpoint = LocalImportCheckpoint.NONE,
) {
    fun import(sourceDirectory: Path, localLibraryRoot: Path, nowMillis: Long): ImportReport {
        val manifest = scanner.scan(sourceDirectory)
        checkpoint.afterScan(manifest)
        val existing = mutations.findLocalMangaByManifest(manifest.sha256)
        if (existing != null) {
            val existingPath = validateExistingLocalManga(existing, manifest, localLibraryRoot)
            return mutations.transaction {
                val current = findLocalMangaByManifest(manifest.sha256)
                    ?: rejectImport("local manga registration disappeared during import")
                if (current.mangaId != existing.mangaId || Path.of(current.storagePath) != existingPath) {
                    rejectImport("local manga registration changed during import")
                }
                register(manifest, existingPath, nowMillis, current)
            }
        }

        val staged = stager.stage(manifest, localLibraryRoot)
        staged.use {
            var promotedByThisImport = false
            val report = mutations.transaction {
                val concurrent = findLocalMangaByManifest(manifest.sha256)
                val storagePath = if (concurrent == null) {
                    if (findManga(LOCAL_SOURCE_ID, mangaUrl(manifest)) != null) {
                        rejectImport("local manga identity already exists without a local registration")
                    }
                    val promotedPath = staged.promote()
                    promotedByThisImport = true
                    checkpoint.afterPromotion(staged)
                    promotedPath
                } else {
                    validateExistingLocalManga(concurrent, manifest, localLibraryRoot)
                }
                register(manifest, storagePath, nowMillis, concurrent)
            }
            if (promotedByThisImport) staged.markCommitted()
            return report
        }
    }

    fun cleanupOrphans(localLibraryRoot: Path) {
        val retained = mutations.localMangaStoragePaths().mapTo(mutableSetOf()) { storagePath ->
            try {
                Path.of(storagePath)
            } catch (error: RuntimeException) {
                throw LocalImportRejected("invalid local manga storage path in database", error)
            }
        }
        stager.cleanupOrphans(localLibraryRoot, retained)
    }

    private fun validateExistingLocalManga(
        existing: LocalMangaRecord,
        manifest: LocalImportManifest,
        localLibraryRoot: Path,
    ): Path {
        val mangaRoot = localLibraryRoot.toAbsolutePath().normalize().resolve("manga")
        val path = try {
            Path.of(existing.storagePath).toAbsolutePath().normalize()
        } catch (error: RuntimeException) {
            throw LocalImportRejected("invalid existing local manga storage path", error)
        }
        if (path.parent != mangaRoot) rejectImport("existing local manga path is outside the local manga root")
        val attributes = readAttributes(path, "existing local manga")
        if (!attributes.isDirectory || isLinkOrReparsePoint(path, attributes) || !Files.isReadable(path)) {
            rejectImport("existing local manga path is not a safe readable directory")
        }
        val storedManifest = scanner.scan(path)
        if (storedManifest.sha256 != manifest.sha256 || storedManifest.chapters != manifest.chapters) {
            rejectImport("existing local manga media does not match its manifest")
        }
        return path
    }

    private fun LibraryMutationPort.register(
        manifest: LocalImportManifest,
        storagePath: Path,
        nowMillis: Long,
        existingLocal: LocalMangaRecord?,
    ): ImportReport {
        val url = mangaUrl(manifest)
        val existingManga = findManga(LOCAL_SOURCE_ID, url)
        if (existingLocal != null && (existingManga == null || existingManga.id != existingLocal.mangaId)) {
            rejectImport("local manga database identity is inconsistent")
        }
        val mangaId: Long
        val mangaInserted: Long
        val mangaMerged: Long
        if (existingManga == null) {
            mangaId = insertManga(
                MangaRecord(
                    sourceId = LOCAL_SOURCE_ID,
                    url = url,
                    title = manifest.title,
                    favorite = true,
                    dateAdded = nowMillis,
                    lastModifiedAt = nowMillis,
                    initialized = true,
                ),
            )
            mangaInserted = 1
            mangaMerged = 0
        } else {
            updateManga(existingManga.copy(title = manifest.title, favorite = true))
            mangaId = existingManga.id
            mangaInserted = 0
            mangaMerged = 1
        }

        var chaptersInserted = 0L
        var chaptersMerged = 0L
        val reportItems = mutableListOf<ImportReportItem>()
        manifest.chapters.forEachIndexed { index, chapter ->
            val chapterUrl = chapterUrl(chapter.relativePath)
            val existingChapter = findChapter(mangaId, chapterUrl)
            val chapterId = if (existingChapter == null) {
                chaptersInserted++
                insertChapter(
                    ChapterRecord(
                        mangaId = mangaId,
                        url = chapterUrl,
                        name = chapter.name,
                        dateFetch = nowMillis,
                        sourceOrder = index.toLong(),
                        lastModifiedAt = chapter.modifiedAt,
                    ),
                )
            } else {
                chaptersMerged++
                updateChapter(
                    existingChapter.copy(
                        name = chapter.name,
                        sourceOrder = index.toLong(),
                        lastModifiedAt = maxOf(existingChapter.lastModifiedAt, chapter.modifiedAt),
                    ),
                )
                existingChapter.id
            }
            insertLocalChapter(
                LocalChapterRecord(
                    chapterId = chapterId,
                    relativePath = portablePath(chapter.relativePath),
                    assetKind = chapter.kind.name,
                    sizeBytes = chapter.sizeBytes,
                    modifiedAt = chapter.modifiedAt,
                ),
            )
            reportItems += ImportReportItem(
                itemType = "CHAPTER",
                itemKey = portablePath(chapter.relativePath),
                outcome = if (existingChapter == null) "IMPORTED" else "MERGED",
                message = if (existingChapter == null) "Imported local chapter" else "Merged local chapter",
            )
        }
        insertLocalManga(
            LocalMangaRecord(
                mangaId = mangaId,
                storagePath = storagePath.toString(),
                manifestSha256 = manifest.sha256,
                importedAt = existingLocal?.importedAt ?: nowMillis,
            ),
        )
        checkpoint.beforeReport()
        val report = ImportReport(
            importType = ImportType.LOCAL_DIRECTORY,
            sourcePath = manifest.sourceRoot.toString(),
            status = ImportStatus.SUCCEEDED,
            startedAt = nowMillis,
            finishedAt = nowMillis,
            counts = ImportCounts(
                mangaInserted = mangaInserted,
                mangaMerged = mangaMerged,
                chaptersInserted = chaptersInserted,
                chaptersMerged = chaptersMerged,
            ),
            items = reportItems,
        )
        val reportId = insertReport(report.toRecord())
        report.items.forEach { insertReportItem(reportId, it.toRecord()) }
        return report.copy(id = reportId)
    }
}

private fun mangaUrl(manifest: LocalImportManifest): String = "local:${manifest.sha256}"

private fun chapterUrl(relativePath: Path): String = "local:${portablePath(relativePath)}"

private fun ImportReport.toRecord() = ImportReportRecord(
    importType = importType,
    sourcePath = sourcePath,
    status = status,
    startedAt = startedAt,
    finishedAt = finishedAt,
    counts = counts,
)

private fun ImportReportItem.toRecord() = ImportReportItemRecord(
    itemType = itemType,
    itemKey = itemKey,
    outcome = outcome,
    reason = reason,
    message = message,
)

private fun rejectImport(message: String): Nothing = throw LocalImportRejected(message)
