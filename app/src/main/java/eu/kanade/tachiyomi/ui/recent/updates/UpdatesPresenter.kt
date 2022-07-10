package eu.kanade.tachiyomi.ui.recent.updates

import android.os.Bundle
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.manga.mangaChapterMapper
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.recent.DateSectionItem
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TreeMap

class UpdatesPresenter(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val handler: DatabaseHandler = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
) : BasePresenter<UpdatesController>() {

    private val relativeTime: Int = preferences.relativeTime().get()
    private val dateFormat: DateFormat = preferences.dateFormat()

    private val _updates: MutableStateFlow<List<UpdatesItem>> = MutableStateFlow(listOf())
    val updates: StateFlow<List<UpdatesItem>> = _updates.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            subscribeToUpdates()

            downloadManager.queue.getStatusAsFlow()
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collectLatest {
                    withUIContext {
                        onDownloadStatusChange(it)
                        view?.onChapterDownloadUpdate(it)
                    }
                }

            downloadManager.queue.getProgressAsFlow()
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collectLatest {
                    withUIContext {
                        view?.onChapterDownloadUpdate(it)
                    }
                }
        }
    }

    /**
     * Get observable containing recent chapters and date
     */
    private suspend fun subscribeToUpdates() {
        // Set date limit for recent chapters
        val cal = Calendar.getInstance().apply {
            time = Date()
            add(Calendar.MONTH, -3)
        }

        handler
            .subscribeToList {
                mangasQueries.getRecentlyUpdated(after = cal.timeInMillis, mangaChapterMapper)
            }
            .map { mangaChapter ->
                val map = TreeMap<Date, MutableList<Pair<Manga, Chapter>>> { d1, d2 -> d2.compareTo(d1) }
                val byDate = mangaChapter.groupByTo(map) { it.second.dateFetch.toDateKey() }
                byDate.flatMap { entry ->
                    val dateItem = DateSectionItem(entry.key, relativeTime, dateFormat)
                    entry.value
                        .sortedWith(compareBy({ it.second.dateFetch }, { it.second.chapterNumber })).asReversed()
                        .map { UpdatesItem(it.second, it.first, dateItem) }
                }
            }
            .collectLatest { list ->
                list.forEach { item ->
                    // Find an active download for this chapter.
                    val download = downloadManager.queue.find { it.chapter.id == item.chapter.id }

                    // If there's an active download, assign it, otherwise ask the manager if
                    // the chapter is downloaded and assign it to the status.
                    if (download != null) {
                        item.download = download
                    }
                }
                setDownloadedChapters(list)

                _updates.value = list

                // Set unread chapter count for bottom bar badge
                preferences.unreadUpdatesCount().set(list.count { !it.chapter.read })
            }
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param items the list of chapter from the database.
     */
    private fun setDownloadedChapters(items: List<UpdatesItem>) {
        for (item in items) {
            val manga = item.manga
            val chapter = item.chapter

            if (downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, manga.title, manga.source)) {
                item.status = Download.State.DOWNLOADED
            }
        }
    }

    /**
     * Update status of chapters.
     *
     * @param download download object containing progress.
     */
    private fun onDownloadStatusChange(download: Download) {
        // Assign the download to the model object.
        if (download.status == Download.State.QUEUE) {
            val chapters = (view?.adapter?.currentItems ?: emptyList()).filterIsInstance<UpdatesItem>()
            val chapter = chapters.find { it.chapter.id == download.chapter.id }
            if (chapter != null && chapter.download == null) {
                chapter.download = download
            }
        }
    }

    fun startDownloadingNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter.id)
    }

    /**
     * Mark selected chapter as read
     *
     * @param items list of selected chapters
     * @param read read status
     */
    fun markChapterRead(items: List<UpdatesItem>, read: Boolean) {
        presenterScope.launchIO {
            setReadStatus.await(
                read = read,
                values = items
                    .map { it.chapter }
                    .toTypedArray(),
            )
        }
    }

    /**
     * Delete selected chapters
     *
     * @param chapters list of chapters
     */
    fun deleteChapters(chapters: List<UpdatesItem>) {
        launchIO {
            try {
                deleteChaptersInternal(chapters)
                withUIContext { view?.onChaptersDeleted() }
            } catch (e: Throwable) {
                withUIContext { view?.onChaptersDeletedError(e) }
            }
        }
    }

    /**
     * Mark selected chapters as bookmarked
     * @param items list of selected chapters
     * @param bookmarked bookmark status
     */
    fun bookmarkChapters(items: List<UpdatesItem>, bookmarked: Boolean) {
        presenterScope.launchIO {
            val toUpdate = items.map {
                ChapterUpdate(
                    bookmark = bookmarked,
                    id = it.chapter.id,
                )
            }
            updateChapter.awaitAll(toUpdate)
        }
    }

    /**
     * Download selected chapters
     * @param items list of recent chapters seleted.
     */
    fun downloadChapters(items: List<UpdatesItem>) {
        items.forEach { downloadManager.downloadChapters(it.manga, listOf(it.chapter.toDbChapter())) }
    }

    /**
     * Delete selected chapters
     *
     * @param items chapters selected
     */
    private fun deleteChaptersInternal(chapterItems: List<UpdatesItem>) {
        val itemsByManga = chapterItems.groupBy { it.manga.id }
        for ((_, items) in itemsByManga) {
            val manga = items.first().manga
            val source = sourceManager.get(manga.source) ?: continue
            val chapters = items.map { it.chapter.toDbChapter() }

            downloadManager.deleteChapters(chapters, manga, source)
            items.forEach {
                it.status = Download.State.NOT_DOWNLOADED
                it.download = null
            }
        }
    }
}
