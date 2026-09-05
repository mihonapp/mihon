package mihon.desktop.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

class DesktopLibraryUpdateService(
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort,
    private val processManager: WindowsExtensionProcessManager? = null,
    private val chapterListFetcher: (suspend (sourceId: Long, mangaUrl: String) -> List<SChapter>)? = null,
    private val onProgress: ((checked: Int, total: Int, currentTitle: String) -> Unit)? = null,
    private val onUpdateCompleted: ((LibraryUpdateResult) -> Unit)? = null,
) {
    suspend fun updateLibrary(): LibraryUpdateResult = withContext(Dispatchers.IO) {
        val mangaList = repository.librarySnapshot()
        val errors = mutableListOf<String>()
        val updatedTitles = mutableListOf<String>()
        var newChaptersCount = 0
        var mangaWithNewCount = 0

        for ((index, manga) in mangaList.withIndex()) {
            onProgress?.invoke(index + 1, mangaList.size, manga.title)

            // Local sources (sourceId == 0) can be skipped from remote checks
            if (manga.sourceId == 0L) continue

            try {
                val remoteChapters = fetchChapters(manga.sourceId, manga.url, manga.title)
                val existingChapters = repository.chapterSnapshot(manga.id)
                val existingUrls = existingChapters.map { it.url }.toSet()

                val newRemoteChapters = remoteChapters.filterNot { it.url in existingUrls }
                if (newRemoteChapters.isNotEmpty()) {
                    var addedForThisManga = 0
                    mutationPort.transaction {
                        for (ch in newRemoteChapters) {
                            insertChapter(
                                ChapterRecord(
                                    mangaId = manga.id,
                                    url = ch.url,
                                    name = ch.name,
                                    scanlator = ch.scanlator,
                                    chapterNumber = ch.chapterNumber.toDouble(),
                                    dateUpload = ch.dateUpload,
                                    dateFetch = System.currentTimeMillis(),
                                ),
                            )
                            addedForThisManga++
                        }
                    }
                    if (addedForThisManga > 0) {
                        newChaptersCount += addedForThisManga
                        mangaWithNewCount++
                        updatedTitles.add(manga.title)
                    }
                }
            } catch (e: Exception) {
                errors.add("Failed to update '${manga.title}': ${e.message}")
            }
        }

        val result = LibraryUpdateResult(
            totalMangaChecked = mangaList.size,
            mangaWithNewChapters = mangaWithNewCount,
            newChaptersFound = newChaptersCount,
            updatedMangaTitles = updatedTitles,
            errors = errors,
        )
        onUpdateCompleted?.invoke(result)
        result
    }

    private suspend fun fetchChapters(sourceId: Long, mangaUrl: String, mangaTitle: String): List<SChapter> {
        if (chapterListFetcher != null) {
            return chapterListFetcher.invoke(sourceId, mangaUrl)
        }
        if (processManager != null) {
            val sManga = SManga(url = mangaUrl, title = mangaTitle)
            return processManager.getChapterList(sourceId, sManga)
        }
        return emptyList()
    }
}
