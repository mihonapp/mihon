package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.util.storage.DiskUtil

/**
 * Folder names of translated chapters in the Local source, shared by the worker (which writes them) and the manga
 * screen (which looks for them):
 *
 * `<Local source>/<Manga title> (<Language>)/<Chapter name>/001.jpg, ...`
 */
object TranslationPaths {

    const val STAGING_PREFIX = ".translating-"

    fun seriesName(mangaTitle: String, language: String, disallowNonAscii: Boolean): String {
        return DiskUtil.buildValidFilename("$mangaTitle ($language)", disallowNonAscii = disallowNonAscii)
    }

    fun chapterFolder(chapterName: String, disallowNonAscii: Boolean): String {
        return DiskUtil.buildValidFilename(
            chapterName,
            DiskUtil.MAX_FILE_NAME_BYTES - STAGING_PREFIX.length,
            disallowNonAscii,
        )
    }
}
