package tachiyomi.domain.export.service

import com.hippo.unifile.UniFile
import tachiyomi.domain.manga.model.Manga

interface ExportService {

    /**
     * Exports a chapter file to the specified destination
     */
    suspend fun exportChapter(file: UniFile, destinationSubfolder: UniFile): Result<Unit>

    /**
     * Exports the cover image for a manga
     */
    suspend fun exportCover(manga: Manga, destinationSubfolder: UniFile): Result<Unit>

    /**
     * Exports ComicInfo metadata for a manga
     */
    suspend fun exportComicInfo(manga: Manga, destinationSubfolder: UniFile): Result<Unit>

    /**
     * Gets all items that can be exported for a manga
     */
    suspend fun getItemsToExport(manga: Manga): Result<Array<UniFile>>

    /**
     * Gets or creates the destination subfolder for a manga
     */
    suspend fun getDestinationSubfolder(manga: Manga): Result<UniFile>

    /**
     * Checks if a destination subfolder already exists
     */
    suspend fun destinationSubfolderExists(mangaTitle: String): Result<Boolean>

    /**
     * Deletes all items in a subfolder
     */
    suspend fun deleteAllItemsInSubfolder(subfolder: UniFile): Result<Unit>
} 