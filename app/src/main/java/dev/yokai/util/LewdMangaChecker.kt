package dev.yokai.util

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

fun Manga.isLewd(): Boolean {
    val sourceName = Injekt.get<SourceManager>().get(source)?.name
    val tags = genre?.split(",")?.map { it.trim().lowercase(Locale.US) } ?: emptyList()

    if (!tags.none { isNonHentai(it) }) return false
    return (sourceName != null && sourceName.isFromHentaiSource()) || tags.any { isHentai(it) }
}

private fun isNonHentai(tag: String) = tag.contains("non-h", true)

private fun String.isFromHentaiSource() =
    contains("hentai", true) ||
    contains("adult", true)

private fun isHentai(tag: String) =
    tag.contains("hentai", true) ||
    tag.contains("adult", true) ||
    tag.contains("smut", true) ||
    tag.contains("lewd", true) ||
    tag.contains("nsfw", true) ||
    tag.contains("erotic", true) ||
    tag.contains("pornographic", true) ||
    tag.contains("18+", true)
