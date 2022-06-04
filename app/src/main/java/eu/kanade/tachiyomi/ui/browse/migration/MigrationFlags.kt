package eu.kanade.tachiyomi.ui.browse.migration

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.hasCustomCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

object MigrationFlags {

    private const val CHAPTERS = 0b0001
    private const val CATEGORIES = 0b0010
    private const val TRACK = 0b0100
    private const val CUSTOM_COVER = 0b1000

    private val coverCache: CoverCache by injectLazy()
    private val db: DatabaseHelper = Injekt.get()

    val flags get() = arrayOf(CHAPTERS, CATEGORIES, TRACK, CUSTOM_COVER)

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

    fun getEnabledFlagsPositions(value: Int): List<Int> {
        return flags.mapIndexedNotNull { index, flag -> if (value and flag != 0) index else null }
    }

    fun getFlagsFromPositions(positions: Array<Int>): Int {
        return positions.fold(0) { accumulated, position -> accumulated or (1 shl position) }
    }

    fun titles(manga: Manga?): Array<Int> {
        val titles = arrayOf(R.string.chapters, R.string.categories).toMutableList()
        if (manga != null) {
            db.inTransaction {
                if (db.getTracks(manga.id).executeAsBlocking().isNotEmpty()) {
                    titles.add(R.string.track)
                }

                if (manga.hasCustomCover(coverCache)) {
                    titles.add(R.string.custom_cover)
                }
            }
        }
        return titles.toTypedArray()
    }
}
