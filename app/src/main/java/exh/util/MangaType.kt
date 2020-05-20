package exh.util

import android.content.Context
import android.util.Log
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
    Log.d("MangaType", currentTags.joinToString(separator = "\n"))
    return if (currentTags.any { tag -> tag.contains("japanese", ignoreCase = true) || isMangaTag(tag) }) {
        Log.d("MangaType", "isManga")
        MangaType.TYPE_MANGA
    } else if (currentTags.any { tag -> isWebtoonTag(tag) } || isWebtoonSource(sourceName)) {
        Log.d("MangaType", "isWebtoon")
        MangaType.TYPE_WEBTOON
    } else if (currentTags.any { tag -> tag.contains("english", ignoreCase = true) || isComicTag(tag) } || isComicSource(sourceName)) {
        Log.d("MangaType", "isComic")
        MangaType.TYPE_COMIC
    } else if (currentTags.any { tag -> tag.contains("chinese", ignoreCase = true) || isManhuaTag(tag) } || isManhuaSource(sourceName)) {
        Log.d("MangaType", "isManhua")
        MangaType.TYPE_MANHUA
    } else if (currentTags.any { tag -> tag.contains("korean", ignoreCase = true) || isManhwaTag(tag) } || isManhwaSource(sourceName)) {
        Log.d("MangaType", "isManhwa")
        MangaType.TYPE_MANHWA
    } else {
        Log.d("MangaType", "ended up as isManga")
        MangaType.TYPE_MANGA
    }
}

/**
 * The type the reader should use. Different from manga type as certain manga has different
 * read types
 */
fun Manga.defaultReaderType(): Int? {
    // val sourceName = Injekt.get<SourceManager>().getOrStub(source).name
    val type = mangaType()
    return if (type == MangaType.TYPE_MANHWA || type == MangaType.TYPE_WEBTOON) {
        ReaderActivity.WEBTOON
    /* } else if (type == MangaType.TYPE_MANHUA || (type == MangaType.TYPE_COMIC && !sourceName.contains("tapastic", ignoreCase = true))) {
             ReaderActivity.LEFT_TO_RIGHT*/
    } else null
}

private fun isMangaTag(tag: String): Boolean {
    return tag.contains("manga") ||
        tag.contains("манга")
}

private fun isManhuaTag(tag: String): Boolean {
    return tag.contains("manhua") ||
        tag.contains("маньхуа")
}

private fun isManhwaTag(tag: String): Boolean {
    return tag.contains("manhwa") ||
        tag.contains("манхва")
}

private fun isComicTag(tag: String): Boolean {
    return tag.contains("comic") ||
        tag.contains("комикс")
}

private fun isWebtoonTag(tag: String): Boolean {
    return tag.contains("long strip") ||
        tag.contains("webtoon")
}

/*private fun isMangaSource(sourceName: String): Boolean {
    return
}*/

private fun isManhwaSource(sourceName: String): Boolean {
    return sourceName.contains("getmanhwa", true) ||
        sourceName.contains("hiperdex", true) ||
        sourceName.contains("manhwa18", true) ||
        sourceName.contains("manhwascan", true) ||
        sourceName.contains("manwahentai.me", true) ||
        sourceName.contains("manwha club", true) ||
        sourceName.contains("manytoon", true) ||
        sourceName.contains("manwha", true) ||
        sourceName.contains("toonily", true) ||
        sourceName.contains("readmanhwa", true)
}

private fun isWebtoonSource(sourceName: String): Boolean {
    return sourceName.contains("mangatoon", true) ||
        sourceName.contains("manmanga", true) ||
        // sourceName.contains("tapas", true) ||
        sourceName.contains("toomics", true) ||
        sourceName.contains("webcomics", true) ||
        sourceName.contains("webtoons", true) ||
        sourceName.contains("webtoon", true)
}

private fun isComicSource(sourceName: String): Boolean {
    return sourceName.contains("ciayo comics", true) ||
        sourceName.contains("comicextra", true) ||
        sourceName.contains("comicpunch", true) ||
        sourceName.contains("cyanide", true) ||
        sourceName.contains("dilbert", true) ||
        sourceName.contains("existential comics", true) ||
        sourceName.contains("hiveworks comics", true) ||
        sourceName.contains("milftoon", true) ||
        sourceName.contains("myhentaicomics", true) ||
        sourceName.contains("myhentaigallery", true) ||
        sourceName.contains("gunnerkrigg", true) ||
        sourceName.contains("oglaf", true) ||
        sourceName.contains("patch friday", true) ||
        sourceName.contains("porncomix", true) ||
        sourceName.contains("questionable content", true) ||
        sourceName.contains("read comics online", true) ||
        sourceName.contains("readcomicsonline", true) ||
        sourceName.contains("swords comic", true) ||
        sourceName.contains("teabeer comics", true) ||
        sourceName.contains("xkcd", true)
}

private fun isManhuaSource(sourceName: String): Boolean {
    return sourceName.contains("1st kiss manhua", true) ||
        sourceName.contains("hero manhua", true) ||
        sourceName.contains("manhuabox", true) ||
        sourceName.contains("manhuaus", true) ||
        sourceName.contains("manhuas world", true) ||
        sourceName.contains("manhuas.net", true) ||
        sourceName.contains("readmanhua", true) ||
        sourceName.contains("wuxiaworld", true) ||
        sourceName.contains("manhua", true)
}

enum class MangaType {
    TYPE_MANGA,
    TYPE_MANHWA,
    TYPE_MANHUA,
    TYPE_COMIC,
    TYPE_WEBTOON
}
