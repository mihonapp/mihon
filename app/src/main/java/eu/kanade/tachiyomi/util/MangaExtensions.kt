package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.util.Date
import eu.kanade.domain.manga.model.Manga as DomainManga

/**
 * Call before updating [Manga.thumbnail_url] to ensure old cover can be cleared from cache
 */
fun Manga.prepUpdateCover(coverCache: CoverCache, remoteManga: SManga, refreshSameUrl: Boolean) {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteManga.thumbnail_url ?: return

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return

    if (!refreshSameUrl && thumbnail_url == newUrl) return

    when {
        toDomainManga()!!.isLocal() -> {
            cover_last_modified = Date().time
        }
        toDomainManga()!!.hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
        }
        else -> {
            cover_last_modified = Date().time
            coverCache.deleteFromCache(this, false)
        }
    }
}

fun Manga.removeCovers(coverCache: CoverCache = Injekt.get()): Int {
    if (toDomainManga()!!.isLocal()) return 0

    cover_last_modified = Date().time
    return coverCache.deleteFromCache(this, true)
}

fun DomainManga.shouldDownloadNewChapters(dbCategories: List<Long>, prefs: PreferencesHelper): Boolean {
    if (!favorite) return false

    val categories = dbCategories.ifEmpty { listOf(0L) }

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapter = prefs.downloadNewChapter().get()
    if (!downloadNewChapter) return false

    val includedCategories = prefs.downloadNewChapterCategories().get().map { it.toLong() }
    val excludedCategories = prefs.downloadNewChapterCategoriesExclude().get().map { it.toLong() }

    // Default: Download from all categories
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // In excluded category
    if (categories.any { it in excludedCategories }) return false

    // Included category not selected
    if (includedCategories.isEmpty()) return true

    // In included category
    return categories.any { it in includedCategories }
}

suspend fun DomainManga.editCover(
    context: Context,
    stream: InputStream,
    updateManga: UpdateManga = Injekt.get(),
    coverCache: CoverCache = Injekt.get(),
): Boolean {
    return if (isLocal()) {
        LocalSource.updateCover(context, toDbManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(toDbManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else {
        // We should never reach this block
        false
    }
}
