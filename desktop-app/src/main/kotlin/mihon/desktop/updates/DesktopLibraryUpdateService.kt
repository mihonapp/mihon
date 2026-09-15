package mihon.desktop.updates

import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.library.update.LibraryUpdateOptions
import mihon.desktop.library.update.LibraryUpdateService
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

/** Compatibility adapter for older callers. Scheduling and update behavior live in library/update. */
class DesktopLibraryUpdateService(
    repository: LibraryRepository,
    mutationPort: LibraryMutationPort,
    processManager: WindowsExtensionProcessManager? = null,
    chapterListFetcher: (suspend (sourceId: Long, mangaUrl: String) -> List<SChapter>)? = null,
    private val onProgress: ((checked: Int, total: Int, currentTitle: String) -> Unit)? = null,
    private val onUpdateCompleted: ((LibraryUpdateResult) -> Unit)? = null,
) {
    private val delegate = LibraryUpdateService(
        repository = repository,
        refreshManga = { manga ->
            val chapters = when {
                chapterListFetcher != null -> chapterListFetcher(manga.sourceId, manga.url)
                processManager != null -> processManager.getChapterList(
                    manga.sourceId,
                    SManga(url = manga.url, title = manga.title),
                )
                else -> error("No source available for '${manga.title}'")
            }
            val existing = repository.chapterSnapshot(manga.id).map { it.url }.toSet()
            mutationPort.transaction {
                chapters.distinctBy { it.url }.filterNot { it.url in existing }.forEach { chapter ->
                    insertChapter(
                        ChapterRecord(
                            mangaId = manga.id,
                            url = chapter.url,
                            name = chapter.name,
                            scanlator = chapter.scanlator,
                            chapterNumber = chapter.chapterNumber.toDouble(),
                            dateUpload = chapter.dateUpload,
                            dateFetch = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        },
    )

    suspend fun updateLibrary(): LibraryUpdateResult {
        val report = delegate.updateLibrary(
            options = LibraryUpdateOptions(skipCompleted = false),
            onProgress = { onProgress?.invoke(it.currentIndex, it.totalManga, it.currentMangaTitle) },
        )
        return LibraryUpdateResult(
            totalMangaChecked = report.totalMangaChecked,
            mangaWithNewChapters = report.updatedMangaCount,
            newChaptersFound = report.newChaptersTotal,
            updatedMangaTitles = report.results.filter { it.newChapters.isNotEmpty() }.map { it.title },
            errors = report.errors,
        ).also { onUpdateCompleted?.invoke(it) }
    }
}
