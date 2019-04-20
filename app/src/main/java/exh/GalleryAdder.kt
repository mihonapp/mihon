package exh

import android.net.Uri
import com.elvishew.xlog.XLog
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.syncChaptersWithSource
import exh.metadata.metadata.EHentaiSearchMetadata
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    private val networkHelper: NetworkHelper by injectLazy()

    companion object {
        const val EH_API_BASE = "https://api.e-hentai.org/api.php"
        val JSON = MediaType.parse("application/json; charset=utf-8")!!
    }

    fun getGalleryUrlFromPage(url: String): String {
        val uri = Uri.parse(url)
        val lastSplit = uri.pathSegments.last().split("-")
        val pageNum = lastSplit.last()
        val gallery = lastSplit.first()
        val pageToken = uri.pathSegments.elementAt(1)

        val json = JsonObject()
        json["method"] = "gtoken"
        json["pagelist"] = JsonArray().apply {
            add(JsonArray().apply {
                add(gallery.toInt())
                add(pageToken)
                add(pageNum.toInt())
            })
        }

        val outJson = JsonParser().parse(networkHelper.client.newCall(Request.Builder()
                .url(EH_API_BASE)
                .post(RequestBody.create(JSON, json.toString()))
                .build()).execute().body()!!.string()).obj

        val obj = outJson["tokenlist"].array.first()
        return "${uri.scheme}://${uri.host}/g/${obj["gid"].int}/${obj["token"].string}/"
    }

    fun addGallery(url: String,
                   fav: Boolean = false,
                   forceSource: Long? = null,
                   throttleFunc: () -> Unit = {}): GalleryAddEvent {
        XLog.d("Importing gallery (url: %s, fav: %s, forceSource: %s)...", url, fav, forceSource)
        try {
            val urlObj = Uri.parse(url)
            val lowercasePs = urlObj.pathSegments.map(String::toLowerCase)
            val lcFirstPathSegment = lowercasePs[0]
            val source = when (urlObj.host.toLowerCase()) {
                "g.e-hentai.org", "e-hentai.org" -> EH_SOURCE_ID
                "exhentai.org" -> EXH_SOURCE_ID
                "nhentai.net" -> NHENTAI_SOURCE_ID
                "www.perveden.com" -> {
                    when(lowercasePs[1]) {
                        "en-manga" -> PERV_EDEN_EN_SOURCE_ID
                        "it-manga" -> PERV_EDEN_IT_SOURCE_ID
                        else -> return GalleryAddEvent.Fail.UnknownType(url)
                    }
                }
                "hentai.cafe" -> HENTAI_CAFE_SOURCE_ID
                "www.tsumino.com" -> TSUMINO_SOURCE_ID
                "hitomi.la" -> HITOMI_SOURCE_ID
                else -> return GalleryAddEvent.Fail.UnknownType(url)
            }

            if(forceSource != null && source != forceSource) {
                return GalleryAddEvent.Fail.UnknownType(url)
            }

            val sourceObj = sourceManager.get(source)
                    ?: return GalleryAddEvent.Fail.Error(url, "Source not installed!")

            val realUrl = when(source) {
                EH_SOURCE_ID, EXH_SOURCE_ID -> when (lcFirstPathSegment) {
                    "g" -> {
                        //Is already gallery page, do nothing
                        url
                    }
                    "s" -> {
                        //Is page, fetch gallery token and use that
                        getGalleryUrlFromPage(url)
                    }
                    else -> return GalleryAddEvent.Fail.UnknownType(url)
                }
                NHENTAI_SOURCE_ID -> {
                    if(lcFirstPathSegment != "g")
                        return GalleryAddEvent.Fail.UnknownType(url)

                    "https://nhentai.net/g/${urlObj.pathSegments[1]}/"
                }
                PERV_EDEN_EN_SOURCE_ID,
                PERV_EDEN_IT_SOURCE_ID -> {
                    val uri = Uri.parse("http://www.perveden.com/").buildUpon()
                    urlObj.pathSegments.take(3).forEach {
                        uri.appendPath(it)
                    }
                    uri.toString()
                }
                HENTAI_CAFE_SOURCE_ID -> {
                    if(lcFirstPathSegment == "manga")
                        "https://hentai.cafe/${urlObj.pathSegments[2]}"
                    
                    "https://hentai.cafe/$lcFirstPathSegment"
                }
                TSUMINO_SOURCE_ID -> {
                    if(lcFirstPathSegment != "read" && lcFirstPathSegment != "book")
                        return GalleryAddEvent.Fail.UnknownType(url)
                        
                    "https://tsumino.com/Book/Info/${urlObj.pathSegments[2]}"
                }
                HITOMI_SOURCE_ID -> {
                    if(lcFirstPathSegment != "galleries" && lcFirstPathSegment != "reader")
                        return GalleryAddEvent.Fail.UnknownType(url)

                    "https://hitomi.la/galleries/${urlObj.pathSegments[1].substringBefore('.')}.html"
                }
                else -> return GalleryAddEvent.Fail.UnknownType(url)
            }

            val cleanedUrl = when(source) {
                EH_SOURCE_ID, EXH_SOURCE_ID -> EHentaiSearchMetadata.normalizeUrl(getUrlWithoutDomain(realUrl))
                NHENTAI_SOURCE_ID -> getUrlWithoutDomain(realUrl)
                PERV_EDEN_EN_SOURCE_ID,
                PERV_EDEN_IT_SOURCE_ID -> getUrlWithoutDomain(realUrl)
                HENTAI_CAFE_SOURCE_ID -> getUrlWithoutDomain(realUrl)
                TSUMINO_SOURCE_ID -> getUrlWithoutDomain(realUrl)
                HITOMI_SOURCE_ID -> getUrlWithoutDomain(realUrl)
                else -> return GalleryAddEvent.Fail.UnknownType(url)
            }

            //Use manga in DB if possible, otherwise, make a new manga
            val manga = db.getManga(cleanedUrl, source).executeAsBlocking()
                    ?: Manga.create(source).apply {
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
            val newManga = sourceObj.fetchMangaDetails(manga).toBlocking().first()
            manga.copyFrom(newManga)
            manga.initialized = true

            if (fav) manga.favorite = true

            db.insertManga(manga).executeAsBlocking()

            //Fetch and copy chapters
            try {
                val chapterListObs = if(sourceObj is EHentai) {
                    sourceObj.fetchChapterList(manga, throttleFunc)
                } else {
                    sourceObj.fetchChapterList(manga)
                }
                chapterListObs.map {
                    syncChaptersWithSource(db, it, manga, sourceObj)
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

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null)
                out += "?" + uri.query
            if (uri.fragment != null)
                out += "#" + uri.fragment
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }
}

sealed class GalleryAddEvent {
    abstract val logMessage: String
    abstract val galleryUrl: String
    open val galleryTitle: String? = null

    class Success(override val galleryUrl: String,
                  val manga: Manga): GalleryAddEvent() {
        override val logMessage = "Added gallery: $galleryTitle"
        override val galleryTitle: String
            get() = manga.title
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