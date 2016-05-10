package eu.kanade.tachiyomi.data.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import eu.kanade.tachiyomi.data.database.models.Manga
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * A [DataFetcher] for loading a cover of a manga depending on its favorite status.
 * If the manga is favorite, it tries to load the cover from our cache, and if it's not found, it
 * fallbacks to network and copies it to the cache.
 * If the manga is not favorite, it tries to delete the cover from our cache and always fallback
 * to network for fetching.
 *
 * @param networkFetcher the network fetcher for this cover.
 * @param file the file where this cover should be. It may exists or not.
 * @param manga the manga of the cover to load.
 */
class MangaDataFetcher(private val networkFetcher: DataFetcher<InputStream>,
                       private val file: File,
                       private val manga: Manga)
: DataFetcher<InputStream> {

    @Throws(Exception::class)
    override fun loadData(priority: Priority): InputStream? {
        if (manga.favorite) {
            if (!file.exists()) {
                file.parentFile.mkdirs()
                networkFetcher.loadData(priority)?.let {
                    it.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return FileInputStream(file)
        } else {
            if (file.exists()) {
                file.delete()
            }
            return networkFetcher.loadData(priority)
        }
    }

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

    override fun cancel() {
        networkFetcher.cancel()
    }

    override fun cleanup() {
        networkFetcher.cleanup()
    }

}