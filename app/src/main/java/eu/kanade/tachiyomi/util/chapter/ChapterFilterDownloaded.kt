package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga): List<Chapter> {
    if (manga.isLocal()) return this

    val downloadCache: DownloadCache = Injekt.get()

    return filter { downloadCache.isChapterDownloaded(it.name, it.scanlator, manga.title, manga.source, false) }
}
