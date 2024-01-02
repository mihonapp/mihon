package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.copyFromSChapter
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.data.chapter.ChapterSanitizer
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import java.lang.Long.max
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncChaptersWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterRepository: ChapterRepository,
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter,
    private val updateManga: UpdateManga,
    private val updateChapter: UpdateChapter,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getExcludedScanlators: GetExcludedScanlators,
) {

    /**
     * Method to synchronize db chapters with source ones
     *
     * @param rawSourceChapters the chapters from the source.
     * @param manga the manga the chapters belong to.
     * @param source the source the manga belongs to.
     * @return Newly added chapters
     */
    suspend fun await(
        rawSourceChapters: List<SChapter>,
        manga: Manga,
        source: Source,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Chapter> {
        if (rawSourceChapters.isEmpty() && !source.isLocal()) {
            throw NoChaptersException()
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()

        val sourceChapters = rawSourceChapters
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) { sChapter.name.sanitize(manga.title) })
                    .copy(mangaId = manga.id, sourceOrder = i.toLong())
            }

        val dbChapters = getChaptersByMangaId.await(manga.id)

        val newChapters = mutableListOf<Chapter>()
        val updatedChapters = mutableListOf<Chapter>()
        val removedChapters = dbChapters.filterNot { dbChapter ->
            sourceChapters.any { sourceChapter ->
                dbChapter.url == sourceChapter.url
            }
        }

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate = 0L

        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Update metadata from source if necessary.
            if (source is HttpSource) {
                val sChapter = chapter.toSChapter()
                source.prepareNewChapter(sChapter, manga.toSManga())
                chapter = chapter.copyFromSChapter(sChapter)
            }

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapterNumber)
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChapters.find { it.url == chapter.url }

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                newChapters.add(toAddChapter)
            } else {
                if (shouldUpdateDbChapter.await(dbChapter, chapter)) {
                    val shouldRenameChapter = downloadProvider.isChapterDirNameChanged(dbChapter, chapter) &&
                        downloadManager.isChapterDownloaded(
                            dbChapter.name, dbChapter.scanlator, manga.title, manga.source,
                        )

                    if (shouldRenameChapter) {
                        downloadManager.renameChapter(source, manga, dbChapter, chapter)
                    }
                    var toChangeChapter = dbChapter.copy(
                        name = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sourceOrder = chapter.sourceOrder,
                    )
                    if (chapter.dateUpload != 0L) {
                        toChangeChapter = toChangeChapter.copy(dateUpload = chapter.dateUpload)
                    }
                    updatedChapters.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedChapters.isEmpty() && updatedChapters.isEmpty()) {
            if (manualFetch || manga.fetchInterval == 0 || manga.nextUpdate < fetchWindow.first) {
                updateManga.awaitUpdateFetchInterval(
                    manga,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val reAdded = mutableListOf<Chapter>()

        val deletedChapterNumbers = TreeSet<Double>()
        val deletedReadChapterNumbers = TreeSet<Double>()
        val deletedBookmarkedChapterNumbers = TreeSet<Double>()

        removedChapters.forEach { chapter ->
            if (chapter.read) deletedReadChapterNumbers.add(chapter.chapterNumber)
            if (chapter.bookmark) deletedBookmarkedChapterNumbers.add(chapter.chapterNumber)
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val deletedChapterNumberDateFetchMap = removedChapters.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.
        var itemCount = newChapters.size
        var updatedToAdd = newChapters.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            if (chapter.isRecognizedNumber.not() || chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            chapter = chapter.copy(
                read = chapter.chapterNumber in deletedReadChapterNumbers,
                bookmark = chapter.chapterNumber in deletedBookmarkedChapterNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            reAdded.add(chapter)

            chapter
        }

        if (removedChapters.isNotEmpty()) {
            val toDeleteIds = removedChapters.map { it.id }
            chapterRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = chapterRepository.addAll(updatedToAdd)
        }

        if (updatedChapters.isNotEmpty()) {
            val chapterUpdates = updatedChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }
        updateManga.awaitUpdateFetchInterval(manga, now, fetchWindow)

        // Set this manga as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateManga.awaitUpdateLastUpdate(manga.id)

        val reAddedUrls = reAdded.map { it.url }.toHashSet()

        val excludedScanlators = getExcludedScanlators.await(manga.id).toHashSet()

        return updatedToAdd.filterNot {
            it.url in reAddedUrls || it.scanlator in excludedScanlators
        }
    }
}
