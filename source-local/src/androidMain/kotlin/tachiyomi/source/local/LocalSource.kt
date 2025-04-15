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
            val filter = filters.extractLocalFilter()
            mangaPages = filter
                .getMangaFunc()
                .asSequence()
                .filter { manga ->
                    if (filter.includedAuthors.isEmpty()) {
                        true
                    } else {
                        manga.author
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.includedAuthors.contains(it) }
                            ?: false
                    }
                }
                .filterNot { manga ->
                    if (filter.excludedAuthors.isEmpty()) {
                        false
                    } else {
                        manga.author
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.excludedAuthors.contains(it) }
                            ?: false
                    }
                }
                .filter { manga ->
                    if (filter.includedArtists.isEmpty()) {
                        true
                    } else {
                        manga.artist
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.includedArtists.contains(it) }
                            ?: false
                    }
                }
                .filterNot { manga ->
                    if (filter.excludedArtists.isEmpty()) {
                        false
                    } else {
                        manga.artist
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.excludedArtists.contains(it) }
                            ?: false
                    }
                }
                .filter { manga ->
                    if (filter.includedGenres.isEmpty()) {
                        true
                    } else {
                        manga.genre
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.includedGenres.contains(it) }
                            ?: false
                    }
                }
                .filterNot { manga ->
                    if (filter.excludedGenres.isEmpty()) {
                        false
                    } else {
                        manga.genre
                            .takeUnless { it.isNullOrBlank() }
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.any { filter.excludedGenres.contains(it) }
                            ?: false
                    }
                }
                .filter { manga ->
                    filter.includedStatuses
                        .takeUnless { it.isEmpty() }
                        ?.contains(manga.status)
                        ?: true
                }
                .filterNot { manga ->
                    filter.excludedStatuses
                        .takeUnless { it.isEmpty() }
                        ?.contains(manga.status)
                        ?: false
                }
                .chunked(CHUNK_SIZE)
                .toList()
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
        val manga = runBlocking { localSourceIndexer.getAllLocalSourceManga.await() }

        val genres = manga
            .mapNotNull { it.genre?.split(",") }
            .flatMap { it.map { genre -> genre.trim() } }
            .toSet()

        val authors = manga
            .mapNotNull { it.author?.split(",") }
            .flatMap { it.map { author -> author.trim() } }
            .toSet()

        val artists = manga
            .mapNotNull { it.artist?.split(",") }
            .flatMap { it.map { artist -> artist.trim() } }
            .toSet()

        val filters = try {
            mutableListOf<Filter<*>>(
                OrderBy.Popular(context),
                Separator(),
                GenreGroup(
                    context = context,
                    genres = genres.map { GenreFilter(it) },
                ),
                AuthorGroup(
                    context = context,
                    authors = authors.map { AuthorFilter(it) },
                ),
                ArtistGroup(
                    context = context,
                    artists = artists.map { ArtistFilter(it) },
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
                GenreTextSearch(context),
                AuthorTextSearch(context),
                ArtistTextSearch(context),
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
