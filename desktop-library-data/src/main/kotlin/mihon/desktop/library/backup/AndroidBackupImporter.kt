package mihon.desktop.library.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItem
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSkipReason
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import java.nio.file.Path
import java.util.Locale

class AndroidBackupImporter(
    private val codec: AndroidBackupCodec,
    private val validator: AndroidBackupValidator,
    private val mutations: LibraryMutationPort,
    private val preferences: SupportedPreferencePolicy = SupportedPreferencePolicy(),
    private val checkpoint: ImportCheckpoint = ImportCheckpoint.NONE,
) {
    fun import(path: Path, nowMillis: Long): ImportReport {
        val validated = validator.validate(codec.decode(path))
        return mutations.transaction {
            val accumulator = ImportAccumulator(path, nowMillis)
            mergeCategoriesSourcesMangaChildrenAndPreferences(validated, accumulator, nowMillis)
            checkpoint.beforeReport()
            val report = accumulator.success(nowMillis)
            val reportId = insertReport(report.toRecord())
            report.items.forEach { insertReportItem(reportId, it.toRecord()) }
            report.copy(id = reportId)
        }
    }

    private fun LibraryMutationPort.mergeCategoriesSourcesMangaChildrenAndPreferences(
        validated: ValidatedAndroidBackup,
        accumulator: ImportAccumulator,
        nowMillis: Long,
    ) {
        val backup = validated.backup
        val databaseCategoryIdByOrder = mutableMapOf<Long, Long>()
        val databaseCategoryIdByName = mutableMapOf<String, Long>()
        val backupCategoryNameById = mutableMapOf<Long, String>()
        backup.backupCategories.forEach { category ->
            val databaseId =
                upsertCategory(CategoryRecord(name = category.name, sortOrder = category.order, flags = category.flags))
            databaseCategoryIdByOrder[category.order] = databaseId
            databaseCategoryIdByName[category.name.lowercase(Locale.ROOT)] = databaseId
            backupCategoryNameById[category.id] = category.name
        }

        backup.backupSources.forEach { source ->
            upsertSource(SourceRecord(source.sourceId, source.name, nowMillis))
        }

        backup.backupManga.forEachIndexed { mangaIndex, manga ->
            val incomingManga = manga.toRecord(validated.mangaMemoJson[mangaIndex])
            val existingManga = findManga(manga.source, manga.url)
            val mangaId = if (existingManga == null) {
                accumulator.mangaInserted++
                insertManga(incomingManga)
            } else {
                accumulator.mangaMerged++
                updateManga(BackupMergePolicy.mergeManga(existingManga, incomingManga))
                existingManga.id
            }

            manga.categories.forEach { categoryOrder ->
                linkCategory(mangaId, checkNotNull(databaseCategoryIdByOrder[categoryOrder]))
                accumulator.categoriesLinked++
            }

            val chapterIdByUrl = mutableMapOf<String, Long>()
            manga.chapters.forEachIndexed { chapterIndex, chapter ->
                val incomingChapter = chapter.toRecord(mangaId, validated.chapterMemoJson[mangaIndex][chapterIndex])
                val existingChapter = findChapter(mangaId, chapter.url)
                val chapterId = if (existingChapter == null) {
                    accumulator.chaptersInserted++
                    insertChapter(incomingChapter)
                } else {
                    accumulator.chaptersMerged++
                    updateChapter(BackupMergePolicy.mergeChapter(existingChapter, incomingChapter))
                    existingChapter.id
                }
                chapterIdByUrl[chapter.url] = chapterId
            }

            manga.history.forEach { history ->
                val incoming = HistoryRecord(
                    chapterId = checkNotNull(chapterIdByUrl[history.url]),
                    lastRead = history.lastRead,
                    readDuration = history.readDuration,
                )
                upsertHistory(incoming)
            }

            manga.tracking.forEach { tracking ->
                val incoming = tracking.toRecord(mangaId)
                val existing = findTracking(mangaId, tracking.syncId.toLong())
                if (existing == null) {
                    insertTracking(incoming)
                } else {
                    updateTracking(BackupMergePolicy.mergeTracking(existing, incoming))
                }
            }
        }

        backup.backupPreferences.forEach { preference ->
            val decision = preferences.classifyApp(preference.key, preference.value)
            val remapped = remapCategoryPreference(
                preference,
                decision,
                backupCategoryNameById,
                databaseCategoryIdByName,
            )
            when (remapped) {
                is PreferenceDecision.Import -> {
                    upsertPreference(
                        PreferenceSnapshotRecord(preference.key, remapped.type, remapped.canonicalJson, nowMillis),
                    )
                    accumulator.preferencesImported++
                }
                is PreferenceDecision.Skip -> accumulator.skipPreference("app", preference.key, remapped.reason)
            }
        }

        backup.backupSourcePreferences.forEach { sourcePreferences ->
            sourcePreferences.prefs.forEach { preference ->
                when (
                    val decision = preferences.classifySource(
                        sourcePreferences.sourceKey,
                        preference.key,
                        preference.value,
                    )
                ) {
                    is PreferenceDecision.Import -> {
                        upsertSourcePreference(
                            SourcePreferenceSnapshotRecord(
                                sourceKey = sourcePreferences.sourceKey,
                                key = preference.key,
                                valueType = decision.type,
                                valueJson = decision.canonicalJson,
                                importedAt = nowMillis,
                            ),
                        )
                        accumulator.preferencesImported++
                    }
                    is PreferenceDecision.Skip -> accumulator.skipPreference(
                        scope = "source/${sourcePreferences.sourceKey}",
                        key = preference.key,
                        reason = decision.reason,
                        itemType = "SOURCE_PREFERENCE",
                    )
                }
            }
        }

        backup.backupExtensionStores.forEachIndexed { index, _ ->
            accumulator.items += ImportReportItem(
                itemType = "EXTENSION_STORE",
                itemKey = "extension-store[$index]",
                outcome = "SKIPPED",
                reason = PreferenceSkipReason.UNKNOWN.name,
                message = "Extension store installation is outside Plan 2",
            )
        }
    }

    private fun remapCategoryPreference(
        preference: AndroidBackupPreference,
        decision: PreferenceDecision,
        backupCategoryNameById: Map<Long, String>,
        databaseCategoryIdByName: Map<String, Long>,
    ): PreferenceDecision {
        if (decision !is PreferenceDecision.Import) return decision
        return when (preference.key) {
            "default_category" -> {
                val backupId = (preference.value as AndroidIntPreferenceValue).value.toLong()
                val databaseId = backupCategoryNameById[backupId]
                    ?.lowercase(Locale.ROOT)
                    ?.let(databaseCategoryIdByName::get)
                    ?: return PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
                PreferenceDecision.Import("INT", databaseId.toString())
            }
            "library_update_categories", "library_update_categories_exclude" -> {
                val databaseIds = linkedSetOf<String>()
                (preference.value as AndroidStringSetPreferenceValue).value.forEach { backupId ->
                    val databaseId = backupId.toLongOrNull()
                        ?.let(backupCategoryNameById::get)
                        ?.lowercase(Locale.ROOT)
                        ?.let(databaseCategoryIdByName::get)
                        ?: return PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
                    databaseIds += databaseId.toString()
                }
                AndroidStringSetPreferenceValue(databaseIds).toImportDecision()
                    ?: PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
            }
            else -> decision
        }
    }
}

