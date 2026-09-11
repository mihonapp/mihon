package mihon.extension.compat

import eu.kanade.tachiyomi.source.CatalogueSource
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsImageSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import eu.kanade.tachiyomi.source.model.Filter as TFilter
import eu.kanade.tachiyomi.source.model.FilterList as TFilterList
import eu.kanade.tachiyomi.source.model.Page as TPage
import eu.kanade.tachiyomi.source.model.SChapter as TSChapter
import eu.kanade.tachiyomi.source.model.SManga as TSManga

class TachiyomiCatalogueSourceAdapter(
    val delegate: CatalogueSource,
) : WindowsImageSource {

    override val id: Long = delegate.id
    override val name: String = delegate.name
    override val lang: String = delegate.lang
    override val supportsLatest: Boolean = delegate.supportsLatest

    override suspend fun getPopularManga(page: Int): MangasPage {
        val tachiPage = delegate.getPopularManga(page)
        return MangasPage(
            mangas = tachiPage.mangas.map { it.toSdkModel() },
            hasNextPage = tachiPage.hasNextPage,
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val tachiPage = delegate.getLatestUpdates(page)
        return MangasPage(
            mangas = tachiPage.mangas.map { it.toSdkModel() },
            hasNextPage = tachiPage.hasNextPage,
        )
    }

    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val tachiFilters = toTachiFilterList(filters)
        val tachiPage = delegate.getSearchManga(page, query, tachiFilters)
        return MangasPage(
            mangas = tachiPage.mangas.map { it.toSdkModel() },
            hasNextPage = tachiPage.hasNextPage,
        )
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val tachiManga = TSManga.create().apply {
            url = manga.url
            title = manga.title
            artist = manga.artist
            author = manga.author
            description = manga.description
            genre = manga.genre.joinToString(", ")
            status = manga.status
            thumbnail_url = manga.thumbnailUrl
            initialized = manga.initialized
        }
        val update = delegate.getMangaUpdate(tachiManga, emptyList(), fetchDetails = true, fetchChapters = false)
        return update.manga.toSdkModel(manga)
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val tachiManga = TSManga.create().apply {
            url = manga.url
            title = manga.title
            thumbnail_url = manga.thumbnailUrl
        }
        val update = delegate.getMangaUpdate(tachiManga, emptyList(), fetchDetails = false, fetchChapters = true)
        return update.chapters.map { it.toSdkModel() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val tachiChapter = TSChapter.create().apply {
            url = chapter.url
            name = chapter.name
            date_upload = chapter.dateUpload
            chapter_number = chapter.chapterNumber
            scanlator = chapter.scanlator
        }
        val tachiPages = delegate.getPageList(tachiChapter)
        return tachiPages.mapIndexed { index, page ->
            Page(
                index = index,
                url = page.url,
                imageUrl = page.imageUrl,
                headers = (delegate as? eu.kanade.tachiyomi.source.online.HttpSource)
                    ?.headers
                    ?.toMultimap()
                    ?.mapValues { (_, values) -> values.joinToString(", ") }
                    .orEmpty(),
            )
        }
    }

    override suspend fun getImage(page: Page): ByteArray {
        val source = delegate as? eu.kanade.tachiyomi.source.online.HttpSource
            ?: error("Source $name does not implement HTTP image loading")
        val tachiPage = TPage(page.index, page.url, page.imageUrl)
        if (tachiPage.imageUrl.isNullOrBlank()) {
            tachiPage.imageUrl = source.getImageUrl(tachiPage)
        }
        require(!tachiPage.imageUrl.isNullOrBlank()) { "Source $name returned an empty image URL" }
        return source.getImage(tachiPage).use { response ->
            val limit = 64 * 1024 * 1024
            response.body.byteStream().use { stream ->
                val bytes = stream.readNBytes(limit + 1)
                require(bytes.size <= limit) { "Source image exceeds 64 MiB" }
                bytes
            }
        }
    }

    override fun getFilterList(): FilterList {
        val tachiFilters = delegate.getFilterList()
        val mapped = tachiFilters.mapNotNull { it.toSdkFilter() }
        return FilterList(mapped)
    }

    private fun TSManga.toSdkModel(fallback: SManga? = null): SManga {
        val mappedUrl = runCatching { url }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback?.url.orEmpty()
        val mappedTitle = runCatching { title }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback?.title.orEmpty()
        return SManga(
            url = mappedUrl,
            title = mappedTitle,
            artist = artist ?: fallback?.artist,
            author = author ?: fallback?.author,
            description = description ?: fallback?.description,
            genre = getGenres() ?: fallback?.genre ?: emptyList(),
            status = status.takeUnless { it == TSManga.UNKNOWN } ?: fallback?.status ?: TSManga.UNKNOWN,
            thumbnailUrl = thumbnail_url ?: fallback?.thumbnailUrl,
            initialized = initialized || fallback?.initialized == true,
        )
    }

    private fun TSChapter.toSdkModel(): SChapter {
        return SChapter(
            url = url,
            name = name,
            dateUpload = date_upload,
            chapterNumber = chapter_number,
            scanlator = scanlator,
        )
    }

    private fun toTachiFilterList(filters: FilterList): TFilterList {
        val sourceFilters = delegate.getFilterList()
        val stateByName = filters.filters.associate { it.name to it.state }
        for (sf in sourceFilters) {
            val state = stateByName[sf.name] ?: continue
            try {
                when (sf) {
                    is TFilter.Text -> if (state is String) sf.state = state
                    is TFilter.CheckBox -> if (state is Boolean) sf.state = state
                    is TFilter.Select<*> -> if (state is Int) sf.state = state
                    is TFilter.TriState -> if (state is Int) sf.state = state
                    is TFilter.Sort -> if (state is Filter.Sort.Selection) {
                        sf.state = TFilter.Sort.Selection(state.index, state.ascending)
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        return sourceFilters
    }

    @Suppress("UNCHECKED_CAST")
    private fun TFilter<*>.toSdkFilter(): Filter<*>? {
        return when (this) {
            is TFilter.Header -> Filter.Header(name)
            is TFilter.Separator -> Filter.Separator(name)
            is TFilter.Text -> Filter.Text(name, state)
            is TFilter.CheckBox -> Filter.CheckBox(name, state)
            is TFilter.Select<*> -> Filter.Select(name, values as Array<Any>, state)
            is TFilter.TriState -> Filter.TriState(name, state)
            is TFilter.Sort -> {
                val cur = state
                val sel = if (cur != null) Filter.Sort.Selection(cur.index, cur.ascending) else null
                Filter.Sort(name, values, sel)
            }
            else -> null
        }
    }
}
