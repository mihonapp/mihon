package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.content.Context
import android.widget.Toast
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.MigrationType
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga.SearchResult
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.smartsearch.SmartSourceSearchEngine
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicInteger

class MigrationListScreenModel(
    private val config: MigrationProcedureConfig,
    private val preferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getHistoryByMangaId: GetHistory = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val deleteTrack: DeleteTrack = Injekt.get(),
) : ScreenModel {

    private val smartSearchEngine = SmartSourceSearchEngine(config.extraSearchParams)

    val migratingItems = MutableStateFlow<ImmutableList<MigratingManga>?>(null)
    val migrationDone = MutableStateFlow(false)
    val unfinishedCount = MutableStateFlow(0)

    val manualMigrations = MutableStateFlow(0)

    val hideNotFound = preferences.hideNotFoundMigration().get()
    val showOnlyUpdates = preferences.showOnlyUpdatesMigration().get()

    val navigateOut = MutableSharedFlow<Unit>()

    val dialog = MutableStateFlow<Dialog?>(null)

    val migratingProgress = MutableStateFlow(Float.MAX_VALUE)

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            val mangaIds = when (val migration = config.migration) {
                is MigrationType.MangaList -> {
                    migration.mangaIds
                }
                is MigrationType.MangaSingle -> listOf(migration.fromMangaId)
            }
            runMigrations(
                mangaIds
                    .map {
                        async {
                            val manga = getManga.await(it) ?: return@async null
                            MigratingManga(
                                manga = manga,
                                chapterInfo = getChapterInfo(it),
                                sourcesString = sourceManager.getOrStub(manga.source).getNameForMangaInfo(),
                                parentContext = screenModelScope.coroutineContext,
                            )
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .also {
                        migratingItems.value = it.toImmutableList()
                    },
            )
        }
    }

    suspend fun getManga(result: SearchResult.Result) = getManga(result.id)
    suspend fun getManga(id: Long) = getManga.await(id)
    suspend fun getChapterInfo(result: SearchResult.Result) = getChapterInfo(result.id)
    suspend fun getChapterInfo(id: Long) = getChaptersByMangaId.await(id).let { chapters ->
        MigratingManga.ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }
    fun getSourceName(manga: Manga) = sourceManager.getOrStub(manga.source).getNameForMangaInfo()

    fun getMigrationSources() = preferences.migrationSources().get().split("/").mapNotNull {
        val value = it.toLongOrNull() ?: return@mapNotNull null
        sourceManager.get(value) as? CatalogueSource
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        unfinishedCount.value = mangas.size
        val useSourceWithMost = preferences.useSourceWithMost().get()
        val useSmartSearch = preferences.smartMigration().get()

        val sources = getMigrationSources()
        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) {
                break
            }
            // in case it was removed
            when (val migration = config.migration) {
                is MigrationType.MangaList -> if (manga.manga.id !in migration.mangaIds) {
                    continue
                }
                else -> Unit
            }

            if (manga.searchResult.value == SearchResult.Searching && manga.migrationScope.isActive) {
                val mangaObj = manga.manga
                val mangaSource = sourceManager.getOrStub(mangaObj.source)

                val result = try {
                    manga.migrationScope.async {
                        val validSources = if (sources.size == 1) {
                            sources
                        } else {
                            sources.filter { it.id != mangaSource.id }
                        }
                        when (val migration = config.migration) {
                            is MigrationType.MangaSingle -> if (migration.toManga != null) {
                                val localManga = getManga.await(migration.toManga)
                                if (localManga != null) {
                                    val source = sourceManager.get(localManga.source) as? CatalogueSource
                                    if (source != null) {
                                        val chapters = source.getChapterList(localManga.toSManga())
                                        try {
                                            syncChaptersWithSource.await(chapters, localManga, source)
                                        } catch (_: Exception) {
                                        }
                                        manga.progress.value = validSources.size to validSources.size
                                        return@async localManga
                                    }
                                }
                            }
                            else -> Unit
                        }
                        if (useSourceWithMost) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async async2@{
                                    sourceSemaphore.withPermit {
                                        try {
                                            val searchResult = if (useSmartSearch) {
                                                smartSearchEngine.smartSearch(source, mangaObj.title)
                                            } else {
                                                smartSearchEngine.normalSearch(source, mangaObj.title)
                                            }

                                            if (searchResult != null &&
                                                !(searchResult.url == mangaObj.url && source.id == mangaObj.source)
                                            ) {
                                                val localManga = networkToLocalManga(searchResult)

                                                val chapters = source.getChapterList(localManga.toSManga())

                                                try {
                                                    syncChaptersWithSource.await(chapters, localManga, source)
                                                } catch (_: Exception) {
                                                    return@async2 null
                                                }
                                                manga.progress.value =
                                                    validSources.size to processedSources.incrementAndGet()
                                                localManga to chapters.size
                                            } else {
                                                null
                                            }
                                        } catch (e: CancellationException) {
                                            // Ignore cancellations
                                            throw e
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.mapNotNull { it.await() }.maxByOrNull { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = if (useSmartSearch) {
                                        smartSearchEngine.smartSearch(source, mangaObj.title)
                                    } else {
                                        smartSearchEngine.normalSearch(source, mangaObj.title)
                                    }

                                    if (searchResult != null) {
                                        val localManga = networkToLocalManga(searchResult)
                                        val chapters = try {
                                            source.getChapterList(localManga.toSManga())
                                        } catch (e: Exception) {
                                            this@MigrationListScreenModel.logcat(LogPriority.ERROR, e)
                                            emptyList()
                                        }
                                        syncChaptersWithSource.await(chapters, localManga, source)
                                        localManga
                                    } else {
                                        null
                                    }
                                } catch (e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                                manga.progress.value = validSources.size to (index + 1)
                                if (searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }.await()
                } catch (e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnailUrl == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toSManga())
                        updateManga.awaitUpdateFromSource(result, newManga, true)
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (_: Exception) {
                    }
                }

                manga.searchResult.value = if (result == null) {
                    SearchResult.NotFound
                } else {
                    SearchResult.Result(result.id)
                }
                if (result == null && hideNotFound) {
                    removeManga(manga)
                }
                if (result != null &&
                    showOnlyUpdates &&
                    (getChapterInfo(result.id).latestChapter ?: 0.0) <= (manga.chapterInfo.latestChapter ?: 0.0)
                ) {
                    removeManga(manga)
                }

                sourceFinished()
            }
        }
    }

    private suspend fun sourceFinished() {
        unfinishedCount.value = migratingItems.value.orEmpty().count {
            it.searchResult.value != SearchResult.Searching
        }
        if (allMangasDone()) {
            migrationDone.value = true
        }
        if (migratingItems.value?.isEmpty() == true) {
            navigateOut()
        }
    }

    fun allMangasDone() = migratingItems.value.orEmpty().all { it.searchResult.value != SearchResult.Searching } &&
        migratingItems.value.orEmpty().any { it.searchResult.value is SearchResult.Result }

    fun mangasSkipped() = migratingItems.value.orEmpty().count { it.searchResult.value == SearchResult.NotFound }

    private suspend fun migrateMangaInternal(
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        if (prevManga.id == manga.id) return // Nothing to migrate

        val flags = preferences.migrateFlags().get()
        // Update chapters read
        if (MigrationFlags.hasChapters(flags)) {
            val prevMangaChapters = getChaptersByMangaId.await(prevManga.id)
            val maxChapterRead = prevMangaChapters.filter(Chapter::read)
                .maxOfOrNull(Chapter::chapterNumber)
            val dbChapters = getChaptersByMangaId.await(manga.id)
            val prevHistoryList = getHistoryByMangaId.await(prevManga.id)

            val chapterUpdates = mutableListOf<ChapterUpdate>()
            val historyUpdates = mutableListOf<HistoryUpdate>()

            dbChapters.forEach { chapter ->
                if (chapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters.find {
                        it.isRecognizedNumber &&
                            it.chapterNumber == chapter.chapterNumber
                    }
                    if (prevChapter != null) {
                        chapterUpdates += ChapterUpdate(
                            id = chapter.id,
                            bookmark = prevChapter.bookmark,
                            read = prevChapter.read,
                            dateFetch = prevChapter.dateFetch,
                        )
                        prevHistoryList.find { it.chapterId == prevChapter.id }?.let { prevHistory ->
                            historyUpdates += HistoryUpdate(
                                chapter.id,
                                prevHistory.readAt ?: return@let,
                                prevHistory.readDuration,
                            )
                        }
                    } else if (maxChapterRead != null && chapter.chapterNumber <= maxChapterRead) {
                        chapterUpdates += ChapterUpdate(
                            id = chapter.id,
                            read = true,
                        )
                    }
                }
            }

            updateChapter.awaitAll(chapterUpdates)
            upsertHistory.awaitAll(historyUpdates)
        }
        // Update categories
        if (MigrationFlags.hasCategories(flags)) {
            val categories = getCategories.await(prevManga.id)
            setMangaCategories.await(manga.id, categories.map { it.id })
        }
        // Update track
        if (MigrationFlags.hasTracks(flags)) {
            val tracks = getTracks.await(prevManga.id)
            if (tracks.isNotEmpty()) {
                getTracks.await(manga.id).forEach {
                    deleteTrack.await(manga.id, it.trackerId)
                }
                insertTrack.awaitAll(tracks.map { it.copy(mangaId = manga.id) })
            }
        }
        // Update custom cover
        if (MigrationFlags.hasCustomCover(flags) && prevManga.hasCustomCover(coverCache)) {
            coverCache.setCustomCoverToCache(manga, coverCache.getCustomCoverFile(prevManga.id).inputStream())
        }

        var mangaUpdate = MangaUpdate(manga.id, favorite = true, dateAdded = System.currentTimeMillis())
        var prevMangaUpdate: MangaUpdate? = null
        // Update extras
        if (MigrationFlags.hasExtra(flags)) {
            mangaUpdate = mangaUpdate.copy(
                chapterFlags = prevManga.chapterFlags,
                viewerFlags = prevManga.viewerFlags,
            )
        }
        // Delete downloaded
        if (MigrationFlags.hasDeleteChapters(flags)) {
            val oldSource = sourceManager.get(prevManga.source)
            if (oldSource != null) {
                downloadManager.deleteManga(prevManga, oldSource)
            }
        }
        // Update favorite status
        if (replace) {
            prevMangaUpdate = MangaUpdate(
                id = prevManga.id,
                favorite = false,
                dateAdded = 0,
            )
            mangaUpdate = mangaUpdate.copy(
                dateAdded = prevManga.dateAdded,
            )
        }

        updateManga.awaitAll(listOfNotNull(mangaUpdate, prevMangaUpdate))
    }

    fun useMangaForMigration(context: Context, newMangaId: Long, selectedMangaId: Long) {
        val migratingManga = migratingItems.value.orEmpty().find { it.manga.id == selectedMangaId }
            ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        screenModelScope.launchIO {
            val result = migratingManga.migrationScope.async {
                val manga = getManga(newMangaId)!!
                val localManga = networkToLocalManga(manga)
                try {
                    val source = sourceManager.get(manga.source)!!
                    val chapters = source.getChapterList(localManga.toSManga())
                    syncChaptersWithSource.await(chapters, localManga, source)
                } catch (_: Exception) {
                    return@async null
                }
                localManga
            }.await()

            if (result != null) {
                try {
                    val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toSManga())
                    updateManga.awaitUpdateFromSource(result, newManga, true)
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (_: Exception) {
                }

                migratingManga.searchResult.value = SearchResult.Result(result.id)
            } else {
                migratingManga.searchResult.value = SearchResult.NotFound
                withUIContext {
                    context.toast(MR.strings.no_chapters_found_for_migration, Toast.LENGTH_LONG)
                }
            }
        }
    }

    fun migrateMangas() {
        migrateMangas(true)
    }

    fun copyMangas() {
        migrateMangas(false)
    }

    private fun migrateMangas(replace: Boolean) {
        dialog.value = null
        migrateJob = screenModelScope.launchIO {
            migratingProgress.value = 0f
            val items = migratingItems.value.orEmpty()
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val toMangaObj = manga.searchResult.value.let {
                            if (it is SearchResult.Result) {
                                getManga.await(it.id)
                            } else {
                                null
                            }
                        }
                        if (toMangaObj != null) {
                            migrateMangaInternal(
                                manga.manga,
                                toMangaObj,
                                replace,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    migratingProgress.value = index.toFloat() / items.size
                }

                navigateOut()
            } finally {
                migratingProgress.value = Float.MAX_VALUE
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateOut() {
        navigateOut.emit(Unit)
    }

    fun migrateManga(mangaId: Long, copy: Boolean) {
        manualMigrations.value++
        screenModelScope.launchIO {
            val manga = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO

            val toMangaObj = getManga.await((manga.searchResult.value as? SearchResult.Result)?.id ?: return@launchIO)
                ?: return@launchIO
            migrateMangaInternal(
                manga.manga,
                toMangaObj,
                !copy,
            )

            removeManga(mangaId)
        }
    }

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO
            removeManga(item)
            item.migrationScope.cancel()
            sourceFinished()
        }
    }

    fun removeManga(item: MigratingManga) {
        when (val migration = config.migration) {
            is MigrationType.MangaList -> {
                val ids = migration.mangaIds.toMutableList()
                val index = ids.indexOf(item.manga.id)
                if (index > -1) {
                    ids.removeAt(index)
                    config.migration = MigrationType.MangaList(ids)
                    val index2 = migratingItems.value.orEmpty().indexOf(item)
                    if (index2 > -1) migratingItems.value = (migratingItems.value.orEmpty() - item).toImmutableList()
                }
            }
            is MigrationType.MangaSingle -> Unit
        }
    }

    override fun onDispose() {
        super.onDispose()
        migratingItems.value.orEmpty().forEach {
            it.migrationScope.cancel()
        }
    }

    fun openMigrateDialog(
        copy: Boolean,
    ) {
        dialog.value = Dialog.MigrateMangaDialog(
            copy,
            migratingItems.value.orEmpty().size,
            mangasSkipped(),
        )
    }

    sealed class Dialog {
        data class MigrateMangaDialog(val copy: Boolean, val mangaSet: Int, val mangaSkipped: Int) : Dialog()
        object MigrationExitDialog : Dialog()
    }
}