fun interface ImportCheckpoint {
    fun beforeReport()

    companion object {
        val NONE = ImportCheckpoint {}
    }
}

private class ImportAccumulator(
    private val path: Path,
    private val startedAt: Long,
) {
    var mangaInserted = 0L
    var mangaMerged = 0L
    var chaptersInserted = 0L
    var chaptersMerged = 0L
    var categoriesLinked = 0L
    var preferencesImported = 0L
    var preferencesSkipped = 0L
    val items = mutableListOf<ImportReportItem>()

    fun skipPreference(
        scope: String,
        key: String,
        reason: PreferenceSkipReason,
        itemType: String = "PREFERENCE",
    ) {
        preferencesSkipped++
        items += ImportReportItem(
            itemType = itemType,
            itemKey = "$scope/$key",
            outcome = "SKIPPED",
            reason = reason.name,
            message = "Skipped $scope preference '$key': ${reason.name}",
        )
    }

    fun success(finishedAt: Long) = ImportReport(
        importType = ImportType.ANDROID_BACKUP,
        sourcePath = path.toString(),
        status = ImportStatus.SUCCEEDED,
        startedAt = startedAt,
        finishedAt = finishedAt,
        counts = ImportCounts(
            mangaInserted = mangaInserted,
            mangaMerged = mangaMerged,
            chaptersInserted = chaptersInserted,
            chaptersMerged = chaptersMerged,
            categoriesLinked = categoriesLinked,
            preferencesImported = preferencesImported,
            preferencesSkipped = preferencesSkipped,
        ),
        items = items.toList(),
    )
}

private fun AndroidBackupManga.toRecord(memoJson: String) = MangaRecord(
    sourceId = source,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = canonicalStringSetJson(genre),
    status = status.toLong(),
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    dateAdded = dateAdded,
    viewerFlags = (viewerFlags ?: viewer).toLong(),
    chapterFlags = chapterFlags.toLong(),
    updateStrategy = updateStrategy.name,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    excludedScanlatorsJson = canonicalStringSetJson(excludedScanlators),
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memoJson,
)

private fun AndroidBackupChapter.toRecord(mangaId: Long, memoJson: String) = ChapterRecord(
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber.toDouble(),
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    memoJson = memoJson,
)

@Suppress("DEPRECATION")
private fun AndroidBackupTracking.toRecord(mangaId: Long) = TrackingRecord(
    mangaId = mangaId,
    trackerId = syncId.toLong(),
    remoteId = if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId,
    libraryId = libraryId,
    title = title,
    lastChapterRead = lastChapterRead.toDouble(),
    totalChapters = totalChapters.toLong(),
    score = score.toDouble(),
    status = status.toLong(),
    startedReadingDate = startedReadingDate,
    finishedReadingDate = finishedReadingDate,
    private = private,
    trackingUrl = trackingUrl,
)

private fun canonicalStringSetJson(values: Iterable<String>) = JsonArray(
    values.toSet().sortedWith(UNICODE_CODE_POINT_COMPARATOR).map(::JsonPrimitive),
).toString()

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
