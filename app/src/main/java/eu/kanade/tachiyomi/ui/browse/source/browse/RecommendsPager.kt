package eu.kanade.tachiyomi.ui.browse.source.browse

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaImpl
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable
import rx.schedulers.Schedulers

open class RecommendsPager(
    val manga: Manga,
    val smart: Boolean = true,
    var preferredApi: API = API.MYANIMELIST
) : Pager() {
    private val client = OkHttpClient.Builder().build()

    private fun myAnimeList(): Observable<List<SMangaImpl>>? {
        fun getId(): Observable<String> {
            val endpoint =
                myAnimeListEndpoint.toHttpUrlOrNull()
                    ?: throw Exception("Could not convert endpoint url")
            val urlBuilder = endpoint.newBuilder()
            urlBuilder.addPathSegment("search")
            urlBuilder.addPathSegment("manga")
            urlBuilder.addQueryParameter("q", manga.title)
            val url = urlBuilder.build().toUrl()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            return client.newCall(request)
                .asObservableSuccess().subscribeOn(Schedulers.io())
                .map { netResponse ->
                    val responseBody = netResponse.body?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = JsonParser.parseString(responseBody).obj
                    val results = response["results"].array
                    val firstResult = results[0].obj
                    val id = firstResult["mal_id"].string
                    if (id.isEmpty()) {
                        throw Exception("Not found")
                    }
                    id
                }
        }

        return getId().map { id ->
            val endpoint =
                myAnimeListEndpoint.toHttpUrlOrNull()
                    ?: throw Exception("Could not convert endpoint url")
            val urlBuilder = endpoint.newBuilder()
            urlBuilder.addPathSegment("manga")
            urlBuilder.addPathSegment(id)
            urlBuilder.addPathSegment("recommendations")
            val url = urlBuilder.build().toUrl()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request)
                .asObservableSuccess().subscribeOn(Schedulers.io())
                .map { netResponse ->
                    val responseBody = netResponse.body?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = JsonParser.parseString(responseBody).obj
                    val recommendations = response["recommendations"].array
                    recommendations.map { rec ->
                        Log.d("MYANIMELIST RECOMMEND", "${rec["title"].string}")
                        SMangaImpl().apply {
                            this.title = rec["title"].string
                            this.thumbnail_url = rec["image_url"].string
                            this.initialized = true
                            this.url = rec["url"].string
                        }
                    }
                }.toBlocking().first()
        }
    }

    private fun anilist(): Observable<List<SMangaImpl>>? {
        val query =
            """
            {
                Media(search: "$manga.title", type: MANGA) {
                    title{
                        romaji
                        english
                        native
                    }
                    recommendations {
                        edges {
                            node {
                                mediaRecommendation {
                                    siteUrl
                                    title {
                                        romaji
                                    }
                                    coverImage {
                                        large
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        val variables = jsonObject()
        val payload = jsonObject(
            "query" to query,
            "variables" to variables
        )
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(anilistEndpoint)
            .post(body)
            .build()

        return client.newCall(request)
            .asObservableSuccess().subscribeOn(Schedulers.io())
            .map { netResponse ->
                val responseBody = netResponse.body?.string().orEmpty()
                if (responseBody.isEmpty()) {
                    throw Exception("Null Response")
                }
                val response = JsonParser.parseString(responseBody).obj
                val data = response["data"]!!.obj
                val media = data["Media"].obj
                val recommendations = media["recommendations"].obj
                val edges = recommendations["edges"].array
                edges.map {
                    val rec = it["node"]["mediaRecommendation"].obj
                    Log.d("ANILIST RECOMMEND", "${rec["title"].obj["romaji"].string}")
                    SMangaImpl().apply {
                        this.title = rec["title"].obj["romaji"].nullString
                            ?: rec["title"].obj["english"].nullString
                                ?: rec["title"].obj["native"].string
                        this.thumbnail_url = rec["coverImage"].obj["large"].string
                        this.initialized = true
                        this.url = rec["siteUrl"].string
                    }
                }
            }
    }

    override fun requestNext(): Observable<MangasPage> {
        if (smart) {
            val myAnimeListPoints = 0
            val anilistPoints =
                anilistSmart.count { manga.genre!!.toLowerCase(Locale.ROOT).contains(it) }
            val apiPoints = listOf(
                API.MYANIMELIST to myAnimeListPoints,
                API.ANILIST to anilistPoints
            ).sortedWith(
                compareBy(
                    { (_, value) -> value },
                    { (key, _) -> key == preferredApi }
                )
            )
            preferredApi = apiPoints.last().first
            Log.d("SMART RECOMMEND", preferredApi.toString())
        }

        val apiList = API.values().toMutableList()
        apiList.removeAt(apiList.indexOf(preferredApi))
        apiList.add(0, preferredApi)

        var recommendations: Observable<List<SMangaImpl>>? = null
        for (api in apiList) {
            recommendations = when (api) {
                API.MYANIMELIST -> myAnimeList()
                API.ANILIST -> anilist()
            }
                ?: throw Exception("Could not get recommendations")

            val recommendationsBlocking = recommendations.toBlocking().first()
            if (recommendationsBlocking.isNotEmpty()) {
                break
            }
        }

        return recommendations!!.map {
            MangasPage(it, false)
        }.doOnNext {
            onPageReceived(it)
        }
    }

    companion object {
        private const val myAnimeListEndpoint = "https://api.jikan.moe/v3/"
        private const val anilistEndpoint = "https://graphql.anilist.co/"
        private val anilistSmart =
            listOf("manhua", "manhwa", "webtoon", "long strip", "korean", "chinese")

        enum class API { MYANIMELIST, ANILIST }
    }
}
