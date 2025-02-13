package eu.kanade.tachiyomi.ui.browse.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

data class MigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): MigrationFlag {
            return MigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object MigrationFlags {

    private const val CHAPTERS = 0b00001
    private const val CATEGORIES = 0b00010
    private const val CUSTOM_COVER = 0b01000
    private const val MIGRATE_DOWNLOADED = 0b100000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: CoverCache by injectLazy()
    private val downloadCache: DownloadCache by injectLazy()

    fun hasChapters(value: Int): Boolean {
        return isFlagSet(value, CHAPTERS)
    }

    fun hasCategories(value: Int): Boolean {
        return isFlagSet(value, CATEGORIES)
    }

    fun hasCustomCover(value: Int): Boolean {
        return isFlagSet(value, CUSTOM_COVER)
    }

    fun hasMigrateDownloaded(value: Int): Boolean {
        return isFlagSet(value, MIGRATE_DOWNLOADED)
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return isFlagSet(value, DELETE_DOWNLOADED)
    }

    private fun isFlagSet(value: Int, flag: Int): Boolean {
        return (value and flag) == flag
    }

    /** Returns information about applicable flags with default selections. */
    fun getFlags(manga: Manga?, defaultSelectedBitMap: Int): List<MigrationFlag> {
        val flags = mutableListOf<MigrationFlag>()
        flags += MigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.chapters)
        flags += MigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)

        if (manga != null) {
            if (manga.hasCustomCover(coverCache)) {
                flags += MigrationFlag.create(CUSTOM_COVER, defaultSelectedBitMap, MR.strings.custom_cover)
            }
            if (downloadCache.getDownloadCount(manga) > 0) {
                flags += MigrationFlag.create(MIGRATE_DOWNLOADED, defaultSelectedBitMap, MR.strings.migrate_downloaded)
                flags += MigrationFlag.create(DELETE_DOWNLOADED, defaultSelectedBitMap, MR.strings.delete_downloaded)
            }
        }
        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<MigrationFlag>,
    ): Int {
        return selectedFlags.zip(flags).filter { (isSelected, _) -> isSelected }.map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
