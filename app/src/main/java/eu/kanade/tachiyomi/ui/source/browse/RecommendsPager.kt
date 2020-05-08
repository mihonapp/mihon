package eu.kanade.tachiyomi.ui.source.browse

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaImpl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable
import rx.schedulers.Schedulers

open class RecommendsPager(val title: String) : Pager() {
    private val client = OkHttpClient.Builder().build()

    override fun requestNext(): Observable<MangasPage> {
        val query =
            """
            {
                Media(search: "$title", type: MANGA) {
                    title{
                        romaji
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
            .url(apiUrl)
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
                    Log.d("RECOMMEND", "${rec["title"].obj["romaji"].string}")
                    SMangaImpl().apply {
                        this.title = rec["title"].obj["romaji"].string
                        this.thumbnail_url = rec["coverImage"].obj["large"].string
                        this.initialized = true
                        this.url = rec["siteUrl"].string
                    }
                }
            }.map {
                MangasPage(it, false)
            }.doOnNext {
                if (it.mangas.isNotEmpty()) {
                    onPageReceived(it)
                } else {
                    throw NoResultsException()
                }
            }
    }
    companion object {
        const val apiUrl = "https://graphql.anilist.co/"
    }
}
