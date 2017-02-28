package exh

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.util.UrlUtil
import exh.metadata.MetadataHelper
import exh.metadata.copyTo
import uy.kohesive.injekt.injectLazy
import java.net.MalformedURLException
import java.net.URL

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    private val metadataHelper = MetadataHelper()

    fun addGallery(url: String, fav: Boolean = false): Manga {
        val source = when(URL(url).host) {
            "g.e-hentai.org", "e-hentai.org" -> 1
            "exhentai.org" -> 2
            else -> throw MalformedURLException("Not a valid gallery URL!")
        }

        val sourceObj = sourceManager.get(source)

        val pathOnlyUrl = UrlUtil.getPath(url)

        //Use manga in DB if possible, otherwise, make a new manga
        val manga = db.getManga(pathOnlyUrl, source).executeAsBlocking()
                ?: Manga.create(pathOnlyUrl, source).apply {
            title = url
        }

        sourceObj?.let {
            //Copy basics
            manga.copyFrom(sourceObj.fetchMangaDetails(manga).toBlocking().first())

            //Apply metadata
            metadataHelper.fetchMetadata(url, source == 2)?.copyTo(manga)
        }

        if(fav) manga.favorite = true

        db.insertManga(manga).executeAsBlocking().insertedId()?.let {
            manga.id = it
        }

        return manga
    }
}