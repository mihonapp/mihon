package eu.kanade.domain.chapter.interactor

import eu.kanade.data.chapter.NoChaptersException
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.Long.max
import java.util.Date
import java.util.TreeSet

class SyncChaptersWithSource(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
) {

    suspend fun await(
        rawSourceChapters: List<SChapter>,
        manga: Manga,
        source: Source,
    ): Pair<List<Chapter>, List<Chapter>> {
        if (rawSourceChapters.isEmpty() && source.id != LocalSource.ID) {
            throw NoChaptersException()
        }

        val sourceChapters = rawSourceChapters
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(mangaId = manga.id, sourceOrder = i.toLong())
            }

        // Chapters from db.
        val dbChapters = getChapterByMangaId.await(manga.id)

        // Chapters from the source not in db.
        val toAdd = mutableListOf<Chapter>()

        // Chapters whose metadata have changed.
        val toChange = mutableListOf<Chapter>()

        // Chapters from the db not in source.
        val toDelete = dbChapters.filterNot { dbChapter ->
            sourceChapters.any { sourceChapter ->
                dbChapter.url == sourceChapter.url
            }
        }

        val rightNow = Date().time

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate = 0L

        val sManga = manga.toSManga()
        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Update metadata from source if necessary.
            if (source is HttpSource) {
                val sChapter = chapter.toSChapter()
                source.prepareNewChapter(sChapter, sManga)
                chapter = chapter.copyFromSChapter(sChapter)
            }

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapterNumber)
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChapters.find { it.url == chapter.url }

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) rightNow else maxSeenUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                toAdd.add(toAddChapter)
            } else {
                if (shouldUpdateDbChapter.await(dbChapter, chapter)) {
                    if (dbChapter.name != chapter.name && downloadManager.isChapterDownloaded(dbChapter.toDbChapter(), manga.toDbManga())) {
                        downloadManager.renameChapter(source, manga.toDbManga(), dbChapter.toDbChapter(), chapter.toDbChapter())
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
                    toChange.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete or change, avoiding unnecessary db transactions.
        if (toAdd.isEmpty() && toDelete.isEmpty() && toChange.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        val reAdded = mutableListOf<Chapter>()

        val deletedChapterNumbers = TreeSet<Float>()
        val deletedReadChapterNumbers = TreeSet<Float>()

        toDelete.forEach { chapter ->
            if (chapter.read) {
                deletedReadChapterNumbers.add(chapter.chapterNumber)
            }
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val deletedChapterNumberDateFetchMap = toDelete.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.

        var itemCount = toAdd.size
        var updatedToAdd = toAdd.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = rightNow + itemCount--)

            if (chapter.isRecognizedNumber.not() && chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            if (chapter.chapterNumber in deletedReadChapterNumbers) {
                chapter = chapter.copy(read = true)
            }

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            val oldDateFetch = deletedChapterNumberDateFetchMap[chapter.chapterNumber]
            oldDateFetch?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            reAdded.add(chapter)

            chapter
        }

        if (toDelete.isNotEmpty()) {
            val toDeleteIds = toDelete.map { it.id }
            chapterRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = chapterRepository.addAll(updatedToAdd)
        }

        if (toChange.isNotEmpty()) {
            val chapterUpdates = toChange.map { it.toChapterUpdate() }
            chapterRepository.updateAll(chapterUpdates)
        }

        // Set this manga as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateManga.awaitUpdateLastUpdate(manga.id)

        @Suppress("ConvertArgumentToSet") // See tachiyomiorg/tachiyomi#6372.
        return Pair(updatedToAdd.subtract(reAdded).toList(), toDelete.subtract(reAdded).toList())
    }
}
