package exh.util

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import java.util.Locale
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Manga.mangaType(context: Context): String {
    return context.getString(
        when (mangaType()) {
            MangaType.TYPE_WEBTOON -> R.string.webtoon
            MangaType.TYPE_MANHWA -> R.string.manhwa
            MangaType.TYPE_MANHUA -> R.string.manhua
            MangaType.TYPE_COMIC -> R.string.comic
            else -> R.string.manga
        }
    ).toLowerCase(Locale.getDefault())
}

/**
 * The type of comic the manga is (ie. manga, manhwa, manhua)
 */
fun Manga.mangaType(): MangaType {
    val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
    val currentTags =
        genre?.split(",")?.map { it.trim().toLowerCase(Locale.US) } ?: emptyList()
    return if (currentTags.any { tag -> tag.contains("japanese", ignoreCase = true) || isMangaTag(tag) }) {
        MangaType.TYPE_MANGA
    } else if (currentTags.any { tag -> tag.contains("english", ignoreCase = true) || isComicTag(tag) } || isComicSource(sourceName)) {
        MangaType.TYPE_COMIC
    } else if (currentTags.any { tag -> tag.contains("chinese", ignoreCase = true) || isManhuaTag(tag) } || isManhuaSource(sourceName)) {
        MangaType.TYPE_MANHUA
    } else if (currentTags.any { tag -> tag.contains("korean", ignoreCase = true) || isManhwaTag(tag) } || isWebtoonSource(sourceName)) {
        MangaType.TYPE_MANHWA
    } else if (currentTags.any { tag -> isWebtoonTag(tag) }) {
        MangaType.TYPE_WEBTOON
    } else {
        MangaType.TYPE_MANGA
    }
}

/**
 * The type the reader should use. Different from manga type as certain manga has different
 * read types
 */
fun Manga.defaultReaderType(): Int {
    val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
    val type = mangaType()
    return if (type == MangaType.TYPE_MANHWA || type == MangaType.TYPE_WEBTOON) {
        ReaderActivity.WEBTOON
    } else if (type == MangaType.TYPE_MANHUA || (type == MangaType.TYPE_COMIC && !sourceName.contains("tapastic", ignoreCase = true))) {
        ReaderActivity.LEFT_TO_RIGHT
    } else 0
}

private fun isMangaTag(tag: String): Boolean {
    return tag.toLowerCase() in listOf("manga", "манга")
}

private fun isManhuaTag(tag: String): Boolean {
    return tag.toLowerCase() in listOf("manhua", "маньхуа")
}

private fun isManhwaTag(tag: String): Boolean {
    return tag.toLowerCase() in listOf("manhwa", "манхва")
}

private fun isComicTag(tag: String): Boolean {
    return tag.toLowerCase() in listOf("comic", "комикс")
}

private fun isWebtoonTag(tag: String): Boolean {
    return tag.toLowerCase() in listOf("long strip", "webtoon")
}

private fun isWebtoonSource(sourceName: String): Boolean {
    return sourceName.contains("webtoon", true) ||
        sourceName.contains("manwha", true) ||
        sourceName.contains("toonily", true)
}

private fun isComicSource(sourceName: String): Boolean {
    return sourceName.contains("gunnerkrigg", true) ||
        sourceName.contains("dilbert", true) ||
        sourceName.contains("cyanide", true) ||
        sourceName.contains("xkcd", true) ||
        sourceName.contains("tapastic", true)
}

private fun isManhuaSource(sourceName: String): Boolean {
    return sourceName.contains("manhua", true)
}

enum class MangaType {
    TYPE_MANGA,
    TYPE_MANHWA,
    TYPE_MANHUA,
    TYPE_COMIC,
    TYPE_WEBTOON
}
