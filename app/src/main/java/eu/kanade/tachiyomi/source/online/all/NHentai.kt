package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.NHENTAI_SOURCE_ID
import exh.metadata.copyTo
import exh.metadata.loadNhentai
import exh.metadata.loadNhentaiAsync
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PageImageType
import exh.metadata.models.Tag
import exh.util.createUUIDObj
import exh.util.defRealm
import exh.util.realmTrans
import exh.util.urlImportFetchSearchManga
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import timber.log.Timber

/**
 * NHentai source
 */

class NHentai(context: Context) : HttpSource() {
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        //TODO There is currently no way to get the most popular mangas
        //TODO Instead, we delegate this to the latest updates thing to avoid confusing users with an empty screen
        return fetchLatestUpdates(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        TODO("Currently unavailable!")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        TODO("Currently unavailable!")
    }

    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query, {
                super.fetchSearchManga(page, query, filters)
            })

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //Currently we have no filters
        //TODO Filter builder
        val uri = Uri.parse("$baseUrl/api/galleries/search").buildUpon()
        uri.appendQueryParameter("query", query)
        uri.appendQueryParameter("page", page.toString())
        return nhGet(uri.toString(), page)
    }

    override fun searchMangaParse(response: Response)
            = parseResultPage(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val uri = Uri.parse("$baseUrl/api/galleries/all").buildUpon()
        uri.appendQueryParameter("page", page.toString())
        return nhGet(uri.toString(), page)
    }

    override fun latestUpdatesParse(response: Response)
            = parseResultPage(response)

    override fun mangaDetailsParse(response: Response)
            = parseGallery(jsonParser.parse(response.body()!!.string()).asJsonObject)

    //Used so we can use a different URL for fetching manga details and opening the details in the browser
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(urlToDetailsRequest(manga.url))
                .asObservableSuccess()
                .map { response ->
                    mangaDetailsParse(response).apply { initialized = true }
                }
    }

    override fun mangaDetailsRequest(manga: SManga)
            = nhGet(manga.url)

    fun urlToDetailsRequest(url: String)
            = nhGet(baseUrl + "/api/gallery/" + url.split("/").last { it.isNotBlank() })

    fun parseResultPage(response: Response): MangasPage {
        val res = jsonParser.parse(response.body()!!.string()).asJsonObject

        val error = res.get("error")
        if(error == null) {
            val results = res.getAsJsonArray("result")?.map {
                parseGallery(it.asJsonObject)
            }
            val numPages = res.get("num_pages")?.int
            if(results != null && numPages != null)
                return MangasPage(results, numPages > response.request().tag() as Int)
        } else {
            Timber.w("An error occurred while performing the search: $error")
        }
        return MangasPage(emptyList(), false)
    }

    fun rawParseGallery(obj: JsonObject) = realmTrans { realm ->
        val nhId = obj.get("id").asLong

        (realm.loadNhentai(nhId)
                ?: realm.createUUIDObj(NHentaiMetadata::class.java)).apply {
            this.nhId = nhId

            uploadDate = obj.get("upload_date")?.notNull()?.long

            favoritesCount = obj.get("num_favorites")?.notNull()?.long

            mediaId = obj.get("media_id")?.notNull()?.string

            obj.get("title")?.asJsonObject?.let {
                japaneseTitle = it.get("japanese")?.notNull()?.string
                shortTitle = it.get("pretty")?.notNull()?.string
                englishTitle = it.get("english")?.notNull()?.string
            }

            obj.get("images")?.asJsonObject?.let {
                coverImageType = it.get("cover")?.get("t")?.notNull()?.asString
                it.get("pages")?.asJsonArray?.map {
                    it?.asJsonObject?.get("t")?.notNull()?.asString
                }?.filterNotNull()?.map {
                    PageImageType(it)
                }?.let {
                    pageImageTypes.clear()
                    pageImageTypes.addAll(it)
                }
                thumbnailImageType = it.get("thumbnail")?.get("t")?.notNull()?.asString
            }

            scanlator = obj.get("scanlator")?.notNull()?.asString

            obj.get("tags")?.asJsonArray?.map {
                val asObj = it.asJsonObject
                Pair(asObj.get("type")?.string, asObj.get("name")?.string)
            }?.apply {
                tags.clear()
            }?.forEach {
                if(it.first != null && it.second != null)
                    tags.add(Tag(it.first!!, it.second!!, false))
            }
        }
    }

    fun parseGallery(obj: JsonObject) = rawParseGallery(obj).let {
        SManga.create().apply {
            it.copyTo(this)
        }
    }

    fun lazyLoadMetadata(url: String) =
            defRealm { realm ->
                realm.loadNhentaiAsync(NHentaiMetadata.nhIdFromUrl(url))
                        .flatMap {
                            if(it == null)
                                client.newCall(urlToDetailsRequest(url))
                                        .asObservableSuccess()
                                        .map {
                                            rawParseGallery(jsonParser.parse(it.body()!!.string())
                                                    .asJsonObject)
                                        }.first()
                            else
                                Observable.just(it)
                        }.map { realm.copyFromRealm(it) }
            }

    override fun fetchChapterList(manga: SManga)
            = lazyLoadMetadata(manga.url).map {
        listOf(SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            //TODO Get this working later
//            date_upload = it.uploadDate ?: 0
            chapter_number = 1f
        })
    }!!

    override fun fetchPageList(chapter: SChapter)
            = lazyLoadMetadata(chapter.url).map { metadata ->
        if(metadata.mediaId == null) emptyList()
        else
            metadata.pageImageTypes.mapIndexed { index, s ->
                val imageUrl = imageUrlFromType(metadata.mediaId!!, index + 1, s.type!!)
                Page(index, imageUrl!!, imageUrl)
            }
    }!!

    override fun fetchImageUrl(page: Page) = Observable.just(page.imageUrl!!)!!

    fun imageUrlFromType(mediaId: String, page: Int, t: String) = NHentaiMetadata.typeToExtension(t)?.let {
        "https://i.nhentai.net/galleries/$mediaId/$page.$it"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw NotImplementedError("Unused method called!")
    }

    override fun pageListParse(response: Response): List<Page> {
        throw NotImplementedError("Unused method called!")
    }

    override fun imageUrlParse(response: Response): String {
        throw NotImplementedError("Unused method called!")
    }

    val appName by lazy {
        context.getString(R.string.app_name)!!
    }
    fun nhGet(url: String, tag: Any? = null) = GET(url)
            .newBuilder()
            .header("User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/56.0.2924.87 " +
                            "Safari/537.36 " +
                            "$appName/${BuildConfig.VERSION_CODE}")
            .tag(tag).build()!!

    override val id = NHENTAI_SOURCE_ID

    override val lang = "all"

    override val name = "nhentai"

    override val baseUrl = NHentaiMetadata.BASE_URL

    override val supportsLatest = true

    companion object {
        val jsonParser by lazy {
            JsonParser()
        }
    }

    fun JsonElement.notNull() =
            if(this is JsonNull)
                null
            else this
}
