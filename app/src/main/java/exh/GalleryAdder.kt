package exh

import android.net.Uri
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.syncChaptersWithSource
import uy.kohesive.injekt.injectLazy

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    fun addGallery(url: String,
                   fav: Boolean = false,
                   forceSource: UrlImportableSource? = null,
                   throttleFunc: () -> Unit = {}): GalleryAddEvent {
        XLog.d("Importing gallery (url: %s, fav: %s, forceSource: %s)...", url, fav, forceSource)
        try {
            val uri = Uri.parse(url)

            // Find matching source
            val source = if(forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) forceSource
                    else return GalleryAddEvent.Fail.UnknownType(url)
                } catch(e: Exception) {
                    XLog.e("Source URI match check error!", e)
                    return GalleryAddEvent.Fail.UnknownType(url)
                }
            } else {
                sourceManager.getVisibleCatalogueSources()
                        .filterIsInstance<UrlImportableSource>()
                        .find {
                            try {
                                it.matchesUri(uri)
                            } catch(e: Exception) {
                                XLog.e("Source URI match check error!", e)
                                false
                            }
                        } ?: return GalleryAddEvent.Fail.UnknownType(url)
            }

            // Map URL to manga URL
            val realUrl = try {
                source.mapUrlToMangaUrl(uri)
            } catch(e: Exception) {
                XLog.e("Source URI map-to-manga error!", e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url)

            // Clean URL
            val cleanedUrl = try {
                source.cleanMangaUrl(realUrl)
            } catch(e: Exception) {
                XLog.e("Source URI clean error!", e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url)

            //Use manga in DB if possible, otherwise, make a new manga
            val manga = db.getManga(cleanedUrl, source.id).executeAsBlocking()
                    ?: Manga.create(source.id).apply {
                this.url = cleanedUrl
                title = realUrl
            }

            // Insert created manga if not in DB before fetching details
            // This allows us to keep the metadata when fetching details
            if(manga.id == null) {
                db.insertManga(manga).executeAsBlocking().insertedId()?.let {
                    manga.id = it
                }
            }

            // Fetch and copy details
            val newManga = source.fetchMangaDetails(manga).toBlocking().first()
            manga.copyFrom(newManga)
            manga.initialized = true

            if (fav) manga.favorite = true

            db.insertManga(manga).executeAsBlocking()

            //Fetch and copy chapters
            try {
                val chapterListObs = if(source is EHentai) {
                    source.fetchChapterList(manga, throttleFunc)
                } else {
                    source.fetchChapterList(manga)
                }
                chapterListObs.map {
                    syncChaptersWithSource(db, it, manga, source)
                }.toBlocking().first()
            } catch (e: Exception) {
                XLog.w("Failed to update chapters for gallery: ${manga.title}!", e)
                return GalleryAddEvent.Fail.Error(url, "Failed to update chapters for gallery: $url")
            }

            return GalleryAddEvent.Success(url, manga)
        } catch(e: Exception) {
            XLog.w("Could not add gallery (url: $url)!", e)

            if(e is EHentai.GalleryNotFoundException) {
                return GalleryAddEvent.Fail.NotFound(url)
            }

            return GalleryAddEvent.Fail.Error(url,
                    ((e.message ?: "Unknown error!") + " (Gallery: $url)").trim())
        }
    }
}

sealed class GalleryAddEvent {
    abstract val logMessage: String
    abstract val galleryUrl: String
    open val galleryTitle: String? = null

    class Success(override val galleryUrl: String,
                  val manga: Manga): GalleryAddEvent() {
        override val galleryTitle = manga.title
        override val logMessage = "Added gallery: $galleryTitle"
    }

    sealed class Fail: GalleryAddEvent() {
        class UnknownType(override val galleryUrl: String): Fail() {
            override val logMessage = "Unknown gallery type for gallery: $galleryUrl"
        }

        open class Error(override val galleryUrl: String,
                    override val logMessage: String): Fail()

        class NotFound(galleryUrl: String):
                Error(galleryUrl, "Gallery does not exist: $galleryUrl")
    }
}