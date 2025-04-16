package tachiyomi.source.local

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import mihon.domain.manga.local.interactor.GetLocalSourceFilterValues
import mihon.domain.manga.local.interactor.GetLocalSourceMangaByUrl
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.ArtistFilter
import tachiyomi.source.local.filter.ArtistGroup
import tachiyomi.source.local.filter.ArtistTextSearch
import tachiyomi.source.local.filter.AuthorFilter
import tachiyomi.source.local.filter.AuthorGroup
import tachiyomi.source.local.filter.AuthorTextSearch
import tachiyomi.source.local.filter.GenreFilter
import tachiyomi.source.local.filter.GenreGroup
import tachiyomi.source.local.filter.GenreTextSearch
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.filter.Separator
import tachiyomi.source.local.filter.StatusFilter
import tachiyomi.source.local.filter.StatusGroup
import tachiyomi.source.local.filter.TextSearchHeader
import tachiyomi.source.local.filter.extractLocalFilter
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.io.LocalSourceIndexer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) : CatalogueSource, UnmeteredSource {

    private val localSourceIndexer: LocalSourceIndexer = Injekt.get()
    private val getLocalSourceMangaByUrl: GetLocalSourceMangaByUrl = Injekt.get()
    private val getLocalSourceFilterValues: GetLocalSourceFilterValues = Injekt.get()

    @Suppress("PrivatePropertyName")
    private val PopularFilters = FilterList(OrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = FilterList(OrderBy.Latest(context))

    override val name: String = context.stringResource(MR.strings.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    private var mangaPages: List<List<SManga>>? = null

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LatestFilters)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = withIOContext {
        if (page == 1) {
            filters.extractLocalFilter().getFilteredManga()
            val filter = filters.extractLocalFilter()
            mangaPages = filter.getFilteredManga()
                .filterNot { manga ->
                    filter.includedAuthors.any { author ->
                        manga.author?.split(",")?.map { it.trim() }?.contains(author) == false
                    }
                }.filterNot { manga ->
                    filter.includedArtists.any { artist ->
                        manga.artist?.split(",")?.map { it.trim() }?.contains(artist) == false
                    }
                }.filterNot { manga ->
                filter.includedGenres.any { genre ->
                    manga.genre?.split(",")?.map { it.trim() }?.contains(genre) == false
                }
            }.chunked(CHUNK_SIZE)
        }

        if (mangaPages.isNullOrEmpty()) {
            return@withIOContext MangasPage(
                mangas = emptyList<SManga>(),
                hasNextPage = false,
            )
        }

        val hasNextPage = page != mangaPages?.count()
        MangasPage(
            mangas = mangaPages!![page - 1],
            hasNextPage = hasNextPage,
        )
    }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        var dbManga = getLocalSourceMangaByUrl.await(manga.url)
        val mangaDir = fileSystem.getMangaDirectory(manga.url)

        mangaDir?.let { dir ->
            if (dir.lastModified() != dbManga?.dir_last_modified) {
                dbManga = localSourceIndexer.fileToSManga(dir)
                dbManga?.let { localSourceIndexer.insertOrReplaceLocalSourceManga.await(it) }
            }
        }
        dbManga ?: manga
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> = localSourceIndexer.getChapterList(manga)

    // Filters
    override fun getFilterList(): FilterList {
        val filterValues = runBlocking { getLocalSourceFilterValues.await() }

        val filters = try {
            mutableListOf<Filter<*>>(
                OrderBy.Popular(context),
                Separator(),
                AuthorGroup(
                    context = context,
                    authors = filterValues.first.map { AuthorFilter(it) },
                ),
                ArtistGroup(
                    context = context,
                    artists = filterValues.second.map { ArtistFilter(it) },
                ),
                GenreGroup(
                    context = context,
                    genres = filterValues.third.map { GenreFilter(it) },
                ),
                StatusGroup(
                    context,
                    listOf(
                        context.getString(R.string.ongoing),
                        context.getString(R.string.completed),
                        context.getString(R.string.licensed),
                        context.getString(R.string.publishing_finished),
                        context.getString(R.string.cancelled),
                        context.getString(R.string.on_hiatus),
                        context.getString(R.string.unknown),
                    ).map { StatusFilter(it) },
                ),
                Separator(),
                TextSearchHeader(context),
                AuthorTextSearch(context),
                ArtistTextSearch(context),
                GenreTextSearch(context),
                Filter.Separator(),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
        return FilterList(filters)
    }

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException("Unused")

    fun getFormat(chapter: SChapter): Format = localSourceIndexer.getFormat(chapter)

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private const val CHUNK_SIZE = 20
    }
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID
