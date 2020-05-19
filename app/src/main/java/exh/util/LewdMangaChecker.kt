package exh.util

import android.util.Log
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.NHENTAI_SOURCE_ID
import exh.lewdDelegatedSourceIds
import java.util.Locale
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Manga.isLewd(): Boolean {
    val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
    val currentTags =
        genre?.split(",")?.map { it.trim().toLowerCase(Locale.US) } ?: emptyList()
    val meta =

        Log.d("Lewd", currentTags.joinToString(separator = "\n"))

    if (source == EH_SOURCE_ID || source == EXH_SOURCE_ID || source == NHENTAI_SOURCE_ID) {
        return !currentTags.any { tag -> isNonHentaiTag(tag) }
    }

    return source in 6905L..6913L ||
        source in lewdDelegatedSourceIds ||
        isHentaiSource(sourceName) ||
        currentTags.any { tag -> isHentaiTag(tag) }
}

private fun isNonHentaiTag(tag: String): Boolean {
    return tag.contains("non-h", true)
}

private fun isHentaiTag(tag: String): Boolean {
    return tag.contains("hentai", true) ||
        tag.contains("adult", true)
}

private fun isHentaiSource(source: String): Boolean {
    return source.contains("hentai cafe", true) ||
        source.contains("allporncomic", true) ||
        source.contains("hentai2read", true) ||
        source.contains("hentainexus", true)
}
