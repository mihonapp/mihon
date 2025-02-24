package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga

object LibraryExporter {

    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
    )

    fun exportToCsv(
        context: Context,
        uri: Uri,
        favorites: List<Manga>,
        options: ExportOptions,
        coroutineScope: CoroutineScope,
        onExportComplete: () -> Unit,
    ) {
        coroutineScope.launch {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val csvData = generateCsvData(favorites, options)
                outputStream.write(csvData.toByteArray())
                outputStream.flush()
                onExportComplete()
            }
        }
    }

    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private fun generateCsvData(favorites: List<Manga>, options: ExportOptions): String {
        val columnSize = listOf(
            options.includeTitle,
            options.includeAuthor,
            options.includeArtist
        ).count { it }

        val rows = buildList(favorites.size) {
            favorites.forEach { manga ->
                add(buildList(columnSize) {
                    if (options.includeTitle) add(manga.title)
                    if (options.includeAuthor) add(manga.author)
                    if (options.includeArtist) add(manga.artist)
                })
            }
        }
        return rows.joinToString("\n") { columns ->
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
