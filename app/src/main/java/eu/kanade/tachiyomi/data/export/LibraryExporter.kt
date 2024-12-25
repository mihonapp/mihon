package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga

class LibraryExporter {

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

    private fun generateCsvData(favorites: List<Manga>, options: ExportOptions): String {
        val stringBuilder = StringBuilder()
        favorites.forEach { manga ->
            val row = mutableListOf<String>()
            if (options.includeTitle) row.add(escapeCsvField(manga.title))
            if (options.includeAuthor) row.add(escapeCsvField(manga.author ?: ""))
            if (options.includeArtist) row.add(escapeCsvField(manga.artist ?: ""))
            if (row.isNotEmpty()) {
                stringBuilder.appendLine(row.joinToString(",") { "\"$it\"" })
            }
        }
        return stringBuilder.toString()
    }

    private fun escapeCsvField(field: String): String {
        return field
            .replace("\"", "\"\"")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }
}
