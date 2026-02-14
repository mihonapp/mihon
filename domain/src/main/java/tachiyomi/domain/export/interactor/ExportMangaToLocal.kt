package tachiyomi.domain.export.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.export.service.ExportService
import tachiyomi.domain.manga.model.Manga

class ExportMangaToLocal(
    private val exportService: ExportService,
) {

    suspend fun await(manga: Manga, onProgress: (Float) -> Unit = {}): Result {
        return try {
            val items = exportService.getItemsToExport(manga).getOrThrow()
            val destination = exportService.getDestinationSubfolder(manga).getOrThrow()

            items.forEachIndexed { index, item ->
                val progress = (index.toFloat() / items.size).coerceAtMost(1f)
                onProgress(progress)

                exportService.exportChapter(item, destination).getOrElse {
                    exportService.deleteAllItemsInSubfolder(destination)
                    return Result.Error(it)
                }
            }

            exportService.exportCover(manga, destination)
            exportService.exportComicInfo(manga, destination)

            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to export manga ${manga.title}" }
            exportService.getDestinationSubfolder(manga).getOrNull()?.let {
                exportService.deleteAllItemsInSubfolder(it)
            }
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Throwable) : Result
    }
}
