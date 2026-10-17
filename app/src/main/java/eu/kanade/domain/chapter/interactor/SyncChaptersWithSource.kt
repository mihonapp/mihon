package eu.kanade.domain.chapter.interactor

import dev.zacsweers.metro.Inject
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.chapterOrigins
import mihon.domain.source.model.origins
import mihon.domain.source.model.sourceId
import mihon.domain.source.service.MultiSourceChapterMerger
import tachiyomi.data.chapter.ChapterSanitizer
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import java.lang.Long.max
import java.util.TreeSet
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Inject
class SyncChaptersWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterRepository: ChapterRepository,
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter,
    private val updateManga: UpdateManga,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val libraryPreferences: LibraryPreferences,
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
        fetchWindow: ClosedRange<Instant>? = null,
    ): List<Chapter> {
        if (rawSourceChapters.isEmpty() && !source.isLocal()) {
            throw NoChaptersException()
        }

        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val nowInstant = now.toInstant(timeZone)

        val dbChapters = chapterRepository.getChapterByMangaId(manga.id)
        val aggregate = rawSourceChapters.any { it.memo.chapterOrigins().isNotEmpty() }
        val incoming = if (aggregate) {
            // Resolve against fresh progress and the actual queue after source requests finish.
            val queued = downloadManager.queueState.value.filter { it.manga.id == manga.id }
                .associateBy { it.chapter.id }
            val current = dbChapters.map { chapter ->
                queued[chapter.id]?.let { chapter.copy(memo = it.chapter.memo) } ?: chapter
            }
            val protected = current.filter { it.read || it.bookmark || it.lastPageRead > 0 }
                .mapTo(mutableSetOf()) { it.id }
            val previouslyQueuedOrDownloadedIds = downloadManager.getDownloadedChapterIds(current, manga) + queued.keys
            protected += previouslyQueuedOrDownloadedIds
            val bySource = rawSourceChapters.flatMap { chapter ->
                chapter.memo.chapterOrigins().ifEmpty { listOf(ChapterOrigin.from(manga.source, chapter)) }
            }.groupBy { it.sourceId }.map { (sourceId, origins) -> sourceId to origins.map { it.toSChapter() } }
            MultiSourceChapterMerger.merge(manga.source, bySource, current, protected, previouslyQueuedOrDownloadedIds)
        } else {
            rawSourceChapters
        }

        val sourceChapters = incoming
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) { sChapter.name.sanitize(manga.title) })
                    .copy(mangaId = manga.id, sourceOrder = i.toLong())
            }

        val dbChaptersByUrl = dbChapters.associateBy { it.url }
        val sharedUserState = dbChapters.groupBy { MultiSourceChapterMerger.identity(it, manga.source) }

        val newChapters = mutableListOf<Chapter>()
        val updatedChapters = mutableListOf<ChapterRemoteUpdate>()
        val sourceUrls = mutableSetOf<String>()

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate: Instant? = null

        // Update metadata from source if necessary.
        val preparedChapters = if (source is HttpSource) {
            val sManga = manga.toSManga()
            sourceChapters.map { chapter ->
                if (chapter.origins().isNotEmpty()) return@map chapter
                val sChapter = chapter.toSChapter()
                @Suppress("DEPRECATION")
                source.prepareNewChapter(sChapter, sManga)
                chapter.copyFromSChapter(sChapter)
            }
        } else {
            sourceChapters
        }

        // Recognize chapter numbers, from the whole list since names alone don't always tell.
        val recognizedChapters = preparedChapters
            .zip(ChapterRecognition.parseChapterNumbers(manga.title, preparedChapters)) { chapter, numbering ->
                if (chapter.origins().isNotEmpty()) {
                    val equivalents = sharedUserState[
                        MultiSourceChapterMerger.identity(
                            chapter,
                            manga.source,
                        ),
                    ].orEmpty()
                    chapter.copy(read = equivalents.any { it.read }, bookmark = equivalents.any { it.bookmark })
                } else {
                    chapter.copy(chapterNumber = numbering.chapter?.let(ChapterRecognition::toDouble) ?: -1.0)
                }
            }

        for (chapter in recognizedChapters) {
            if (!sourceUrls.add(chapter.url)) continue

            val dbChapter = dbChaptersByUrl[chapter.url]

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == null) {
                    chapter.copy(dateUpload = maxSeenUploadDate ?: nowInstant)
                } else {
                    maxSeenUploadDate = listOfNotNull(maxSeenUploadDate, chapter.dateUpload).maxOrNull()
                    chapter
                }
                newChapters.add(toAddChapter)
            } else {
                if (shouldUpdateDbChapter.await(dbChapter, chapter)) {
                    val shouldRenameChapter = downloadProvider.isChapterDirNameChanged(dbChapter, chapter) &&
                        downloadManager.isChapterDownloaded(
                            dbChapter.name,
                            dbChapter.scanlator,
                            dbChapter.url,
                            manga.title,
                            dbChapter.sourceId(manga.source),
                        )

                    if (shouldRenameChapter) {
                        downloadManager.renameChapter(source, manga, dbChapter, chapter)
                    }

                    updatedChapters.add(
                        ChapterRemoteUpdate(
                            id = dbChapter.id,
                            name = chapter.name,
                            chapterNumber = chapter.chapterNumber,
                            scanlator = chapter.scanlator,
                            sourceOrder = chapter.sourceOrder,
                            dateUpload = chapter.dateUpload,
                            memo = chapter.memo,
                        ),
                    )
                }
            }
        }

        val removedChapters = dbChapters.filterNot { it.url in sourceUrls }

        // The source no longer lists these, so their queued downloads could only fail
        removedChapters.mapNotNull { downloadManager.getQueuedDownloadOrNull(it.id) }
            .takeIf { it.isNotEmpty() }
            ?.let { downloadManager.cancelQueuedDownloads(it) }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedChapters.isEmpty() && updatedChapters.isEmpty()) {
            val isNextUpdateBeforeWindow = fetchWindow != null &&
                manga.nextUpdate.let { it == null || it < fetchWindow.start }
            if (manualFetch || manga.fetchInterval == 0 || isNextUpdateBeforeWindow) {
                updateManga.awaitUpdateFetchInterval(
                    manga,
                    timeZone,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val changedOrDuplicateReadUrls = mutableSetOf<String>()

        val deletedChapterNumbers = TreeSet<Double>()
        val deletedReadChapterNumbers = TreeSet<Double>()
        val deletedBookmarkedChapterNumbers = TreeSet<Double>()

        val readChapterNumbers = dbChapters
            .asSequence()
            .filter { it.read && it.isRecognizedNumber }
            .map { it.chapterNumber }
            .toSet()

        removedChapters.forEach { chapter ->
            if (chapter.read) deletedReadChapterNumbers.add(chapter.chapterNumber)
            if (chapter.bookmark) deletedBookmarkedChapterNumbers.add(chapter.chapterNumber)
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val deletedChapterNumberDateFetchMap = removedChapters.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_NEW)

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.
        var itemCount = newChapters.size
        val toAdd = newChapters.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = nowInstant + (itemCount--).milliseconds)

            if (chapter.chapterNumber in readChapterNumbers && markDuplicateAsRead) {
                changedOrDuplicateReadUrls.add(chapter.url)
                chapter = chapter.copy(read = true)
            }

            if (!chapter.isRecognizedNumber || chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            chapter = chapter.copy(
                read = chapter.chapterNumber in deletedReadChapterNumbers,
                bookmark = chapter.chapterNumber in deletedBookmarkedChapterNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            changedOrDuplicateReadUrls.add(chapter.url)

            chapter
        }

        val added = chapterRepository.updateFromRemote(
            removedIds = removedChapters.map { it.id },
            added = toAdd,
            updated = updatedChapters,
        )

        // Merging existing scanlations can keep one old row and remove another. Preserve their shared flags.
        val sharedFlagUpdates = recognizedChapters.filter { it.origins().isNotEmpty() }.mapNotNull { chapter ->
            val old = dbChaptersByUrl[chapter.url] ?: return@mapNotNull null
            if ((!chapter.read || old.read) && (!chapter.bookmark || old.bookmark)) return@mapNotNull null
            ChapterUpdate(old.id) {
                if (chapter.read) read = true
                if (chapter.bookmark) bookmark = true
            }
        }
        if (sharedFlagUpdates.isNotEmpty()) chapterRepository.updateAll(sharedFlagUpdates)

        val excludedScanlators = getExcludedScanlators.await(manga.id).toHashSet()

        // A first fetch lists the back catalogue, and chapters coming back under a deleted number were out already
        val foundRelease = dbChapters.isNotEmpty() &&
            toAdd.any { chapter ->
                chapter.scanlator !in excludedScanlators &&
                    !(chapter.isRecognizedNumber && chapter.chapterNumber in deletedChapterNumbers)
            }

        // Set this manga as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateManga.awaitUpdateFetchIntervalAndLastUpdate(manga, timeZone, now, fetchWindow, foundRelease)

        return added.filterNot { it.url in changedOrDuplicateReadUrls || it.scanlator in excludedScanlators }
    }
}
