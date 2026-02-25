package tachiyomi.domain.export.interactor

import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.export.service.ExportService
import tachiyomi.domain.manga.model.Manga

class GetExportItems(
    private val exportService: ExportService,
) {

    suspend fun await(manga: Manga): Array<UniFile>? {
        return try {
            val result = exportService.getItemsToExport(manga)
            if (result.isSuccess) {
                result.getOrNull()
            } else {
                logcat(LogPriority.ERROR) { "Failed to get export items for manga ${manga.title}" }
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get export items for manga ${manga.title}" }
            null
        }
    }
} 