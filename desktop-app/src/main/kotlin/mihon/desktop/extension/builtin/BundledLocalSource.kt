package mihon.desktop.extension.builtin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import kotlin.jvm.JvmName

/**
 * Built-in desktop equivalent of Android Mihon's `LocalSource`.
 *
 * Local manga are first-class library rows with [ID] as their source id. The source reads its
 * catalogue from the imported local manga entries stored by the desktop library repository, so no
 * extension package is required.
 */
class BundledLocalSource(
    private val repositoryProvider: () -> LibraryRepository? = { null },
) : WindowsCatalogueSource {

    constructor(libraryRepository: LibraryRepository) : this({ libraryRepository })

    override val id: Long = ID
    override val name: String = NAME
    override val lang: String = LANG
    override val supportsLatest: Boolean = true

    override fun getFilterList(): FilterList = FilterList(OrderByFilter(Filter.Sort.Selection(0, true)))

    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        searchLocalManga(query = "", filters = FilterList(OrderByFilter(Filter.Sort.Selection(0, true))))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
        searchLocalManga(query = "", filters = FilterList(OrderByFilter(Filter.Sort.Selection(1, false))))
    }

    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage =
        withContext(Dispatchers.IO) {
            searchLocalManga(query = query, filters = filters)
        }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        localMangaEntries()
            .firstOrNull { it.url == manga.url || it.title.equals(manga.title, ignoreCase = true) }
            ?.toSManga()
            ?: manga
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val repository = repositoryProvider() ?: return@withContext emptyList()
        val entry = localMangaEntries()
            .firstOrNull { it.url == manga.url || it.title.equals(manga.title, ignoreCase = true) }
            ?: return@withContext emptyList()

        runCatching { repository.chapterSnapshot(entry.id) }.getOrDefault(emptyList()).map { chapter ->
            SChapter(
                url = chapter.url,
                name = chapter.name,
                dateUpload = chapter.dateUpload,
                chapterNumber = chapter.chapterNumber.toFloat(),
                scanlator = chapter.scanlator,
            )
        }
    }

    /**
     * Local page loading is handled by the existing reader through `LocalChapterAsset`; the source
     * level page list is intentionally empty like Android's LocalSource.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    private fun searchLocalManga(query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isEmpty()) {
            localMangaEntries()
        } else {
            localMangaEntries().filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }
        return MangasPage(sortEntries(filtered, filters).map(LocalSourceManga::toSManga), hasNextPage = false)
    }

    private fun sortEntries(entries: List<LocalSourceManga>, filters: FilterList): List<LocalSourceManga> {
        val sortFilter = filters.filterIsInstance<Filter.Sort>().firstOrNull()
        val selection = sortFilter?.state ?: Filter.Sort.Selection(0, true)
        val sorted = when (selection.index) {
            1 -> entries.sortedWith(compareBy<LocalSourceManga> { it.lastModifiedAt })
            else -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
        return if (selection.ascending) sorted else sorted.reversed()
    }

    private fun localMangaEntries(): List<LocalSourceManga> {
        val repository = repositoryProvider() ?: return emptyList()
        val mutationPort = repository as? LibraryMutationPort

        val favoriteEntries = runCatching { repository.librarySnapshot(null) }
            .getOrDefault(emptyList())
            .filter { it.sourceId == ID }
        val allRecords = runCatching { repository.allMangaSnapshot() }
            .getOrDefault(emptyList())
            .filter { it.sourceId == ID }
            .associateBy { it.url }

        val entries = LinkedHashMap<String, LocalSourceManga>()
        favoriteEntries.forEach { manga ->
            val record = allRecords[manga.url]
            entries[manga.url] = manga.toLocalSourceManga(
                mutationPort = mutationPort,
                chapterCount = manga.chapterCount,
                lastModifiedAt = record?.lastModifiedAt ?: 0L,
            )
        }

        // Keep local rows that were removed from the library visible in the source list. They are
        // useful for re-opening/re-importing content, and Android's LocalSource is filesystem based.
        allRecords.values.forEach { record ->
            if (entries.containsKey(record.url)) return@forEach
            entries[record.url] = record.toLocalSourceManga(mutationPort, chapterCount = 0L)
        }

        return entries.values.toList()
    }

    private fun LibraryManga.toLocalSourceManga(
        mutationPort: LibraryMutationPort?,
        chapterCount: Long,
        lastModifiedAt: Long,
    ): LocalSourceManga = LocalSourceManga(
        id = id,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl ?: localStoragePath(mutationPort, url),
        chapterCount = chapterCount,
        lastModifiedAt = lastModifiedAt,
    )

    private fun MangaRecord.toLocalSourceManga(
        mutationPort: LibraryMutationPort?,
        chapterCount: Long,
    ): LocalSourceManga = LocalSourceManga(
        id = id,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl ?: localStoragePath(mutationPort, url),
        chapterCount = chapterCount,
        lastModifiedAt = lastModifiedAt,
    )

    private fun localStoragePath(mutationPort: LibraryMutationPort?, mangaUrl: String): String? {
        val manifestSha256 = mangaUrl.substringAfter("local:", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { mutationPort?.findLocalMangaByManifest(manifestSha256)?.storagePath }.getOrNull()
    }

    private class OrderByFilter(
        state: Filter.Sort.Selection,
    ) : Filter.Sort(
        name = "Order by",
        values = arrayOf("Title", "Date"),
        state = state,
    )

    private data class LocalSourceManga(
        val id: Long,
        val url: String,
        val title: String,
        val thumbnailUrl: String?,
        val chapterCount: Long,
        val lastModifiedAt: Long,
    ) {
        fun toSManga(): SManga = SManga(
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            initialized = true,
        )
    }

    companion object {
        /** Stable source id matching Android Mihon's `LocalSource.ID`. */
        const val ID: Long = mihon.desktop.library.local.LOCAL_SOURCE_ID
        const val LOCAL_SOURCE_ID: Long = ID
        const val NAME: String = "Local source"
        const val LANG: String = "other"
        const val HELP_URL: String = "https://mihon.app/docs/guides/local-source/"
    }
}

/** Stable id of the built-in local source. */
const val LOCAL_SOURCE_ID: Long = BundledLocalSource.ID

/** True when this descriptor describes the built-in local source. */
fun SourceDescriptor.isLocalSource(): Boolean = id == BundledLocalSource.ID

/** Alias for [isLocalSource] for call sites that prefer a property-style check. */
val SourceDescriptor.isLocal: Boolean
    get() = isLocalSource()

/** Android-style `Source.isLocal()` helper for desktop source descriptors. */
@JvmName("isLocalDescriptor")
fun SourceDescriptor.isLocal(): Boolean = isLocalSource()
