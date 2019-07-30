package eu.kanade.tachiyomi.source.online.all

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lvla.rxjava.interopkt.toV1Observable
import com.lvla.rxjava.interopkt.toV1Single
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.MERGED_SOURCE_ID
import exh.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.asSingle
import okhttp3.Response
import rx.Observable
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

// TODO LocalSource compatibility
// TODO Disable clear database option
class MergedSource : HttpSource() {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val gson: Gson by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return readMangaConfig(manga).load(db, sourceManager).take(1).map { loaded ->
            SManga.create().apply {
                this.copyFrom(loaded.manga)
                url = manga.url
            }
        }.asFlowable().toV1Observable()
    }
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return GlobalScope.async(Dispatchers.IO) {
            val loadedMangas = readMangaConfig(manga).load(db, sourceManager).buffer()
            loadedMangas.map { loadedManga ->
                async(Dispatchers.IO) {
                    loadedManga.source.fetchChapterList(loadedManga.manga).map { chapterList ->
                        chapterList.map { chapter ->
                            chapter.apply {
                                url = writeUrlConfig(UrlConfig(loadedManga.source.id, url, loadedManga.manga.url))
                            }
                        }
                    }.toSingle().await(Schedulers.io())
                }
            }.buffer().map { it.await() }.toList().flatten()
        }.asSingle(Dispatchers.IO).toV1Single().toObservable()
    }
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val config = readUrlConfig(chapter.url)
        val source = sourceManager.getOrStub(config.source)
        return source.fetchPageList(SChapter.create().apply {
            copyFrom(chapter)
            url = config.url
        }).map { pages ->
            pages.map { page ->
                page.copyWithUrl(writeUrlConfig(UrlConfig(config.source, page.url, config.mangaUrl)))
            }
        }
    }
    override fun fetchImageUrl(page: Page): Observable<String> {
        val config = readUrlConfig(page.url)
        val source = sourceManager.getOrStub(config.source) as? HttpSource
                ?: throw UnsupportedOperationException("This source does not support this operation!")
        return source.fetchImageUrl(page.copyWithUrl(config.url))
    }
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchImage(page: Page): Observable<Response> {
        val config = readUrlConfig(page.url)
        val source = sourceManager.getOrStub(config.source) as? HttpSource
                ?: throw UnsupportedOperationException("This source does not support this operation!")
        return source.fetchImage(page.copyWithUrl(config.url))
    }
    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val chapterConfig = readUrlConfig(chapter.url)
        val source = sourceManager.getOrStub(chapterConfig.source) as? HttpSource
                ?: throw UnsupportedOperationException("This source does not support this operation!")
        val copiedManga = SManga.create().apply {
            this.copyFrom(manga)
            url = chapterConfig.mangaUrl
        }
        chapter.url = chapterConfig.url
        source.prepareNewChapter(chapter, copiedManga)
        chapter.url = writeUrlConfig(UrlConfig(source.id, chapter.url, chapterConfig.mangaUrl))
        chapter.scanlator = if(chapter.scanlator.isNullOrBlank()) source.name
        else "$source: ${chapter.scanlator}"
    }

    fun readMangaConfig(manga: SManga): MangaConfig {
        return MangaConfig.readFromUrl(gson, manga.url)
    }
    fun readUrlConfig(url: String): UrlConfig {
        return gson.fromJson(url)
    }
    fun writeUrlConfig(urlConfig: UrlConfig): String {
        return gson.toJson(urlConfig)
    }
    data class LoadedMangaSource(val source: Source, val manga: Manga)
    data class MangaSource(
            @SerializedName("s")
            val source: Long,
            @SerializedName("u")
            val url: String
    ) {
        suspend fun load(db: DatabaseHelper, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = db.getManga(url, source).await() ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }
    data class MangaConfig(
            @SerializedName("c")
            val children: List<MangaSource>
    ) {
        fun load(db: DatabaseHelper, sourceManager: SourceManager): Flow<LoadedMangaSource> {
            return children.asFlow().map { mangaSource ->
                mangaSource.load(db, sourceManager)
                        ?: throw IllegalStateException("Missing source manga: $mangaSource")
            }
        }

        fun writeAsUrl(gson: Gson): String {
            return gson.toJson(this)
        }

        companion object {
            fun readFromUrl(gson: Gson, url: String): MangaConfig {
                return gson.fromJson(url)
            }
        }
    }
    data class UrlConfig(
            @SerializedName("s")
            val source: Long,
            @SerializedName("u")
            val url: String,
            @SerializedName("m")
            val mangaUrl: String
    )

    fun Page.copyWithUrl(newUrl: String) = Page(
            index,
            newUrl,
            imageUrl,
            uri
    )

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}