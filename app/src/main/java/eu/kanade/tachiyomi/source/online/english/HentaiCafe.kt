package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.HentaiCafeSearchMetadata
import exh.metadata.metadata.HentaiCafeSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchManga
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import rx.Observable

class HentaiCafe(delegate: HttpSource) : DelegatedHttpSource(delegate),
        LewdSource<HentaiCafeSearchMetadata, Document>, UrlImportableSource {
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang = "en"
    /**
     * The class of the metadata used by this source
     */
    override val metaClass = HentaiCafeSearchMetadata::class

    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query) {
                super.fetchSearchManga(page, query, filters)
            }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .flatMap {
                    parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga.apply {
                        initialized = true
                    }))
                }
    }

    /**
     * Parse the supplied input into the supplied metadata object
     */
    override fun parseIntoMetadata(metadata: HentaiCafeSearchMetadata, input: Document) {
        with(metadata) {
            url = input.location()
            title = input.select(".entry-title").text()
            val contentElement = input.select(".entry-content").first()
            thumbnailUrl = contentElement.child(0).child(0).attr("src")

            fun filterableTagsOfType(type: String) = contentElement.select("a")
                    .filter { "$baseUrl/$type/" in it.attr("href") }
                    .map { it.text() }

            tags.clear()
            tags += filterableTagsOfType("tag").map {
                RaisedTag(null, it, TAG_TYPE_DEFAULT)
            }

            val artists = filterableTagsOfType("artist")

            artist = artists.joinToString()
            tags += artists.map {
                RaisedTag("artist", it, TAG_TYPE_VIRTUAL)
            }

            readerId = HttpUrl.parse(input.select("[title=Read]").attr("href"))!!.pathSegments()[2]
        }
    }

    override fun fetchChapterList(manga: SManga) = getOrLoadMetadata(manga.id) {
        client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .map { it.asJsoup() }
                .toSingle()
    }.map {
        listOf(
                SChapter.create().apply {
                    setUrlWithoutDomain("/manga/read/${it.readerId}/en/0/1/")
                    name = "Chapter"
                    chapter_number = 0.0f
                }
        )
    }.toObservable()

    override val matchingHosts = listOf(
            "hentai.cafe"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if(lcFirstPathSegment == "manga")
            "https://hentai.cafe/${uri.pathSegments[2]}"
        else
            "https://hentai.cafe/$lcFirstPathSegment"
    }
}
