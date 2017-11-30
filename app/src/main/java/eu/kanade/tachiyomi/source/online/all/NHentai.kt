package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kotson.*
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
import eu.kanade.tachiyomi.source.online.LewdSource
import exh.NHENTAI_SOURCE_ID
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PageImageType
import exh.metadata.models.Tag
import exh.util.*
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import timber.log.Timber

/**
 * NHentai source
 */

class NHentai(context: Context) : HttpSource(), LewdSource<NHentaiMetadata, JsonObject> {
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

    override fun mangaDetailsParse(response: Response): SManga {
        val obj = jsonParser.parse(response.body()!!.string()).asJsonObject
        return parseToManga(NHentaiMetadata.Query(obj["id"].long), obj)
    }

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
                val obj = it.asJsonObject
                parseToManga(NHentaiMetadata.Query(obj["id"].long), obj)
            }
            val numPages = res.get("num_pages")?.int
            if(results != null && numPages != null)
                return MangasPage(results, numPages > response.request().tag() as Int)
        } else {
            Timber.w("An error occurred while performing the search: $error")
        }
        return MangasPage(emptyList(), false)
    }

    override val metaParser: NHentaiMetadata.(JsonObject) -> Unit = { obj ->
        nhId = obj["id"].asLong

        uploadDate = obj["upload_date"].nullLong

        favoritesCount = obj["num_favorites"].nullLong

        mediaId = obj["media_id"].nullString

        obj["title"].nullObj?.let { it ->
            japaneseTitle = it["japanese"].nullString
            shortTitle = it["pretty"].nullString
            englishTitle = it["english"].nullString
        }

        obj["images"].nullObj?.let {
            coverImageType = it["cover"]?.get("t").nullString
            it["pages"].nullArray?.mapNotNull {
                it?.asJsonObject?.get("t").nullString
            }?.map {
                PageImageType(it)
            }?.let {
                pageImageTypes.clear()
                pageImageTypes.addAll(it)
            }
            thumbnailImageType = it["thumbnail"]?.get("t").nullString
        }

        scanlator = obj["scanlator"].nullString

        obj["tags"]?.asJsonArray?.map {
            val asObj = it.asJsonObject
            Pair(asObj["type"].nullString, asObj["name"].nullString)
        }?.apply {
            tags.clear()
        }?.forEach {
            if(it.first != null && it.second != null)
                tags.add(Tag(it.first!!, it.second!!, false))
        }
    }

    fun lazyLoadMetadata(url: String) =
            defRealm { realm ->
                val meta = NHentaiMetadata.UrlQuery(url).query(realm).findFirst()
                if(meta == null) {
                    client.newCall(urlToDetailsRequest(url))
                            .asObservableSuccess()
                            .map {
                                realmTrans { realm ->
                                    realm.copyFromRealm(realm.createUUIDObj(queryAll().clazz.java).apply {
                                        metaParser(this,
                                                jsonParser.parse(it.body()!!.string()).asJsonObject)
                                    })
                                }
                            }
                            .first()
                } else {
                    Observable.just(realm.copyFromRealm(meta))
                }
            }

    override fun fetchChapterList(manga: SManga)
            = lazyLoadMetadata(manga.url).map {
        listOf(SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = ((it.uploadDate ?: 0) * 1000)
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

    override fun queryAll() = NHentaiMetadata.EmptyQuery()
    override fun queryFromUrl(url: String) = NHentaiMetadata.UrlQuery(url)

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
