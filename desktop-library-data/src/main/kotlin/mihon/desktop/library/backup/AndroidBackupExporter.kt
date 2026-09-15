@file:OptIn(ExperimentalSerializationApi::class)

package mihon.desktop.library.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import java.nio.file.Path

class AndroidBackupExporter(
    private val repository: LibraryRepository,
    private val codec: AndroidBackupCodec = AndroidBackupCodec(),
    private val json: Json = Json,
) {
    fun export(path: Path) {
        val backup = createBackup()
        codec.encode(backup, path)
    }

    fun createBackup(): AndroidBackup {
        val transactional = repository as? LibraryMutationPort
        return if (transactional != null) {
            transactional.transaction { createSnapshot() }
        } else {
            createSnapshot()
        }
    }

    private fun createSnapshot(): AndroidBackup {
        val mangas = repository.allMangaSnapshot()
        val chapters = repository.allChaptersSnapshot()
        val categories = repository.allCategoriesSnapshot()
        val mangaCategories = repository.mangaCategoryLinksSnapshot()
        val history = repository.allHistorySnapshot()
        val tracking = repository.allTrackingSnapshot()
        val sources = repository.allSourcesSnapshot()
        val preferences = repository.allPreferenceSnapshots()
        val sourcePreferences = repository.allSourcePreferenceSnapshots()

        val chaptersByMangaId = chapters.groupBy { it.mangaId }
        val trackingByMangaId = tracking.groupBy { it.mangaId }
        val historyByChapterId = history.associateBy { it.chapterId }

        // Desktop merges can leave equal sort orders. Wire category references are orders, not IDs.
        val categoryOrderById = if (categories.map { it.sortOrder }.distinct().size == categories.size) {
            categories.associate { it.id to it.sortOrder }
        } else {
            categories.sortedWith(compareBy<CategoryRecord> { it.sortOrder }.thenBy { it.id })
                .mapIndexed { index, category -> category.id to index.toLong() }.toMap()
        }

        val backupManga = mangas.map { manga ->
            val mangaChapters = chaptersByMangaId[manga.id].orEmpty()
            val mangaTracking = trackingByMangaId[manga.id].orEmpty()
            val categoryIds = mangaCategories[manga.id].orEmpty()

            val backupChapters = mangaChapters.map { ch ->
                val memoBytes = try {
                    ch.memoJson.encodeToByteArray()
                } catch (_: Throwable) {
                    byteArrayOf(123, 125)
                }
                AndroidBackupChapter(
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.lastPageRead,
                    dateFetch = ch.dateFetch,
                    dateUpload = ch.dateUpload,
                    chapterNumber = ch.chapterNumber.toFloat(),
                    sourceOrder = ch.sourceOrder,
                    lastModifiedAt = ch.lastModifiedAt,
                    version = ch.version,
                    memo = memoBytes,
                )
            }

            val backupHistory = mangaChapters.mapNotNull { ch ->
                historyByChapterId[ch.id]?.let { h ->
                    AndroidBackupHistory(
                        url = ch.url,
                        lastRead = h.lastRead,
                        readDuration = h.readDuration,
                    )
                }
            }

            val backupTrack = mangaTracking.map { t ->
                AndroidBackupTracking(
                    syncId = t.trackerId.toInt(),
                    libraryId = t.libraryId,
                    mediaId = t.remoteId,
                    trackingUrl = t.trackingUrl,
                    title = t.title,
                    lastChapterRead = t.lastChapterRead.toFloat(),
                    totalChapters = t.totalChapters.toInt(),
                    score = t.score.toFloat(),
                    status = t.status.toInt(),
                    startedReadingDate = t.startedReadingDate,
                    finishedReadingDate = t.finishedReadingDate,
                    private = t.private,
                )
            }

            val genres = try {
                json.parseToJsonElement(manga.genreJson).jsonArray.map { it.jsonPrimitive.content }
            } catch (_: Throwable) {
                emptyList()
            }

            val excludedScanlators = try {
                json.parseToJsonElement(manga.excludedScanlatorsJson).jsonArray.map { it.jsonPrimitive.content }
            } catch (_: Throwable) {
                emptyList()
            }

            val memoBytes = try {
                manga.memoJson.encodeToByteArray()
            } catch (_: Throwable) {
                byteArrayOf(123, 125)
            }

            AndroidBackupManga(
                source = manga.sourceId,
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = genres,
                status = manga.status.toInt(),
                thumbnailUrl = manga.thumbnailUrl,
                dateAdded = manga.dateAdded,
                viewer = manga.viewerFlags.toInt(),
                chapters = backupChapters,
                categories = categoryIds.mapNotNull { categoryOrderById[it] },
                tracking = backupTrack,
                favorite = manga.favorite,
                chapterFlags = manga.chapterFlags.toInt(),
                viewerFlags = manga.viewerFlags.toInt(),
                history = backupHistory,
                updateStrategy = try {
                    AndroidUpdateStrategy.valueOf(manga.updateStrategy)
                } catch (_: Throwable) {
                    AndroidUpdateStrategy.ALWAYS_UPDATE
                },
                lastModifiedAt = manga.lastModifiedAt,
                favoriteModifiedAt = manga.favoriteModifiedAt,
                excludedScanlators = excludedScanlators,
                version = manga.version,
                notes = manga.notes,
                initialized = manga.initialized,
                memo = memoBytes,
            )
        }

        val backupCategories = categories.map { cat ->
            AndroidBackupCategory(
                name = cat.name,
                order = categoryOrderById.getValue(cat.id),
                id = cat.id,
                flags = cat.flags,
            )
        }

        val backupSources = sources.map { src ->
            AndroidBackupSource(
                name = src.name,
                sourceId = src.sourceId,
            )
        }

        val backupPreferences = preferences.mapNotNull { pref ->
            decodePreferenceValue(pref.valueType, pref.valueJson)?.let { value ->
                AndroidBackupPreference(key = pref.key, value = value)
            }
        }

        val sourcePreferencesBySource = sourcePreferences.groupBy { it.sourceKey }
        val backupSourcePreferences = sourcePreferencesBySource.map { (sourceKey, prefs) ->
            AndroidBackupSourcePreferences(
                sourceKey = sourceKey,
                prefs = prefs.mapNotNull { pref ->
                    decodePreferenceValue(pref.valueType, pref.valueJson)?.let { value ->
                        AndroidBackupPreference(key = pref.key, value = value)
                    }
                },
            )
        }

        return AndroidBackup(
            backupManga = backupManga,
            backupCategories = backupCategories,
            backupSources = backupSources,
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,
        )
    }

    private fun decodePreferenceValue(type: String, jsonStr: String): AndroidPreferenceValue? = try {
        when (type) {
            "INT" -> AndroidIntPreferenceValue(jsonStr.trim().toInt())
            "LONG" -> AndroidLongPreferenceValue(jsonStr.trim().toLong())
            "FLOAT" -> AndroidFloatPreferenceValue(jsonStr.trim().toFloat())
            "STRING" -> {
                val element = json.parseToJsonElement(jsonStr)
                AndroidStringPreferenceValue(element.jsonPrimitive.content)
            }
            "BOOLEAN" -> AndroidBooleanPreferenceValue(jsonStr.trim().toBoolean())
            "STRING_SET" -> {
                val array = json.parseToJsonElement(jsonStr).jsonArray
                AndroidStringSetPreferenceValue(array.map { it.jsonPrimitive.content }.toSet())
            }
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}
