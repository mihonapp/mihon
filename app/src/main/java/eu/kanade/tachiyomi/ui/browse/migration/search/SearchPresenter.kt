package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchCardItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.hasCustomCover
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date

class SearchPresenter(
    initialQuery: String? = "",
    private val manga: Manga,
) : GlobalSearchPresenter(initialQuery) {

    private val replacingMangaRelay = BehaviorRelay.create<Pair<Boolean, Manga?>>()
    private val coverCache: CoverCache by injectLazy()
    private val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }

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

    override fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        val localManga = super.networkToLocalManga(sManga, sourceId)
        // For migration, displayed title should always match source rather than local DB
        localManga.title = sManga.title
        return localManga
    }

    fun migrateManga(prevManga: Manga, manga: Manga, replace: Boolean) {
        val source = sourceManager.get(manga.source) ?: return
        val prevSource = sourceManager.get(prevManga.source)

        replacingMangaRelay.call(Pair(true, null))

        presenterScope.launchIO {
            try {
                val chapters = source.getChapterList(manga.toMangaInfo())
                    .map { it.toSChapter() }

                migrateMangaInternal(prevSource, source, chapters, prevManga, manga, replace)
            } catch (e: Throwable) {
                withUIContext { view?.applicationContext?.toast(e.message) }
            }

            presenterScope.launchUI { replacingMangaRelay.call(Pair(false, manga)) }
        }
    }

    private fun migrateMangaInternal(
        prevSource: Source?,
        source: Source,
        sourceChapters: List<SChapter>,
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        val flags = preferences.migrateFlags().get()
        val migrateChapters =
            MigrationFlags.hasChapters(
                flags,
            )
        val migrateCategories =
            MigrationFlags.hasCategories(
                flags,
            )
        val migrateTracks =
            MigrationFlags.hasTracks(
                flags,
            )
        val migrateCustomCover =
            MigrationFlags.hasCustomCover(
                flags,
            )

        db.inTransaction {
            // Update chapters read
            if (migrateChapters) {
                try {
                    syncChaptersWithSource(sourceChapters, manga, source)
                } catch (e: Exception) {
                    // Worst case, chapters won't be synced
                }

                val prevMangaChapters = db.getChapters(prevManga).executeAsBlocking()
                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapter_number } ?: 0f
                val dbChapters = db.getChapters(manga).executeAsBlocking()
                for (chapter in dbChapters) {
                    if (chapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapter_number == chapter.chapter_number }
                        if (prevChapter != null) {
                            chapter.date_fetch = prevChapter.date_fetch
                            chapter.bookmark = prevChapter.bookmark
                        }
                        if (chapter.chapter_number <= maxChapterRead) {
                            chapter.read = true
                        }
                    }
                }
                db.insertChapters(dbChapters).executeAsBlocking()
            }

            // Update categories
            if (migrateCategories) {
                val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
                val mangaCategories = categories.map { MangaCategory.create(manga, it) }
                db.setMangaCategories(mangaCategories, listOf(manga))
            }

            // Update track
            if (migrateTracks) {
                val tracksToUpdate = db.getTracks(prevManga.id).executeAsBlocking().mapNotNull { track ->
                    track.id = null
                    track.manga_id = manga.id!!

                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(track, prevManga, prevSource) }
                    if (service != null) service.migrateTrack(track, manga, source)
                    else track
                }
                db.insertTracks(tracksToUpdate).executeAsBlocking()
            }

            // Update favorite status
            if (replace) {
                prevManga.favorite = false
                db.updateMangaFavorite(prevManga).executeAsBlocking()
            }
            manga.favorite = true

            // Update reading preferences
            manga.chapter_flags = prevManga.chapter_flags
            manga.viewer_flags = prevManga.viewer_flags

            // Update date added
            if (replace) {
                manga.date_added = prevManga.date_added
                prevManga.date_added = 0
            } else {
                manga.date_added = Date().time
            }

            // Update custom cover
            if (migrateCustomCover) {
                coverCache.setCustomCoverToCache(manga, coverCache.getCustomCoverFile(prevManga.id).inputStream())
            }

            // SearchPresenter#networkToLocalManga may have updated the manga title,
            // so ensure db gets updated title too
            db.insertManga(manga).executeAsBlocking()
        }
    }
}
