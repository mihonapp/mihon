package tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.local.interactor.GetFilteredLocalSourceUrls
import mihon.domain.manga.local.interactor.GetLocalSourceMangaOrderedByDateAsc
import mihon.domain.manga.local.interactor.GetLocalSourceMangaOrderedByDateDesc
import mihon.domain.manga.local.interactor.GetLocalSourceMangaOrderedByTitleAsc
import mihon.domain.manga.local.interactor.GetLocalSourceMangaOrderedByTitleDesc
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val getLocalSourceMangaOrderedByTitleAsc: GetLocalSourceMangaOrderedByTitleAsc = Injekt.get()
private val getLocalSourceMangaOrderedByTitleDesc: GetLocalSourceMangaOrderedByTitleDesc = Injekt.get()
private val getLocalSourceMangaOrderedByDateAsc: GetLocalSourceMangaOrderedByDateAsc = Injekt.get()
private val getLocalSourceMangaOrderedByDateDesc: GetLocalSourceMangaOrderedByDateDesc = Injekt.get()
private val getFilteredLocalSourceUrls: GetFilteredLocalSourceUrls = Injekt.get()

data class LocalSourceFilter(
    var orderBy: OrderBy.Popular? = null,
    val includedAuthors: MutableList<String> = mutableListOf(),
    val excludedAuthors: MutableList<String> = mutableListOf(),
    val includedArtists: MutableList<String> = mutableListOf(),
    val excludedArtists: MutableList<String> = mutableListOf(),
    val includedGenres: MutableList<String> = mutableListOf(),
    val excludedGenres: MutableList<String> = mutableListOf(),
    val includedStatuses: MutableList<Long> = mutableListOf(),
    val excludedStatuses: MutableList<Long> = mutableListOf(),
) {

    suspend fun getFilteredManga(): List<SManga> {
        if (orderBy == null) return emptyList()

        val urls = getFilteredLocalSourceUrls.await(
            excludedAuthors = excludedAuthors,
            excludedArtists = excludedArtists,
            excludedGenres = excludedGenres,
            excludedStatuses = excludedStatuses,
            includedAuthors = includedAuthors,
            includedArtists = includedArtists,
            includedGenres = includedGenres,
            includedStatuses = includedStatuses,
        )

        return if (orderBy!!.state?.index == 0) {
            if (orderBy!!.state!!.ascending) {
                getLocalSourceMangaOrderedByTitleAsc.await(urls)
            } else {
                getLocalSourceMangaOrderedByTitleDesc.await(urls)
            }
        } else {
            if (orderBy!!.state!!.ascending) {
                getLocalSourceMangaOrderedByDateAsc.await(urls)
            } else {
                getLocalSourceMangaOrderedByDateDesc.await(urls)
            }
        }
    }
}
