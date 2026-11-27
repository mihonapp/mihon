package eu.kanade.tachiyomi.data.sync.webdav

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionManager
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.sync.service.WebDavSyncPreferences
import tachiyomi.domain.track.repository.TrackRepository
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
class WebDavSyncSnapshotter(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val categoryRepository: CategoryRepository,
    private val trackRepository: TrackRepository,
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val extensionManager: ExtensionManager,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    private val preferences: WebDavSyncPreferences,
) {
    suspend fun createLocalSnapshot(options: WebDavSyncOptions): WebDavSyncSnapshot {
        val categories = if (options.syncLibrary) {
            categoryRepository.getAll()
                .filterNot { it.isSystemCategory }
                .sortedBy { it.order }
                .map { SyncCategory(name = it.name, order = it.order, flags = it.flags) }
        } else {
            emptyList()
        }

        val syncManga = mutableListOf<SyncManga>()
        val usedSourceIds = linkedSetOf<Long>()

        if (options.syncLibrary) {
            val allManga = (mangaRepository.getFavorites() + mangaRepository.getReadMangaNotInLibrary())
                .distinctBy { it.id }

            for (chunk in allManga.chunked(BATCH_SIZE)) {
                val ids = chunk.map { it.id }
                val chaptersByManga = chapterRepository.getChaptersByMangaIds(ids)
                val historyByManga = historyRepository.getHistoryByMangaIds(ids)
                val categoriesByManga = categoryRepository.getCategoriesByMangaIds(ids)
                val tracksByManga = trackRepository.getTracksByMangaIds(ids)
                val scanlatorsByManga = mangaRepository.getExcludedScanlatorsByMangaIds(ids)

                for (manga in chunk) {
                    usedSourceIds += manga.source
                    val chapters = chaptersByManga[manga.id].orEmpty()
                    val chapterUrlById = chapters.associate { it.id to it.url }

                    val syncChapters = chapters.map { chapter ->
                        SyncChapter(
                            url = chapter.url,
                            name = chapter.name,
                            scanlator = chapter.scanlator,
                            read = chapter.read,
                            bookmark = chapter.bookmark,
                            lastPageRead = chapter.lastPageRead,
                            dateFetch = chapter.dateFetch.toEpochMilliseconds(),
                            dateUpload = chapter.dateUpload?.toEpochMilliseconds(),
                            chapterNumber = chapter.chapterNumber,
                            sourceOrder = chapter.sourceOrder,
                        )
                    }

                    val syncHistory = historyByManga[manga.id].orEmpty().mapNotNull { history ->
                        val chapterUrl = chapterUrlById[history.chapterId] ?: return@mapNotNull null
                        SyncHistory(
                            chapterUrl = chapterUrl,
                            readAt = history.readAt?.toEpochMilliseconds(),
                            readDuration = history.readDuration,
                        )
                    }

                    val syncTracks = tracksByManga[manga.id].orEmpty().map { track ->
                        SyncTrack(
                            trackerId = track.trackerId,
                            remoteId = track.remoteId,
                            libraryId = track.libraryId,
                            title = track.title,
                            lastChapterRead = track.lastChapterRead,
                            totalChapters = track.totalChapters,
                            status = track.status,
                            score = track.score,
                            remoteUrl = track.remoteUrl,
                            startDate = track.startDate?.toEpochMilliseconds(),
                            finishDate = track.finishDate?.toEpochMilliseconds(),
                            private = track.private,
                        )
                    }

                    val mangaCategoryNames = categoriesByManga[manga.id].orEmpty()
                        .filterNot { it.isSystemCategory }
                        .map { it.name }

                    syncManga += SyncManga(
                        source = manga.source,
                        url = manga.url,
                        title = manga.title,
                        artist = manga.artist,
                        author = manga.author,
                        description = manga.description,
                        genre = manga.genre,
                        status = manga.status,
                        thumbnailUrl = manga.thumbnailUrl,
                        updateStrategy = manga.updateStrategy.ordinal,
                        initialized = manga.initialized,
                        favoriteAt = manga.favoriteAt?.toEpochMilliseconds(),
                        viewerFlags = manga.viewerFlags,
                        chapterFlags = manga.chapterFlags,
                        notes = manga.notes,
                        categories = mangaCategoryNames,
                        excludedScanlators = scanlatorsByManga[manga.id].orEmpty(),
                        chapters = syncChapters,
                        history = syncHistory,
                        tracks = syncTracks,
                    )
                }
            }
        }

        val sources = usedSourceIds.mapNotNull { sourceId ->
            val name = sourceManager.get(sourceId)?.name
                ?: stubSourceRepository.getStubSource(sourceId)?.name
            if (name.isNullOrBlank()) null else SyncSource(sourceId = sourceId, name = name)
        }

        val stores = if (options.syncExtensionStores) {
            extensionStoreRepository.getAll().map { store ->
                SyncExtensionStore(
                    indexUrl = store.indexUrl,
                    name = store.name,
                    badgeLabel = store.badgeLabel,
                    signingKey = store.signingKey,
                    website = store.contact.website,
                    discord = store.contact.discord,
                    isLegacy = store.isLegacy,
                    extensionListUrl = store.extensionListUrl,
                )
            }
        } else {
            emptyList()
        }

        val installedExtensions = if (options.syncExtensions) {
            getInstalledExtensionsSnapshot()
        } else {
            emptyList()
        }

        return WebDavSyncSnapshot(
            schemaVersion = WebDavSyncSnapshot.CURRENT_SCHEMA_VERSION,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            deviceId = preferences.getOrCreateDeviceId(),
            categories = categories,
            manga = syncManga,
            sources = sources,
            extensionStores = stores,
            installedExtensions = installedExtensions,
        ).normalizedForComparison()
    }

    suspend fun getInstalledExtensionsSnapshot(): List<SyncInstalledExtension> {
        val loaded = extensionManager.getLoadedExtensions()
        val notLoaded = extensionManager.getNotLoadedExtensions()
        return (loaded + notLoaded)
            .distinctBy { it.pkgName }
            .map { ext ->
                SyncInstalledExtension(
                    pkgName = ext.pkgName,
                    name = ext.name,
                    lang = ext.lang,
                    versionCode = ext.versionCode,
                    versionName = ext.versionName,
                    storeIndexUrl = ext.store?.indexUrl,
                    signingKey = ext.store?.signingKey ?: ext.signatures.lastOrNull(),
                )
            }
            .sortedBy { it.pkgName }
    }

    companion object {
        private const val BATCH_SIZE = 400
    }
}
