package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.toChapterUpdate
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchCardItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date

class SearchPresenter(
    initialQuery: String? = "",
    private val manga: Manga,
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
) : GlobalSearchPresenter(initialQuery) {

    private val replacingMangaRelay = BehaviorRelay.create<Pair<Boolean, Manga?>>()
    private val coverCache: CoverCache by injectLazy()
    private val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        replacingMangaRelay.subscribeLatestCache(
            { controller, (isReplacingManga, newManga) ->
                (controller as? SearchController)?.renderIsReplacingManga(isReplacingManga, newManga)
            },
        )
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        // Put the source of the selected manga at the top
        return super.getEnabledSources()
            .sortedByDescending { it.id == manga.source }
    }

    override fun createCatalogueSearchItem(source: CatalogueSource, results: List<GlobalSearchCardItem>?): GlobalSearchItem {
        // Set the catalogue search item as highlighted if the source matches that of the selected manga
        return GlobalSearchItem(source, results, source.id == manga.source)
    }

    override suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        val localManga = super.networkToLocalManga(sManga, sourceId)
        // For migration, displayed title should always match source rather than local DB
        return localManga.copy(title = sManga.title)
    }

    fun migrateManga(prevManga: Manga, manga: Manga, replace: Boolean) {
        val source = sourceManager.get(manga.source) ?: return
        val prevSource = sourceManager.get(prevManga.source)

        replacingMangaRelay.call(Pair(true, null))

        presenterScope.launchIO {
            try {
                val chapters = source.getChapterList(manga.toSManga())

                migrateMangaInternal(prevSource, source, chapters, prevManga, manga, replace)
            } catch (e: Throwable) {
                withUIContext { view?.applicationContext?.toast(e.message) }
            }

            withUIContext { replacingMangaRelay.call(Pair(false, manga)) }
        }
    }

    private suspend fun migrateMangaInternal(
        prevSource: Source?,
        source: Source,
        sourceChapters: List<SChapter>,
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        val flags = migrateFlags.get()

        val migrateChapters = MigrationFlags.hasChapters(flags)
        val migrateCategories = MigrationFlags.hasCategories(flags)
        val migrateTracks = MigrationFlags.hasTracks(flags)
        val migrateCustomCover = MigrationFlags.hasCustomCover(flags)

        try {
            syncChaptersWithSource.await(sourceChapters, manga, source)
        } catch (e: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateChapters) {
            val prevMangaChapters = getChapterByMangaId.await(prevManga.id)
            val mangaChapters = getChapterByMangaId.await(manga.id)

            val maxChapterRead = prevMangaChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                var updatedChapter = mangaChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(prevManga.id).map { it.id }
            setMangaCategories.await(manga.id, categoryIds)
        }

        // Update track
        if (migrateTracks) {
            val tracks = getTracks.await(prevManga.id).mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = manga.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, prevManga, prevSource) }

                if (service != null) {
                    service.migrateTrack(updatedTrack, manga, source)
                } else {
                    updatedTrack
                }
            }
            insertTrack.awaitAll(tracks)
        }

        if (replace) {
            updateManga.await(MangaUpdate(prevManga.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && prevManga.hasCustomCover()) {
            @Suppress("BlockingMethodInNonBlockingContext")
            coverCache.setCustomCoverToCache(manga.toDbManga(), coverCache.getCustomCoverFile(prevManga.id).inputStream())
        }

        updateManga.await(
            MangaUpdate(
                id = manga.id,
                favorite = true,
                chapterFlags = prevManga.chapterFlags,
                viewerFlags = prevManga.viewerFlags,
                dateAdded = if (replace) prevManga.dateAdded else Date().time,
            ),
        )
    }
}
