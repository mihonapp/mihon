package tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import mihon.domain.manga.local.interactor.GetAllLocalSourceMangaOrderedByDateAsc
import mihon.domain.manga.local.interactor.GetAllLocalSourceMangaOrderedByDateDesc
import mihon.domain.manga.local.interactor.GetAllLocalSourceMangaOrderedByTitleAsc
import mihon.domain.manga.local.interactor.GetAllLocalSourceMangaOrderedByTitleDesc
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val getAllLocalSourceMangaOrderedByTitleAsc: GetAllLocalSourceMangaOrderedByTitleAsc = Injekt.get()
private val getAllLocalSourceMangaOrderedByTitleDesc: GetAllLocalSourceMangaOrderedByTitleDesc = Injekt.get()
private val getAllLocalSourceMangaOrderedByDateAsc: GetAllLocalSourceMangaOrderedByDateAsc = Injekt.get()
private val getAllLocalSourceMangaOrderedByDateDesc: GetAllLocalSourceMangaOrderedByDateDesc = Injekt.get()

fun FilterList.extractLocalFilter(): LocalSourceFilter {
    val localSourceFilter = LocalSourceFilter()
    this.forEach { filter ->

        when (filter) {
            is OrderBy.Popular -> {
                if (filter.state?.index == 0) {
                    if (filter.state!!.ascending) {
                        localSourceFilter.getMangaFunc = {
                            getAllLocalSourceMangaOrderedByTitleAsc.await()
                        }
                    } else {
                        localSourceFilter.getMangaFunc = {
                            getAllLocalSourceMangaOrderedByTitleDesc.await()
                        }
                    }
                } else {
                    if (filter.state!!.ascending) {
                        localSourceFilter.getMangaFunc = {
                            getAllLocalSourceMangaOrderedByDateAsc.await()
                        }
                    } else {
                        localSourceFilter.getMangaFunc = {
                            getAllLocalSourceMangaOrderedByDateDesc.await()
                        }
                    }
                }
            }

            // included Filter
            is GenreGroup -> {
                filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> {
                            localSourceFilter.includedGenres.add(genre.name.lowercase())
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedGenres.add(genre.name.lowercase())
                        }
                    }
                }
            }

            is GenreTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim().lowercase() }
                    ?.forEach {
                        when (it.first()) {
                            '-' -> localSourceFilter.excludedGenres.add(it.drop(1).trim())
                            else -> localSourceFilter.includedGenres.add(it)
                        }
                    }
            }

            is AuthorGroup -> {
                filter.state.forEach { author ->
                    when (author.state) {
                        Filter.TriState.STATE_INCLUDE -> {
                            localSourceFilter.includedAuthors.add(author.name.lowercase())
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedArtists.add(author.name.lowercase())
                        }
                    }
                }
            }

            is AuthorTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim().lowercase() }
                    ?.forEach {
                        when (it.first()) {
                            '-' -> localSourceFilter.excludedAuthors.add(it.drop(1).trim())
                            else -> localSourceFilter.includedArtists.add(it)
                        }
                    }
            }

            is ArtistGroup -> {
                filter.state.forEach { artist ->
                    when (artist.state) {
                        Filter.TriState.STATE_INCLUDE -> {
                            localSourceFilter.includedArtists.add(artist.name.lowercase())
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedArtists.add(artist.name.lowercase())
                        }
                    }
                }
            }

            is ArtistTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim().lowercase() }
                    ?.forEach {
                        when (it.first()) {
                            '-' -> localSourceFilter.excludedArtists.add(it.drop(1).trim())
                            else -> localSourceFilter.includedAuthors.add(it)
                        }
                    }
            }

            is StatusGroup -> {
                filter.state.forEach { status ->
                    when (status.state) {
                        Filter.TriState.STATE_INCLUDE -> {
                            localSourceFilter.includedStatuses.add(ComicInfoPublishingStatus.toSMangaValue(status.name))
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedStatuses.add(ComicInfoPublishingStatus.toSMangaValue(status.name))
                        }
                    }
                }
            }

            else -> {
            }
        }
    }
    return localSourceFilter
}
