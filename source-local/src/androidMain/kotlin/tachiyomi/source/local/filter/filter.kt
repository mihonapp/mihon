package tachiyomi.source.local.filter

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import tachiyomi.source.local.R

class GenreFilter(genre: String) : Filter.TriState(genre)
class GenreGroup(context: Context, genres: List<GenreFilter>) : Filter.Group<GenreFilter>(context.getString(R.string.genres), genres)
class GenreTextSearch(context: Context) : Filter.Text(context.getString(R.string.genres))
class AuthorFilter(author: String) : Filter.TriState(author)
class AuthorGroup(context: Context, authors: List<AuthorFilter>) : Filter.Group<AuthorFilter>(context.getString(R.string.authors), authors)
class AuthorTextSearch(context: Context) : Filter.Text(context.getString(R.string.authors))
class ArtistFilter(genre: String) : Filter.TriState(genre)
class ArtistGroup(context: Context, artists: List<ArtistFilter>) : Filter.Group<ArtistFilter>(context.getString(R.string.artists), artists)
class ArtistTextSearch(context: Context) : Filter.Text(context.getString(R.string.artists))
class StatusFilter(name: String) : Filter.TriState(name)
class StatusGroup(context: Context, filters: List<StatusFilter>) : Filter.Group<StatusFilter>(context.getString(R.string.status), filters)
class TextSearchHeader(context: Context) : Filter.Header(context.getString(R.string.local_filter_text_search_header))
class LocalSourceInfoHeader(context: Context) : Filter.Header(context.getString(R.string.local_filter_info_header))
class Separator : Filter.Separator()
