package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

class OnlineMangaSyncService(
    private val libraryRepository: SqlDelightLibraryRepository,
    private val processManager: WindowsExtensionProcessManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks if an online manga is in the library.
     */
    fun isMangaInLibrary(sourceId: Long, mangaUrl: String): Boolean {
        val record = libraryRepository.findManga(sourceId, mangaUrl)
        return record?.favorite == true
    }

    /**
     * Atomically adds or updates an online manga into the local library,
     * fetches details and chapters from the extension process, and persists them.
     */
    suspend fun addOrUpdateOnlineManga(
        sourceId: Long,
        manga: SManga,
        forceRefresh: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        // Fetch detailed manga information from extension host if not initialized or forced
        val detailedManga = if (!manga.initialized || forceRefresh) {
            try {
                processManager.getMangaDetails(sourceId, manga)
            } catch (_: Exception) {
                manga
            }
        } else {
            manga
        }

        // Fetch chapter list from extension host
        val chapters = try {
            processManager.getChapterList(sourceId, detailedManga)
        } catch (_: Exception) {
            emptyList()
        }

        // Persist to database in a single transaction
        libraryRepository.transaction {
            val now = System.currentTimeMillis()
            val existingManga = libraryRepository.findManga(sourceId, detailedManga.url)
            val mangaId = if (existingManga == null) {
                libraryRepository.insertManga(
                    MangaRecord(
                        id = 0L,
                        sourceId = sourceId,
                        url = detailedManga.url,
                        title = detailedManga.title,
                        artist = detailedManga.artist,
                        author = detailedManga.author,
                        description = detailedManga.description,
                        genreJson = json.encodeToString(detailedManga.genre),
                        status = detailedManga.status.toLong(),
                        thumbnailUrl = detailedManga.thumbnailUrl,
                        favorite = true,
                        dateAdded = now,
                        lastModifiedAt = now,
                        favoriteModifiedAt = now,
                        initialized = true,
                    ),
                )
            } else {
                libraryRepository.updateManga(
                    existingManga.copy(
                        title = detailedManga.title,
                        artist = detailedManga.artist ?: existingManga.artist,
                        author = detailedManga.author ?: existingManga.author,
                        description = detailedManga.description ?: existingManga.description,
                        genreJson = if (detailedManga.genre.isNotEmpty()) json.encodeToString(detailedManga.genre) else existingManga.genreJson,
                        status = if (detailedManga.status != SManga.UNKNOWN) detailedManga.status.toLong() else existingManga.status,
                        thumbnailUrl = detailedManga.thumbnailUrl ?: existingManga.thumbnailUrl,
                        favorite = true,
                        lastModifiedAt = now,
                        favoriteModifiedAt = if (!existingManga.favorite) now else existingManga.favoriteModifiedAt,
                        initialized = true,
                    ),
                )
                existingManga.id
            }

            // Sync chapters
            chapters.forEachIndexed { index, sChapter ->
                val existingChapter = libraryRepository.findChapter(mangaId, sChapter.url)
                if (existingChapter == null) {
                    libraryRepository.insertChapter(
                        ChapterRecord(
                            id = 0L,
                            mangaId = mangaId,
                            url = sChapter.url,
                            name = sChapter.name,
                            scanlator = sChapter.scanlator,
                            read = false,
                            bookmark = false,
                            lastPageRead = 0L,
                            dateFetch = now,
                            dateUpload = if (sChapter.dateUpload > 0L) sChapter.dateUpload else now,
                            chapterNumber = sChapter.chapterNumber.toDouble(),
                            sourceOrder = index.toLong(),
                            lastModifiedAt = now,
                        ),
                    )
                } else {
                    libraryRepository.updateChapter(
                        existingChapter.copy(
                            name = sChapter.name,
                            scanlator = sChapter.scanlator ?: existingChapter.scanlator,
                            dateUpload = if (sChapter.dateUpload > 0L) sChapter.dateUpload else existingChapter.dateUpload,
                            chapterNumber = if (sChapter.chapterNumber >= 0f) sChapter.chapterNumber.toDouble() else existingChapter.chapterNumber,
                            sourceOrder = index.toLong(),
                            lastModifiedAt = now,
                        ),
                    )
                }
            }

            mangaId
        }
    }
}