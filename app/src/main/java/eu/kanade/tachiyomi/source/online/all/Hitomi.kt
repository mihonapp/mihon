package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.HITOMI_SOURCE_ID
import exh.metadata.models.HitomiGalleryMetadata
import exh.metadata.models.HitomiGalleryMetadata.Companion.BASE_URL
import exh.metadata.models.HitomiGalleryMetadata.Companion.urlFromHlId
import exh.metadata.models.Tag
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class Hitomi(private val context: Context)
    :HttpSource(), LewdSource<HitomiGalleryMetadata, HitomiGallery> {
    override fun queryAll() = HitomiGalleryMetadata.EmptyQuery()
    override fun queryFromUrl(url: String) = HitomiGalleryMetadata.UrlQuery(url)

    override val metaParser: HitomiGalleryMetadata.(HitomiGallery) -> Unit = {
        hlId = it.id.toString()
        title = it.name
        thumbnailUrl = resolveImage("//g.hitomi.la/galleries/$hlId/001.jpg")
        artist = it.artists.firstOrNull()
        group = it.groups.firstOrNull()
        type = it.type
        languageSimple = it.language
        series.clear()
        series.addAll(it.parodies)
        characters.clear()
        characters.addAll(it.characters)

        tags.clear()
        it.tags.mapTo(tags) { Tag(it.key, it.value) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Unused method called!")

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return loadGalleryMetadata(manga.url).map {
            parseToManga(queryFromUrl(manga.url), it)
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return lazyLoadMeta(queryFromUrl(manga.url),
            loadAllGalleryMetadata().map {
                val mid = HitomiGalleryMetadata.hlIdFromUrl(manga.url)
                it.find { it.id.toString() == mid }
            }
        ).map {
            listOf(SChapter.create().apply {
                url = "$BASE_URL/reader/${it.hlId}.html"

                name = "Chapter"

                chapter_number = 1f
            })
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select(".img-url").mapIndexed { index, element ->
            val resolved = resolveImage(element.text())
            Page(index, resolved, resolved)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override val name = "hitomi.la"

    override val baseUrl = BASE_URL

    override val lang = "all"

    override val id = HITOMI_SOURCE_ID

    override val supportsLatest = true

    private val prefs: PreferencesHelper by injectLazy()

    private val jsonParser by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JsonParser()
    }

    private val cacheLock = ReentrantLock()

    private var metaCache: List<HitomiGallery>? = null

    override fun popularMangaRequest(page: Int) = GET("$BASE_URL/popular-all-$page.html")

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun latestUpdatesRequest(page: Int) = GET("$BASE_URL/index-all-2.html")

    private fun resolveMangaIds(doc: Document, data: List<HitomiGallery>): List<HitomiGallery> {
        return doc.select(".gallery-content > div > a").mapNotNull {
            val id = HitomiGalleryMetadata.hlIdFromUrl(it.attr("href"))
            data.find { it.id.toString() == id }
        }
    }

    private fun fetchAndResolveRequest(request: Request): Observable<MangasPage> {
        return loadAllGalleryMetadata().flatMap {
            client.newCall(request)
                    .asObservableSuccess()
                    .map { response ->
                        val doc = response.asJsoup()
                        val res = resolveMangaIds(doc, it)
                        val sManga = res.map {
                            parseToManga(queryFromUrl(urlFromHlId(it.id.toString())), it)
                        }
                        val hasNextPage = doc.select(".page-container > ul > li:last-child > a").isNotEmpty()
                        MangasPage(sManga, hasNextPage)
                    }
        }
    }

    override fun fetchPopularManga(page: Int)
            = fetchAndResolveRequest(popularMangaRequest(page))
    override fun fetchLatestUpdates(page: Int)
            = fetchAndResolveRequest(latestUpdatesRequest(page))

    private fun galleryFile(index: Int)
            = File(context.cacheDir.absoluteFile, "hitomi/galleries$index.json")

    private fun shouldRefreshGalleryFiles(): Boolean {
        val timeDiff = System.currentTimeMillis() - prefs.eh_hl_lastRefresh().getOrDefault()
        return timeDiff > prefs.eh_hl_refreshFrequency().getOrDefault().toLong() * 60L * 60L * 1000L
    }

    private inline fun <T> lockCache(block: () -> T): T {
        cacheLock.lock()
        try {
            return block()
        } finally {
            cacheLock.unlock()
        }
    }

    private fun loadGalleryMetadata(url: String): Observable<HitomiGallery> {
        return loadAllGalleryMetadata().map {
            val mid = HitomiGalleryMetadata.hlIdFromUrl(url)
            it.find { it.id.toString() == mid }
        }
    }

    private fun loadAllGalleryMetadata(): Observable<List<HitomiGallery>> {
        val shouldRefresh = shouldRefreshGalleryFiles()

        metaCache?.let {
            if(!shouldRefresh) {
                return Observable.just(metaCache)
            }
        }

        var obs: Observable<List<String>> = Observable.just(emptyList())

        var refresh = false

        for (i in 0 until GALLERY_CHUNK_COUNT) {
            val cacheFile = galleryFile(i)
            val newObs = if(shouldRefresh || !cacheFile.exists()) {
                val url = "https://ltn.hitomi.la/galleries$i.json"

                refresh = true

                client.newCall(GET(url)).asObservableSuccess().map {
                    it.body()!!.string().apply {
                        lockCache {
                            cacheFile.parentFile.mkdirs()
                            cacheFile.writeText(this)
                        }
                    }
                }
            } else {
                // Load galleries from cache
                Observable.fromCallable {
                    lockCache {
                        cacheFile.readText()
                    }
                }
            }

            obs = obs.flatMap { l ->
                newObs.map {
                    l + it
                }
            }
        }

        // Update refresh time if we refreshed
        if(refresh)
            prefs.eh_hl_lastRefresh().set(System.currentTimeMillis())

        return obs.map {
            val res = it.flatMap {
                jsonParser.parse(it).array.map {
                    HitomiGallery.fromJson(it.obj)
                }
            }

            metaCache = res
            res
        }
    }

    private fun resolveImage(url: String): String {
        return Duktape.create().use {
            it.evaluate(IMAGE_RESOLVER.replace(IMAGE_RESOLVER_URL_VAR, url)) as String
        }
    }

    companion object {
        private val GALLERY_CHUNK_COUNT = 20
        private val IMAGE_RESOLVER_URL_VAR = "%IMAGE_URL%"
        private val IMAGE_RESOLVER = """
            (function() {
var adapose = false; // Currently not sure what this does, it switches out frontend URL when we right click???
var number_of_frontends = 2;
function subdomain_from_galleryid(g) {
        if (adapose) {
                return '0';
        }
        return String.fromCharCode(97 + (g % number_of_frontends));
}
function subdomain_from_url(url, base) {
        var retval = 'a';
        if (base) {
                retval = base;
        }

        var r = /\/(\d+)\//;
        var m = r.exec(url);
        var g;
        if (m) {
                g = parseInt(m[1]);
        }
        if (g) {
                retval = subdomain_from_galleryid(g) + retval;
        }

        return retval;
}
function url_from_url(url, base) {
        return url.replace(/\/\/..?\.hitomi\.la\//, '//'+subdomain_from_url(url, base)+'.hitomi.la/');
}

return url_from_url('$IMAGE_RESOLVER_URL_VAR');
})();
            """.trimIndent()
    }
}

data class HitomiGallery(val artists: List<String>,
                         val parodies: List<String>,
                         val id: Int,
                         val name: String,
                         val groups: List<String>,
                         val tags: Map<String, String>,
                         val characters: List<String>,
                         val type: String,
                         val language: String?) {
    companion object {
        fun fromJson(obj: JsonObject): HitomiGallery
                = HitomiGallery(
                obj.mapNullStringList("a"),
                obj.mapNullStringList("p"),
                obj["id"].int,
                obj["n"].string,
                obj.mapNullStringList("g"),
                obj["t"]?.nullArray?.associate {
                    val str = it.string
                    if(str.contains(":"))
                        str.substringBefore(':') to str.substringAfter(':')
                    else
                        "tag" to str
                } ?: emptyMap(),
                obj.mapNullStringList("c"),
                obj["type"].string,
                obj["l"].nullString)

        private fun JsonObject.mapNullStringList(key: String)
                = this[key]?.nullArray?.map { it.string } ?: emptyList()
    }
}
