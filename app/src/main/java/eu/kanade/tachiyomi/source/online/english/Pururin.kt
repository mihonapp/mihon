package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import rx.Observable

class Pururin(delegate: HttpSource) : DelegatedHttpSource(delegate),
        LewdSource<PururinSearchMetadata, Document>, UrlImportableSource {
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang = "en"
    /**
     * The class of the metadata used by this source
     */
    override val metaClass = PururinSearchMetadata::class

    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if(trimmedIdQuery.toIntOrNull() ?: -1 >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else query

        return urlImportFetchSearchManga(newQuery) {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .flatMap {
                    parseToManga(manga, it.asJsoup())
                            .andThen(Observable.just(manga))
                }
    }

    override fun parseIntoMetadata(metadata: PururinSearchMetadata, input: Document) {
        val selfLink = input.select("[itemprop=name]").last().parent()
        val parsedSelfLink = Uri.parse(selfLink.attr("href")).pathSegments

        with(metadata) {
            prId = parsedSelfLink[parsedSelfLink.lastIndex - 1].toIntOrNull()
            prShortLink = parsedSelfLink.last()

            val contentWrapper = input.selectFirst(".content-wrapper")
            title = contentWrapper.selectFirst(".title h1").text()
            altTitle = contentWrapper.selectFirst(".alt-title").text()

            thumbnailUrl = "https:" + input.selectFirst(".cover-wrapper v-lazy-image").attr("src")

            tags.clear()
            contentWrapper.select(".table-gallery-info > tbody > tr").forEach { ele ->
                val key = ele.child(0).text().toLowerCase()
                val value = ele.child(1)
                when(key) {
                    "pages" -> {
                        val split = value.text().split("(").trimAll().dropBlank()

                        pages = split.first().toIntOrNull()
                        fileSize = split.last().removeSuffix(")").trim()
                    }
                    "ratings" -> {
                        ratingCount = value.selectFirst("[itemprop=ratingCount]").attr("content").toIntOrNull()
                        averageRating = value.selectFirst("[itemprop=ratingValue]").attr("content").toDoubleOrNull()
                    }
                    "uploader" -> {
                        uploaderDisp = value.text()
                        uploader = Uri.parse(value.child(0).attr("href")).lastPathSegment
                    }
                    else -> {
                        value.select("a").forEach { link ->
                            val searchUrl = Uri.parse(link.attr("href"))
                            tags += RaisedTag(
                                    searchUrl.pathSegments[searchUrl.pathSegments.lastIndex - 2],
                                    searchUrl.lastPathSegment!!.substringBefore("."),
                                    PururinSearchMetadata.TAG_TYPE_DEFAULT
                            )
                        }
                    }
                }
            }
        }
    }

    override val matchingHosts = listOf(
            "pururin.io",
            "www.pururin.io"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        return "${PururinSearchMetadata.BASE_URL}/gallery/${uri.pathSegments[1]}/${uri.lastPathSegment}"
    }
}