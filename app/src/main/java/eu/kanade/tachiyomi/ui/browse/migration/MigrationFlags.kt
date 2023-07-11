package eu.kanade.tachiyomi.ui.browse.migration

import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

object MigrationFlags {

    private const val CHAPTERS = 0b00001
    private const val CATEGORIES = 0b00010
    private const val TRACK = 0b00100
    private const val CUSTOM_COVER = 0b01000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: CoverCache by injectLazy()
    private val getTracks: GetTracks = Injekt.get()
    private val downloadCache: DownloadCache by injectLazy()

    val flags get() = arrayOf(CHAPTERS, CATEGORIES, TRACK, CUSTOM_COVER, DELETE_DOWNLOADED)
    private var enableFlags = emptyList<Int>().toMutableList()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasTracks(value: Int): Boolean {
        return value and TRACK != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    fun getEnabledFlagsPositions(value: Int): List<Int> {
        return flags.mapIndexedNotNull { index, flag -> if (value and flag != 0) index else null }
    }

    fun getFlagsFromPositions(positions: Array<Int>): Int {
        val fold = positions.fold(0) { accumulated, position -> accumulated or enableFlags[position] }
        enableFlags.clear()
        return fold
    }

    fun titles(manga: Manga?): Array<Int> {
        enableFlags.add(CHAPTERS)
        enableFlags.add(CATEGORIES)
        val titles = arrayOf(R.string.chapters, R.string.categories).toMutableList()
        if (manga != null) {
            if (runBlocking { getTracks.await(manga.id) }.isNotEmpty()) {
                titles.add(R.string.track)
                enableFlags.add(TRACK)
            }
            if (manga.hasCustomCover(coverCache)) {
                titles.add(R.string.custom_cover)
                enableFlags.add(CUSTOM_COVER)
            }
            if (downloadCache.getDownloadCount(manga) > 0) {
                titles.add(R.string.delete_downloaded)
                enableFlags.add(DELETE_DOWNLOADED)
            }
        }
        return titles.toTypedArray()
    }
}
