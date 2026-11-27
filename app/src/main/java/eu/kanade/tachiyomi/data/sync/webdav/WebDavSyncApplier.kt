package eu.kanade.tachiyomi.data.sync.webdav

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import mihon.core.common.extensions.EMPTY
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import kotlin.math.max
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
class WebDavSyncApplier(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val categoryRepository: CategoryRepository,
    private val trackRepository: TrackRepository,
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    private val downloadCache: DownloadCache,
) {
    suspend fun apply(
        local: WebDavSyncSnapshot,
        merged: WebDavSyncSnapshot,
        options: WebDavSyncOptions,
    ): Boolean {
        var changed = false

        if (options.syncExtensionStores) {
            if (applyExtensionStores(merged.extensionStores)) {
                changed = true
            }
        }

        if (options.syncLibrary) {
            applySourceNames(merged.sources)
            val categoryIdsByName = applyCategories(merged.categories)
            if (applyManga(local.manga, merged.manga, categoryIdsByName)) {
                changed = true
            }
            if (changed) {
                try {
                    downloadCache.invalidateCache()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after WebDAV sync" }
                }
            }
        }

        return changed
    }

    private suspend fun applyExtensionStores(mergedStores: List<SyncExtensionStore>): Boolean {
        val currentStores = extensionStoreRepository.getAll().associateBy { it.indexUrl }
        val mergedByUrl = mergedStores.associateBy { it.indexUrl }
        var changed = false

        for (syncStore in mergedStores) {
            currentCoroutineContext().ensureActive()
            val target = ExtensionStore(
                indexUrl = syncStore.indexUrl,
                name = syncStore.name,
                badgeLabel = syncStore.badgeLabel,
                signingKey = syncStore.signingKey,
                contact = ExtensionStore.Contact(
                    website = syncStore.website,
                    discord = syncStore.discord,
                ),
                isLegacy = syncStore.isLegacy,
                extensionListUrl = syncStore.extensionListUrl,
            )
            if (currentStores[syncStore.indexUrl] != target) {
                extensionStoreRepository.upsert(target)
                changed = true
            }
        }

        for (existingUrl in currentStores.keys) {
            if (existingUrl !in mergedByUrl) {
                extensionStoreRepository.remove(existingUrl)
                changed = true
            }
        }

        return changed
    }

    private suspend fun applySourceNames(sources: List<SyncSource>) {
        sources
            .filter { it.name.isNotBlank() }
            .filter {
                sourceManager.get(it.sourceId) == null && stubSourceRepository.getStubSource(it.sourceId) == null
            }
            .forEach {
                stubSourceRepository.upsertStubSource(it.sourceId, lang = "", name = it.name)
            }
    }

    private suspend fun applyCategories(mergedCategories: List<SyncCategory>): Map<String, Long> {
        val existingByName = categoryRepository.getAll()
            .filterNot { it.isSystemCategory }
            .associateBy { it.name }
        val mergedNames = mergedCategories.map { it.name }.toSet()

        val toInsert = mergedCategories
            .filter { it.name !in existingByName }
            .map { NewCategory(name = it.name, flags = it.flags) }
        if (toInsert.isNotEmpty()) {
            categoryRepository.insertAll(toInsert)
        }

        for (syncCategory in mergedCategories) {
            val existing = existingByName[syncCategory.name] ?: continue
            if (existing.flags != syncCategory.flags) {
                categoryRepository.updateFlags(existing.id, syncCategory.flags)
            }
        }

        for ((name, existing) in existingByName) {
            if (name !in mergedNames) {
                categoryRepository.delete(existing.id)
            }
        }

        val updatedCategories = categoryRepository.getAll()
            .filterNot { it.isSystemCategory }
        val byName = updatedCategories.associateBy { it.name }

        val desiredOrderIds = mergedCategories
            .sortedWith(compareBy<SyncCategory> { it.order }.thenBy { it.name })
            .mapNotNull { byName[it.name]?.id }
        val currentOrderIds = updatedCategories.sortedBy { it.order }.map { it.id }
        if (desiredOrderIds.isNotEmpty() && desiredOrderIds != currentOrderIds) {
            categoryRepository.updateAllOrders(desiredOrderIds)
        }

        return byName.mapValues { it.value.id }
    }

    private suspend fun applyManga(
        localMangaList: List<SyncManga>,
        mergedMangaList: List<SyncManga>,
        categoryIdsByName: Map<String, Long>,
    ): Boolean {
        val localByKey = localMangaList.associateBy { it.key() }
        val mergedByKey = mergedMangaList.associateBy { it.key() }
        var changed = false

        for (syncManga in mergedMangaList) {
            currentCoroutineContext().ensureActive()
            val localSnapshot = localByKey[syncManga.key()]
            if (localSnapshot != null && localSnapshot == syncManga) {
                continue
            }
            applySingleManga(syncManga, categoryIdsByName)
            changed = true
        }

        // Unfavorite or clear history for entries removed from merged snapshot
        for (localManga in localMangaList) {
            currentCoroutineContext().ensureActive()
            if (localManga.key() in mergedByKey) continue
            val dbManga = mangaRepository.getMangaByUrlAndSourceId(localManga.url, localManga.source) ?: continue
            if (dbManga.favorite) {
                mangaRepository.update(
                    MangaUpdate(dbManga.id) {
                        favoriteAt = null
                    },
                )
                changed = true
            }
            if (localManga.history.any { it.readAt != null }) {
                historyRepository.resetHistoryByMangaId(dbManga.id)
                changed = true
            }
        }

        return changed
    }

    private suspend fun applySingleManga(
        syncManga: SyncManga,
        categoryIdsByName: Map<String, Long>,
    ) {
        val existing = mangaRepository.getMangaByUrlAndSourceId(syncManga.url, syncManga.source)
        val manga = if (existing == null) {
            val inserted = mangaRepository.insertNetworkManga(listOf(syncManga.toDomainManga())).first()
            if (syncManga.notes.isNotBlank()) {
                mangaRepository.update(
                    MangaUpdate(inserted.id) {
                        notes = syncManga.notes
                    },
                )
            }
            inserted
        } else {
            updateExistingManga(existing, syncManga)
            existing
        }

        // Categories
        val desiredCategoryIds = syncManga.categories.mapNotNull { categoryIdsByName[it] }.sorted()
        val currentCategoryIds = categoryRepository.getCategoriesByMangaId(manga.id)
            .filterNot(Category::isSystemCategory)
            .map { it.id }
            .sorted()
        if (desiredCategoryIds != currentCategoryIds) {
            mangaRepository.setMangaCategories(manga.id, desiredCategoryIds)
        }

        // Excluded scanlators
        val desiredScanlators = syncManga.excludedScanlators.toSet()
        val currentScanlators = mangaRepository.getExcludedScanlators(manga.id)
        if (desiredScanlators != currentScanlators) {
            mangaRepository.setExcludedScanlators(manga.id, desiredScanlators)
        }

        // Chapters
        applyChapters(manga.id, syncManga.chapters)

        // History
        applyHistory(manga.id, syncManga.history)

        // Tracks
        applyTracks(manga.id, syncManga.tracks)
    }

    private suspend fun updateExistingManga(existing: Manga, syncManga: SyncManga) {
        val targetFavoriteAt = syncManga.favoriteAt?.let(Instant::fromEpochMilliseconds)
        val userChanged = existing.favoriteAt != targetFavoriteAt ||
            existing.viewerFlags != syncManga.viewerFlags ||
            existing.chapterFlags != syncManga.chapterFlags ||
            existing.notes != syncManga.notes

        if (userChanged) {
            mangaRepository.update(
                MangaUpdate(existing.id) {
                    favoriteAt = targetFavoriteAt
                    viewerFlags = syncManga.viewerFlags
                    chapterFlags = syncManga.chapterFlags
                    notes = syncManga.notes
                },
            )
        }

        val targetStrategy = UpdateStrategy.entries.getOrElse(syncManga.updateStrategy) {
            UpdateStrategy.ALWAYS_UPDATE
        }
        val remoteChanged = (!existing.initialized && syncManga.initialized) ||
            existing.title != syncManga.title ||
            existing.author != syncManga.author ||
            existing.artist != syncManga.artist ||
            existing.description != syncManga.description ||
            existing.genre != syncManga.genre ||
            existing.status != syncManga.status ||
            existing.thumbnailUrl != syncManga.thumbnailUrl ||
            existing.updateStrategy != targetStrategy

        if (remoteChanged) {
            mangaRepository.updateRemote(
                MangaRemoteUpdate(
                    id = existing.id,
                    title = syncManga.title,
                    author = syncManga.author,
                    artist = syncManga.artist,
                    description = syncManga.description,
                    genre = syncManga.genre,
                    status = syncManga.status,
                    thumbnailUrl = syncManga.thumbnailUrl,
                    updateStrategy = targetStrategy,
                    memo = existing.memo,
                    initialized = existing.initialized || syncManga.initialized,
                    coverLastModified = existing.coverLastModified,
                ),
            )
        }
    }

    private suspend fun applyChapters(mangaId: Long, syncChapters: List<SyncChapter>) {
        if (syncChapters.isEmpty()) return
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(mangaId).associateBy { it.url }

        val toInsert = mutableListOf<Chapter>()
        val toUpdate = mutableListOf<ChapterUpdate>()

        for (syncChapter in syncChapters) {
            val dbChapter = dbChaptersByUrl[syncChapter.url]
            if (dbChapter == null) {
                toInsert += Chapter(
                    id = -1L,
                    mangaId = mangaId,
                    read = syncChapter.read,
                    bookmark = syncChapter.bookmark,
                    lastPageRead = syncChapter.lastPageRead,
                    dateFetch = Instant.fromEpochMilliseconds(syncChapter.dateFetch),
                    sourceOrder = syncChapter.sourceOrder,
                    url = syncChapter.url,
                    name = syncChapter.name,
                    dateUpload = syncChapter.dateUpload?.let(Instant::fromEpochMilliseconds),
                    chapterNumber = syncChapter.chapterNumber,
                    scanlator = syncChapter.scanlator,
                    memo = JsonObject.EMPTY,
                )
            } else if (
                dbChapter.read != syncChapter.read ||
                dbChapter.bookmark != syncChapter.bookmark ||
                dbChapter.lastPageRead != syncChapter.lastPageRead
            ) {
                toUpdate += ChapterUpdate(dbChapter.id) {
                    read = syncChapter.read
                    bookmark = syncChapter.bookmark
                    lastPageRead = syncChapter.lastPageRead
                }
            }
        }

        if (toInsert.isNotEmpty()) {
            chapterRepository.updateFromRemote(
                removedIds = emptyList(),
                added = toInsert,
                updated = emptyList(),
            )
        }
        if (toUpdate.isNotEmpty()) {
            chapterRepository.updateAll(toUpdate)
        }
    }

    private suspend fun applyHistory(mangaId: Long, syncHistory: List<SyncHistory>) {
        val chapters = chapterRepository.getChapterByMangaId(mangaId)
        val chapterIdByUrl = chapters.associate { it.url to it.id }
        val chapterUrlById = chapters.associate { it.id to it.url }
        val dbHistoryByChapterId = historyRepository.getHistoryByMangaId(mangaId).associateBy { it.chapterId }
        val syncedUrls = syncHistory.map { it.chapterUrl }.toSet()

        for (entry in syncHistory) {
            val chapterId = chapterIdByUrl[entry.chapterUrl] ?: continue
            val dbEntry = dbHistoryByChapterId[chapterId]
            val targetReadAt = entry.readAt?.let(Instant::fromEpochMilliseconds)

            if (targetReadAt == null) {
                if (dbEntry != null && dbEntry.readAt != null) {
                    historyRepository.resetHistory(dbEntry.id)
                }
            } else {
                val deltaDuration = max(0L, entry.readDuration - (dbEntry?.readDuration ?: 0L))
                if (dbEntry == null || dbEntry.readAt != targetReadAt || deltaDuration > 0L) {
                    historyRepository.upsertHistory(
                        HistoryUpdate(
                            chapterId = chapterId,
                            readAt = targetReadAt,
                            sessionReadDuration = deltaDuration,
                        ),
                    )
                }
            }
        }

        for ((chapterId, dbEntry) in dbHistoryByChapterId) {
            val url = chapterUrlById[chapterId] ?: continue
            if (url !in syncedUrls && dbEntry.readAt != null) {
                historyRepository.resetHistory(dbEntry.id)
            }
        }
    }

    private suspend fun applyTracks(mangaId: Long, syncTracks: List<SyncTrack>) {
        val dbTracksByTracker = trackRepository.getTracksByMangaId(mangaId).associateBy { it.trackerId }
        val syncedTrackerIds = syncTracks.map { it.trackerId }.toSet()

        val toUpsert = syncTracks.mapNotNull { syncTrack ->
            val existing = dbTracksByTracker[syncTrack.trackerId]
            val target = Track(
                id = existing?.id ?: 0L,
                mangaId = mangaId,
                trackerId = syncTrack.trackerId,
                remoteId = syncTrack.remoteId,
                libraryId = syncTrack.libraryId,
                title = syncTrack.title,
                lastChapterRead = syncTrack.lastChapterRead,
                totalChapters = syncTrack.totalChapters,
                status = syncTrack.status,
                score = syncTrack.score,
                remoteUrl = syncTrack.remoteUrl,
                startDate = syncTrack.startDate?.let(Instant::fromEpochMilliseconds),
                finishDate = syncTrack.finishDate?.let(Instant::fromEpochMilliseconds),
                private = syncTrack.private,
            )
            if (existing == target) null else target
        }

        if (toUpsert.isNotEmpty()) {
            trackRepository.upsertAll(toUpsert)
        }

        for (existingTrackerId in dbTracksByTracker.keys) {
            if (existingTrackerId !in syncedTrackerIds) {
                trackRepository.delete(mangaId, existingTrackerId)
            }
        }
    }

    private fun SyncManga.toDomainManga(): Manga {
        return Manga(
            id = -1L,
            source = source,
            lastUpdate = null,
            nextUpdate = null,
            fetchInterval = 0,
            favoriteAt = favoriteAt?.let(Instant::fromEpochMilliseconds),
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            coverLastModified = null,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status,
            thumbnailUrl = thumbnailUrl,
            updateStrategy = UpdateStrategy.entries.getOrElse(updateStrategy) { UpdateStrategy.ALWAYS_UPDATE },
            initialized = initialized,
            notes = notes,
            memo = JsonObject.EMPTY,
        )
    }
}
