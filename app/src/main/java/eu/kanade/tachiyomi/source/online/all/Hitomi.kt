package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.os.Build
import android.os.HandlerThread
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
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
import exh.metadata.EMULATED_TAG_NAMESPACE
import exh.metadata.models.HitomiGalleryMetadata
import exh.metadata.models.HitomiGalleryMetadata.Companion.BASE_URL
import exh.metadata.models.HitomiGalleryMetadata.Companion.LTN_BASE_URL
import exh.metadata.models.HitomiGalleryMetadata.Companion.hlIdFromUrl
import exh.metadata.models.HitomiPage
import exh.metadata.models.HitomiSkeletonGalleryMetadata
import exh.metadata.models.Tag
import exh.metadata.nullIfBlank
import exh.search.SearchEngine
import exh.util.*
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.AsyncSubject
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * WTF is going on in this class?
 */
class Hitomi(private val context: Context)
    :HttpSource(), LewdSource<HitomiGalleryMetadata, HitomiSkeletonGalleryMetadata> {
    private val jsonParser by lazy(LazyThreadSafetyMode.PUBLICATION) { JsonParser() }
    private val searchEngine by lazy { SearchEngine() }
    private val prefs: PreferencesHelper by injectLazy()

    private val queryCache = mutableMapOf<String, RealmResults<HitomiSkeletonGalleryMetadata>>()
    private val queryWorkQueue = LinkedBlockingQueue<Triple<String, Int, AsyncSubject<List<HitomiSkeletonGalleryMetadata>>>>()
    private var searchWorker: Thread? = null

    private var parseToMangaScheduler: Scheduler? = null

    override fun queryAll() = HitomiGalleryMetadata.EmptyQuery()
    override fun queryFromUrl(url: String) = HitomiGalleryMetadata.UrlQuery(url)

    override val metaParser: HitomiGalleryMetadata.(HitomiSkeletonGalleryMetadata) -> Unit = {
        hlId = it.hlId
        thumbnailUrl = it.thumbnailUrl
        artist = it.artist
        group = it.group
        type = it.type
        language = it.language
        languageSimple = it.languageSimple
        series.clear()
        series.addAll(it.series)
        characters.clear()
        characters.addAll(it.characters)
        buyLink = it.buyLink
        uploadDate = it.uploadDate
        tags.clear()
        tags.addAll(it.tags)
        title = it.title
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Unused method called!")

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    /** >>> PARSE TO MANGA SCHEDULER <<< **/
    /*
    Realm becomes very, very slow after you start opening and closing Realms rapidly.
    By keeping a global Realm open at all times, we can migitate this.
    Realms are per-thread so we create our own RxJava scheduler to schedule realm-heavy
    operations on.
     */
    @Synchronized
    private fun startParseToMangaScheduler() {
        if(parseToMangaScheduler != null) return

        val thread = object : HandlerThread("parse-to-manga-thread") {
            override fun onLooperPrepared() {
                // Open permanent Realm instance on this thread!
                Realm.getDefaultInstance()
            }
        }

        thread.start()
        parseToMangaScheduler = AndroidSchedulers.from(thread.looper)
    }

    private fun parseToMangaScheduler(): Scheduler {
        startParseToMangaScheduler()
        return parseToMangaScheduler!!
    }

    /** >>> SEARCH WORKER <<< **/
    /*
    Running RealmResults.size on a new RealmResults object is very, very slow.
    By caching our RealmResults in memory, we avoid creating many new RealmResults objects,
    thus speeding up RealmResults.size.

    Realms are per-thread and RealmResults are bound to Realms. Therefore we create a
    permanent thread that will open a permanent realm and wait for requests to load RealmResults.
     */

    @Synchronized
    private fun startSearchWorker() {
        if(searchWorker != null) return

        searchWorker = thread {
            ensureCacheLoaded().toBlocking().first()

            val realms = arrayOf(getCacheRealm(0), getCacheRealm(1))

            Timber.d("[SW] New search worker thread started!")
            while (true) {
                val realm = realms[prefs.eh_hl_lastRealmIndex().getOrDefault()]

                Timber.d("[SW] Waiting for next query!")
                val next = queryWorkQueue.take()
                Timber.d("[SW] Found new query (page ${next.second}): ${next.first}")

                if(queryCache[next.first] == null) {
                    val first = realm.where(HitomiSkeletonGalleryMetadata::class.java).findFirst()

                    if (first == null) {
                        next.third.onNext(emptyList())
                        next.third.onCompleted()
                        continue
                    }

                    val parsed = searchEngine.parseQuery(next.first)
                    val filtered = searchEngine.filterResults(realm.where(HitomiSkeletonGalleryMetadata::class.java),
                            parsed,
                            first.titleFields).findAll()

                    queryCache[next.first] = filtered
                }

                val filtered = queryCache[next.first]!!

                val beginIndex = (next.second - 1) * PAGE_SIZE
                if (beginIndex > filtered.lastIndex) {
                    next.third.onNext(emptyList())
                    next.third.onCompleted()
                    continue
                }

                // Chunk into pages of 100
                val res = realm.copyFromRealm(filtered.subList(beginIndex,
                        Math.min(next.second * PAGE_SIZE, filtered.size)))

                next.third.onNext(res)
                next.third.onCompleted()
            }
        }
    }

    private fun trySearch(page: Int, query: String): Observable<List<HitomiSkeletonGalleryMetadata>> {
        startSearchWorker()

        val subject = AsyncSubject.create<List<HitomiSkeletonGalleryMetadata>>()
        queryWorkQueue.clear()
        queryWorkQueue.add(Triple(query, page, subject))
        return subject
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return urlImportFetchSearchManga(query) {
            trySearch(page, query).map {
                val res = it.map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.url!!)

                        title = it.title!!

                        it.thumbnailUrl?.let {
                            thumbnail_url = it
                        }
                    }
                }

                MangasPage(res, it.isNotEmpty())
            }
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return lazyLoadMetaPages(HitomiGalleryMetadata.hlIdFromUrl(manga.url), true)
                .map {
                    val newManga = parseToManga(queryFromUrl(manga.url), it.first)
                    manga.copyFrom(newManga)
                    // Forcibly copy title as copyFrom does not
                    manga.title = newManga.title

                    manga
                }
                .subscribeOn(parseToMangaScheduler())
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return lazyLoadMeta(queryFromUrl(manga.url),
                lazyLoadMetaPages(hlIdFromUrl(manga.url), false).map { it.first }
        ).map {
            listOf(SChapter.create().apply {
                url = readerUrl(it.hlId!!)

                name = "Chapter"

                chapter_number = 1f

                it.uploadDate?.let {
                    date_upload = it
                }
            })
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val hlId = chapter.url.substringAfterLast('/').removeSuffix(".html")
        return lazyLoadMetaPages(hlId, false).map { (_, it) ->
            it.mapIndexed { index, s ->
                Page(index, s, s)
            }
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override val name = "hitomi.la (very slow search)"

    override val baseUrl = BASE_URL

    override val lang = "all"

    override val id = HITOMI_SOURCE_ID

    override val supportsLatest = true

    private val cacheLocks = arrayOf(ReentrantLock(), ReentrantLock())

    override fun popularMangaRequest(page: Int) = GET("$LTN_BASE_URL/popular-all.nozomi")

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    override fun latestUpdatesRequest(page: Int) = GET("$LTN_BASE_URL/index-all.nozomi")

    fun readerUrl(hlId: String) = "$BASE_URL/reader/$hlId.html"

    private fun lazyLoadMetaPages(hlId: String, forceReload: Boolean):
            Observable<Pair<HitomiSkeletonGalleryMetadata, List<String>>> {
        val pages = defRealm { realm ->
            val rres = realm.where(HitomiPage::class.java)
                    .equalTo(HitomiPage::gallery.name, hlId)
                    .sort(HitomiPage::index.name)
                    .findAll()

            if (rres.isNotEmpty())
                rres.map(HitomiPage::url)
            else null
        }

        val meta = getAvailableCacheRealm()?.use {
            val res = it.where(HitomiSkeletonGalleryMetadata::class.java)
                    .equalTo(HitomiSkeletonGalleryMetadata::hlId.name, hlId)
                    .findFirst()

            // Force reload if no thumbnail
            if(res?.thumbnailUrl == null) null else res
        }

        if(pages != null && meta != null && !forceReload) {
            return Observable.just(meta to pages)
        }

        val loc = "$BASE_URL/galleries/$hlId.html"
        val req = GET(loc)

        return client.newCall(req).asObservableSuccess().map { response ->
            val doc = response.asJsoup()

            Duktape.create().use { duck ->
                val thumbs = doc.getElementsByTag("script").find {
                    it.html().startsWith("var thumbnails")
                }

                val parsedThumbs = jsonParser.parse(thumbs!!.html()
                        .removePrefix("var thumbnails = ")
                        .removeSuffix(";")).array

                // Get pages (drop last element as its always null)
                val newPages = parsedThumbs.take(parsedThumbs.size() - 1).mapIndexed { index, item ->
                    val itemName = item.string
                            .substringAfterLast('/')
                            .removeSuffix(".jpg")

                    val url = "//a.hitomi.la/galleries/$hlId/$itemName"

                    val resolved = resolveImage(duck, url)
                    HitomiPage().apply {
                        gallery = hlId
                        this.index = index
                        this.url = resolved
                    }
                }

                // Parse meta
                val galleryParent = doc.select(".gallery")

                val newMeta = HitomiSkeletonGalleryMetadata().apply {
                    url = loc

                    title = galleryParent.select("h1 > a").text()

                    artist = galleryParent.select("h2 > .comma-list > li").joinToString { it.text() }.nullIfBlank()

                    thumbnailUrl = "https:" + doc.select(".cover img").attr("src")

                    uploadDate = DATE_FORMAT.parse(doc.select(".date").text()).time

                    galleryParent.select(".gallery-info tr").forEach { element ->
                        val content = element.child(1)

                        when(element.child(0).text().toLowerCase()) {
                            "group" -> group = content.text().trim()
                            "type" -> type = content.text().trim()
                            "language" -> {
                                language = content.text().trim()
                                languageSimple = content.select("a")
                                        .attr("href")
                                        .split("-").getOrNull(1) ?: "speechless"
                            }
                            "series" -> {
                                series.clear()
                                series.addAll(content.select("li").map(Element::text))
                            }
                            "characters" -> {
                                characters.clear()
                                characters.addAll(content.select("li").map(Element::text))
                            }
                            "tags" -> {
                                tags.clear()
                                tags.addAll(content.select("li").map {
                                    val txt = it.text()

                                    val ns: String
                                    val name: String

                                    when {
                                        txt.endsWith(CHAR_MALE) -> {
                                            ns = "male"
                                            name = txt.removeSuffix(CHAR_MALE).trim()
                                        }
                                        txt.endsWith(CHAR_FEMALE) -> {
                                            ns = "female"
                                            name = txt.removeSuffix(CHAR_FEMALE).trim()
                                        }
                                        else -> {
                                            ns = EMULATED_TAG_NAMESPACE
                                            name = txt.trim()
                                        }
                                    }

                                    Tag(ns, name)
                                })
                            }
                        }
                    }

                    // Inject pseudo tags
                    fun String?.nullNaTag(name: String) {
                        if(this == null || this == NOT_AVAILABLE) return

                        tags.add(Tag(name, this))
                    }

                    group.nullNaTag("group")
                    artist.nullNaTag("artist")
                    languageSimple.nullNaTag("language")
                    series.forEach {
                        it.nullNaTag("parody")
                    }
                    characters.forEach {
                        it.nullNaTag("character")
                    }
                    type.nullNaTag("category")
                }

                realmTrans {
                    // Delete old pages
                    it.where(HitomiPage::class.java)
                            .equalTo(HitomiPage::gallery.name, hlId)
                            .findAll().deleteAllFromRealm()

                    // Add new pages
                    it.insert(newPages)
                }

                (0 .. 1).map { getCacheRealm(it) }.forEach { realm ->
                    realm.useTrans {
                        // Delete old meta
                        it.where(HitomiSkeletonGalleryMetadata::class.java)
                                .equalTo(HitomiSkeletonGalleryMetadata::hlId.name, hlId)
                                .findAll().deleteAllFromRealm()

                        // Add new meta
                        it.insert(newMeta)
                    }
                }

                newMeta to newPages.map(HitomiPage::url)
            }
        }
    }

    private fun fetchAndResolveRequest(page: Int, request: Request): Observable<MangasPage> {
        //Begin pre-loading cache
        ensureCacheLoaded(false).subscribeOn(Schedulers.computation()).subscribe()

        return client.newCall(request)
                .asObservableSuccess()
                .map { response ->
                    val buffer = ByteBuffer.wrap(response.body()!!.bytes())

                    val out = mutableListOf<SManga>()

                    try {
                        while(true) {
                            out += SManga.create().apply {
                                setUrlWithoutDomain("$BASE_URL/galleries/${buffer.int}.html")

                                title = "Loading..."
                            }
                        }
                    } catch(e: BufferUnderflowException) {}

                    val offset = PAGE_SIZE * (page - 1)
                    val endIndex = Math.min(offset + PAGE_SIZE, out.size)

                    MangasPage(out.subList(offset, endIndex),
                            endIndex < out.size)
                }

    }

    override fun fetchPopularManga(page: Int)
            = fetchAndResolveRequest(page, popularMangaRequest(page))
    override fun fetchLatestUpdates(page: Int)
            = fetchAndResolveRequest(page, latestUpdatesRequest(page))

    private fun shouldRefreshGalleryFiles(): Boolean {
        val timeDiff = System.currentTimeMillis() - prefs.eh_hl_lastRefresh().getOrDefault()
        return timeDiff > prefs.eh_hl_refreshFrequency().getOrDefault().toLong() * 60L * 60L * 1000L
    }

    private inline fun <T> lockCache(index: Int, block: () -> T): T {
        cacheLocks[index].lock()
        try {
            return block()
        } finally {
            cacheLocks[index].unlock()
        }
    }

    private fun loadGalleryMetadata(url: String): Observable<HitomiSkeletonGalleryMetadata> {
        val mid = HitomiGalleryMetadata.hlIdFromUrl(url)

        return ensureCacheLoaded().map {
            getAvailableCacheRealm()?.use { realm ->
                findCacheMetadataById(realm, mid)
            }
        }
    }

    private fun findCacheMetadataById(realm: Realm, hlId: String): HitomiSkeletonGalleryMetadata? {
        return realm.where(HitomiSkeletonGalleryMetadata::class.java)
                .equalTo(HitomiSkeletonGalleryMetadata::hlId.name, hlId)
                .findFirst()?.let { realm.copyFromRealm(it) }
    }

    fun ensureCacheLoaded(blocking: Boolean = true): Observable<Any> {
        return Observable.fromCallable {
            if(prefs.eh_hl_lastRealmIndex().getOrDefault() >= 0) { return@fromCallable Any() }

            val nextRealmIndex = when(prefs.eh_hl_lastRealmIndex().getOrDefault()) {
                0 -> 1
                1 -> 0
                else -> 0
            }

            if(!blocking && cacheLocks[nextRealmIndex].isLocked) return@fromCallable Any()

            lockCache(nextRealmIndex) {
                val shouldRefresh = shouldRefreshGalleryFiles()
                getCacheRealm(nextRealmIndex).useTrans { realm ->
                    if (!realm.isEmpty && !shouldRefresh)
                        return@fromCallable Any()

                    realm.deleteAll()
                }

                val cores = Runtime.getRuntime().availableProcessors()
                Timber.d("Starting $cores threads to parse hitomi.la gallery data...")

                val workQueue = ConcurrentLinkedQueue<Int>((0 until GALLERY_CHUNK_COUNT).toList())
                val threads = mutableListOf<Thread>()

                for(threadIndex in 1 .. cores) {
                    threads += thread {
                        getCacheRealm(nextRealmIndex).use { realm ->
                            while (true) {
                                val i = workQueue.poll() ?: break

                                Timber.d("[$threadIndex] Downloading + parsing hitomi.la gallery data ${i + 1}/$GALLERY_CHUNK_COUNT...")

                                val url = "https://ltn.hitomi.la/galleries$i.json"

                                val resp = client.newCall(GET(url)).execute().body()!!

                                val out = mutableListOf<HitomiSkeletonGalleryMetadata>()

                                JsonReader(resp.charStream()).use { reader ->
                                    reader.beginArray()

                                    while (reader.hasNext()) {
                                        val gallery = HitomiGallery.fromJson(reader.nextJsonObject())
                                        val meta = HitomiSkeletonGalleryMetadata()
                                        gallery.addToGalleryMeta(meta)

                                        out.add(meta)
                                    }
                                }

                                Timber.d("[$threadIndex] Saving hitomi.la gallery data ${i + 1}/$GALLERY_CHUNK_COUNT...")

                                realm.trans {
                                    realm.insert(out)
                                }
                            }
                        }
                    }
                }

                threads.forEach(Thread::join)

                // Update refresh time
                prefs.eh_hl_lastRefresh().set(System.currentTimeMillis())

                // Update last refreshed realm
                prefs.eh_hl_lastRealmIndex().set(nextRealmIndex)

                Timber.d("Successfully refreshed realm #$nextRealmIndex!")
            }

            return@fromCallable Any()
        }
    }

    private fun resolveImage(duktape: Duktape, url: String): String {
        return "https:" + duktape.evaluate(IMAGE_RESOLVER.replace(IMAGE_RESOLVER_URL_VAR, url)) as String
    }

    private fun HitomiGallery.addToGalleryMeta(meta: HitomiSkeletonGalleryMetadata) {
        with(meta) {
            hlId = id.toString()
            title = name
            // Intentionally avoid setting thumbnails
            // We need another request to get them anyways
            artist = artists.firstOrNull()
            group = groups.firstOrNull()
            type = this@addToGalleryMeta.type
            languageSimple = language
            series.clear()
            series.addAll(parodies)
            characters.clear()
            characters.addAll(this@addToGalleryMeta.characters)

            tags.clear()
            this@addToGalleryMeta.tags.mapTo(tags) { Tag(it.key, it.value) }
        }
    }

    private fun <T> getAndLockAvailableCacheRealm(block: (Realm) -> T): T? {
        val index = prefs.eh_hl_lastRealmIndex().getOrDefault()

        return if(index >= 0) {
            val cache = getCacheRealm(index)
            lockCache(index) {
                block(cache)
            }
        } else {
            null
        }
    }

    private fun getAvailableCacheRealm(): Realm? {
        val index = prefs.eh_hl_lastRealmIndex().getOrDefault()

        return if(index >= 0) {
            getCacheRealm(index)
        } else {
            null
        }
    }

    private fun getCacheRealm(index: Int) = Realm.getInstance(getRealmConfig(index))

    private fun getRealmConfig(index: Int) = RealmConfiguration.Builder()
            .name("hitomi-cache-$index")
            .deleteRealmIfMigrationNeeded()
            .build()

    fun forceEnsureCacheLoaded(): Boolean {
        // Lock all caches
        if(!cacheLocks[0].tryLock() || !cacheLocks[1].tryLock()) {
            if(cacheLocks[0].isHeldByCurrentThread)
                cacheLocks[0].unlock()
            if(cacheLocks[1].isHeldByCurrentThread)
                cacheLocks[1].unlock()

            return false
        }

        try {
            prefs.eh_hl_lastRealmIndex().set(-1)
            prefs.eh_hl_lastRefresh().set(0)
            ensureCacheLoaded(false).subscribeOn(Schedulers.computation()).subscribe()
        } finally {
            cacheLocks[0].unlock()
            cacheLocks[1].unlock()
        }

        return true
    }

    companion object {
        private val PAGE_SIZE = 25
        private val CHAR_MALE = "♂"
        private val CHAR_FEMALE = "♀"
        private val GALLERY_CHUNK_COUNT = 20
        private val IMAGE_RESOLVER_URL_VAR = "%IMAGE_URL%"
        private val NOT_AVAILABLE = "N/A"
        private val DATE_FORMAT by lazy {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                SimpleDateFormat("yyyy-MM-dd HH:mm:ssX", Locale.US)
            else
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss'-05'", Locale.US)
        }
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
                        EMULATED_TAG_NAMESPACE to str
                } ?: emptyMap(),
                obj.mapNullStringList("c"),
                obj["type"].string,
                obj["l"].nullString)

        private fun JsonObject.mapNullStringList(key: String)
                = this[key]?.nullArray?.map { it.string } ?: emptyList()
    }
}
