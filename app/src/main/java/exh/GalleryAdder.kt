package exh

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.syncChaptersWithSource
import exh.metadata.MetadataHelper
import exh.metadata.copyTo
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    private val metadataHelper = MetadataHelper()

    fun addGallery(url: String, fav: Boolean = false): Manga {
        val source = when(URL(url).host) {
            "g.e-hentai.org", "e-hentai.org" -> EH_SOURCE_ID
            "exhentai.org" -> EXH_SOURCE_ID
            else -> throw MalformedURLException("Not a valid gallery URL!")
        }

        val sourceObj = sourceManager.get(source)
                ?: throw IllegalStateException("Could not find EH source!")

        val pathOnlyUrl = getUrlWithoutDomain(url)

        //Use manga in DB if possible, otherwise, make a new manga
        val manga = db.getManga(pathOnlyUrl, source).executeAsBlocking()
                ?: Manga.create(source).apply {
            this.url = pathOnlyUrl
            title = url
        }

        //Copy basics
        manga.copyFrom(sourceObj.fetchMangaDetails(manga).toBlocking().first())

        //Apply metadata
        metadataHelper.fetchMetadata(url, isExSource(source))?.copyTo(manga)

        if(fav) manga.favorite = true

        db.insertManga(manga).executeAsBlocking().insertedId()?.let {
            manga.id = it
        }

        //Fetch and copy chapters
        try {
            sourceObj.fetchChapterList(manga).map {
                syncChaptersWithSource(db, it, manga, sourceObj)
            }.toBlocking().first()
        } catch (e: Exception) {
            Timber.w(e, "Failed to update chapters for gallery: ${manga.title}!")
        }

        return manga
    }

    private fun getUrlWithoutDomain(orig: String): String {
        try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null)
                out += "?" + uri.query
            if (uri.fragment != null)
                out += "#" + uri.fragment
            return out
        } catch (e: URISyntaxException) {
            return orig
        }
    }
}