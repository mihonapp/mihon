package eu.kanade.tachiyomi.source.online.english

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.metadata.metadata.PururinSearchMetadata
import exh.source.DelegatedHttpSource
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
        // TODO Fetch gallery shortlink
        val newQuery = if(trimmedIdQuery.toIntOrNull() ?: -1 >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else query

        return urlImportFetchSearchManga(newQuery) {
            super.fetchSearchManga(page, query, filters)
        }
    }
}