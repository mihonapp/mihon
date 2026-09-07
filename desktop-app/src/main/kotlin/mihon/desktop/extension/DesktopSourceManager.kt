package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class DesktopSourceManager(
    private val installer: DesktopExtensionInstaller? = null,
    val processManager: WindowsExtensionProcessManager? = null,
) : Closeable {

    private val builtinSources = ConcurrentHashMap<Long, WindowsCatalogueSource>()

    init {
        // Register default out-of-the-box bundled sources
        val mangadex = BundledMangaDexSource()
        registerBuiltinSource(mangadex)
    }

    fun registerBuiltinSource(source: WindowsCatalogueSource) {
        builtinSources[source.id] = source
    }

    fun unregisterBuiltinSource(sourceId: Long) {
        builtinSources.remove(sourceId)
    }

    fun getSources(): List<SourceDescriptor> {
        val builtins = builtinSources.values.map { source ->
            SourceDescriptor(
                id = source.id,
                name = source.name,
                lang = source.lang,
                className = source::class.java.name,
                supportsLatest = source.supportsLatest,
            )
        }

        val extensionSources = installer?.getInstalledExtensions()
            ?.filter { it.isEnabled }
            ?.flatMap { it.manifest.sources }
            ?: emptyList()

        return (builtins + extensionSources).distinctBy { it.id }
    }

    fun findSourceDescriptor(sourceId: Long): SourceDescriptor? {
        return getSources().find { it.id == sourceId }
    }

    suspend fun getPopular(sourceId: Long, page: Int): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getPopularManga(page)
        }
        val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")
        proc.getPopular(sourceId, page)
    }

    suspend fun getLatest(sourceId: Long, page: Int): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getLatestUpdates(page)
        }
        val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")
        proc.getLatest(sourceId, page)
    }

    fun getFilterList(sourceId: Long): FilterList {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return builtin.getFilterList()
        }
        return FilterList()
    }

    suspend fun searchManga(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList = FilterList(),
    ): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.searchManga(page, query, filters)
        }
        val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")
        proc.searchManga(sourceId, page, query)
    }

    suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getMangaDetails(manga)
        }
        val proc = processManager ?: return@withContext manga
        try {
            proc.getMangaDetails(sourceId, manga)
        } catch (_: Exception) {
            manga
        }
    }

    suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getChapterList(manga)
        }
        val proc = processManager ?: return@withContext emptyList()
        try {
            proc.getChapterList(sourceId, manga)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getPageList(chapter)
        }
        val proc = processManager ?: return@withContext emptyList()
        proc.getPageList(sourceId, chapter)
    }

    override fun close() {
        processManager?.close()
    }
}
