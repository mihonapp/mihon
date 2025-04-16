package tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus

fun FilterList.extractLocalFilter(): LocalSourceFilter {
    val localSourceFilter = LocalSourceFilter()
    this.forEach { filter ->

        when (filter) {
            is OrderBy.Popular -> {
              localSourceFilter.orderBy = filter
            }

            // included Filter
            is GenreGroup -> {
                filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> {
                            localSourceFilter.includedGenres.add(genre.name)
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedGenres.add(genre.name)
                        }
                    }
                }
            }

            is GenreTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim() }
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
                            localSourceFilter.includedAuthors.add(author.name)
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedArtists.add(author.name)
                        }
                    }
                }
            }

            is AuthorTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim() }
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
                            localSourceFilter.includedArtists.add(artist.name)
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedArtists.add(artist.name)
                        }
                    }
                }
            }

            is ArtistTextSearch -> {
                filter.state
                    .takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim() }
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
                            localSourceFilter.includedStatuses.add(ComicInfoPublishingStatus.toSMangaValue(status.name).toLong())
                        }

                        Filter.TriState.STATE_EXCLUDE -> {
                            localSourceFilter.excludedStatuses.add(ComicInfoPublishingStatus.toSMangaValue(status.name).toLong())
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
