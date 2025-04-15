package tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.local.interactor.GetAllLocalSourceManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val getAllLocalSourceManga: GetAllLocalSourceManga = Injekt.get()

data class LocalSourceFilter(
    var getMangaFunc: (suspend () -> List<SManga>) = { getAllLocalSourceManga.await() },
    val includedAuthors: MutableList<String> = mutableListOf(),
    val excludedAuthors: MutableList<String> = mutableListOf(),
    val includedArtists: MutableList<String> = mutableListOf(),
    val excludedArtists: MutableList<String> = mutableListOf(),
    val includedGenres: MutableList<String> = mutableListOf(),
    val excludedGenres: MutableList<String> = mutableListOf(),
    val includedStatuses: MutableList<Int> = mutableListOf(),
    val excludedStatuses: MutableList<Int> = mutableListOf(),
)
