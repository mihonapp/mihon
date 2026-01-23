package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.library.model.LibraryManga

object LibraryExporter {

    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
        val includeUrl: Boolean = false,
        val includeChapterCount: Boolean = false,
        val includeCategory: Boolean = false,
        val includeIsNovel: Boolean = false,
    )

    suspend fun exportToCsv(
        context: Context,
        uri: Uri,
        favorites: List<Manga>,
        options: ExportOptions,
        onExportComplete: () -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val csvData = generateCsvData(favorites, options)
                outputStream.write(csvData.toByteArray())
            }
            onExportComplete()
        }
    }

    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private suspend fun generateCsvData(favorites: List<Manga>, options: ExportOptions): String {
        val sourceManager = Injekt.get<SourceManager>()
        val mangaRepo = Injekt.get<MangaRepository>()
        val getCategories = Injekt.get<tachiyomi.domain.category.interactor.GetCategories>()

        // Pre-fetch library entries and category names
        val libraryMap: Map<Long, tachiyomi.domain.library.model.LibraryManga> = try {
            mangaRepo.getLibraryManga().associateBy { it.manga.id }
        } catch (_: Exception) {
            emptyMap()
        }

        val categoryIdToName: Map<Long, String> = try {
            getCategories.await().associate { it.id to it.name }
        } catch (_: Exception) {
            emptyMap()
        }

        val columns = mutableListOf<String>()
        if (options.includeTitle) columns.add("Title")
        if (options.includeAuthor) columns.add("Author")
        if (options.includeArtist) columns.add("Artist")
        if (options.includeCategory) columns.add("Categories")
        if (options.includeIsNovel) columns.add("Is Novel")
        if (options.includeUrl) columns.add("URL")
        if (options.includeChapterCount) columns.add("Chapter Count")

        val rows = mutableListOf<List<String?>>()
        rows.add(columns)

        favorites.forEach { manga ->
            val row = mutableListOf<String?>()
            if (options.includeTitle) row.add(manga.title)
            if (options.includeAuthor) row.add(manga.author)
            if (options.includeArtist) row.add(manga.artist)

            if (options.includeCategory) {
                val lib = libraryMap[manga.id]
                val catNames = lib?.categories?.mapNotNull { categoryIdToName[it] }?.joinToString("|") ?: ""
                row.add(catNames)
            }

            if (options.includeIsNovel) {
                val isNovel = try {
                    sourceManager.get(manga.source)?.isNovelSource == true
                } catch (_: Exception) {
                    false
                }
                row.add(if (isNovel) "Yes" else "No")
            }

            if (options.includeUrl) {
                val fullUrl = try {
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        val sManga = SManga.create().apply { url = manga.url }
                        source.getMangaUrl(sManga)
                    } else {
                        manga.url
                    }
                } catch (_: Exception) {
                    manga.url
                }
                row.add(fullUrl)
            }

            if (options.includeChapterCount) {
                val count = libraryMap[manga.id]?.totalChapters?.toString() ?: ""
                row.add(count)
            }

            rows.add(row)
        }

        return rows.joinToString("\r\n") { columns ->
            columns.joinToString(",") columns@{ column ->
                if (column.isNullOrBlank()) return@columns ""
                if (escapeRequired.any { column.contains(it) }) {
                    column.replace("\"", "\"\"").let { "\"$it\"" }
                } else {
                    column
                }
            }
        }
    }
}
