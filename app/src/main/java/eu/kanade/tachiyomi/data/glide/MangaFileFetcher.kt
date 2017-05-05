package eu.kanade.tachiyomi.data.glide

import eu.kanade.tachiyomi.data.database.models.Manga
import java.io.File

open class MangaFileFetcher(private val file: File, private val manga: Manga) : FileFetcher(file) {

    /**
     * Returns the id for this manga's cover.
     *
     * Appending the file's modified date to the url, we can force Glide to skip its memory and disk
     * lookup step and fetch from our custom cache. This allows us to invalidate Glide's cache when
     * the file has changed. If the file doesn't exist it will append a 0.
     */
    override fun getId(): String {
        return manga.thumbnail_url + file.lastModified()
    }
}