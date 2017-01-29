package eu.kanade.tachiyomi.data.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import eu.kanade.tachiyomi.data.database.models.Manga
import java.io.File
import java.io.InputStream

class MangaFileFetcher(private val fetcher: DataFetcher<InputStream>,
                       private val file: File,
                       private val manga: Manga) : DataFetcher<InputStream> {


    override fun loadData(priority: Priority?): InputStream? {
        return fetcher.loadData(priority)
    }

    override fun getId(): String {
        return manga.thumbnail_url + file.lastModified()
    }

    override fun cancel() {
        fetcher.cancel()
    }

    override fun cleanup() {
        fetcher.cleanup()
    }
}