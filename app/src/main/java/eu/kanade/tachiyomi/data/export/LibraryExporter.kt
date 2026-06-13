package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga

object LibraryExporter {

    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
        val categories: List<Category> = emptyList(), // default no categories means all entries
    )
    sealed class ExportResult {
        data object Success: ExportResult()
        data object Failure: ExportResult()
        data object Empty: ExportResult()
    }
    suspend fun exportToCsv(
        context: Context,
        uri: Uri,
        favorites: List<Manga>,
        options: ExportOptions,
        getCategories: GetCategories,
        onExportComplete: () -> Unit,
    ): ExportResult {
        return try {
            withContext(Dispatchers.IO) {
                val filtered = filtering(favorites, options, getCategories)
                if (filtered.isEmpty()){
                    return@withContext ExportResult.Empty
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val csvData = generateCsvData(filtered, options)
                    outputStream.write(csvData.toByteArray())
                } ?: return@withContext ExportResult.Failure
                onExportComplete()
                ExportResult.Success
            }
        } catch (e: Exception) {
            ExportResult.Failure
        }
    }
    suspend fun filtering(favorites: List<Manga>, options: ExportOptions, getCategories: GetCategories) : List<Manga> {
        return if(options.categories.isEmpty()){
            favorites
        } else {
            favorites.filter { manga ->
                getCategories.await(manga.id).any { it in options.categories}
            }
        }
    }
    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private fun generateCsvData(filtered: List<Manga>, options: ExportOptions): String {
        val columnSize = listOf(
            options.includeTitle,
            options.includeAuthor,
            options.includeArtist,
        )
            .count { it }

        val rows = buildList(filtered.size) {
            filtered.forEach { manga ->
                buildList(columnSize) {
                    if (options.includeTitle) add(manga.title)
                    if (options.includeAuthor) add(manga.author)
                    if (options.includeArtist) add(manga.artist)
                }
                    .let(::add)
            }
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
