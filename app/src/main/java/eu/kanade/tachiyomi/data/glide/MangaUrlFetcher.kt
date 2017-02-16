package eu.kanade.tachiyomi.data.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import eu.kanade.tachiyomi.data.database.models.Manga
import java.io.File
import java.io.FileNotFoundException
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
class MangaUrlFetcher(private val networkFetcher: DataFetcher<InputStream>,
                      private val file: File,
                      private val manga: Manga)
: MangaFileFetcher(file, manga) {

    override fun loadData(priority: Priority): InputStream {
        if (manga.favorite) {
            synchronized(file) {
                if (!file.exists()) {
                    val tmpFile = File(file.path + ".tmp")
                    try {
                        // Retrieve source stream.
                        val input = networkFetcher.loadData(priority)
                                ?: throw Exception("Couldn't open source stream")

                        // Retrieve destination stream, create parent folders if needed.
                        val output = try {
                            tmpFile.outputStream()
                        } catch (e: FileNotFoundException) {
                            tmpFile.parentFile.mkdirs()
                            tmpFile.outputStream()
                        }

                        // Copy the file and rename to the original.
                        input.use { output.use { input.copyTo(output) } }
                        tmpFile.renameTo(file)
                    } catch (e: Exception) {
                        tmpFile.delete()
                        throw e
                    }
                }
            }
            return super.loadData(priority)
        } else {
            if (file.exists()) {
                file.delete()
            }
            return networkFetcher.loadData(priority)
        }
    }

    override fun cancel() {
        networkFetcher.cancel()
    }

    override fun cleanup() {
        super.cleanup()
        networkFetcher.cleanup()
    }

}