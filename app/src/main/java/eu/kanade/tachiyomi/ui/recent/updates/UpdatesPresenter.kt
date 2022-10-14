package eu.kanade.tachiyomi.ui.recent.updates

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.updates.interactor.GetUpdates
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.updates.UpdatesState
import eu.kanade.presentation.updates.UpdatesStateImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class UpdatesPresenter(
    private val state: UpdatesStateImpl = UpdatesState() as UpdatesStateImpl,
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    libraryPreferences: LibraryPreferences = Injekt.get(),
) : BasePresenter<UpdatesController>(), UpdatesState by state {

    val isDownloadOnly: Boolean by basePreferences.downloadedOnly().asState()
    val isIncognitoMode: Boolean by basePreferences.incognitoMode().asState()

    val lastUpdated by libraryPreferences.libraryUpdateLastTimestamp().asState()

    val relativeTime: Int by uiPreferences.relativeTime().asState()
    val dateFormat: DateFormat by mutableStateOf(UiPreferences.dateFormat(uiPreferences.dateFormat().get()))

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)

    /**
     * Subscription to observe download status changes.
     */
    private var observeDownloadsStatusJob: Job? = null
    private var observeDownloadsPageJob: Job? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            // Set date limit for recent chapters
            val calendar = Calendar.getInstance().apply {
                time = Date()
                add(Calendar.MONTH, -3)
            }

            observeDownloads()

            getUpdates.subscribe(calendar)
                .distinctUntilChanged()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.InternalError)
                }
                .collectLatest { updates ->
                    state.items = updates.toUpdateItems()
                    state.isLoading = false
                }
        }
    }

    private fun List<UpdatesWithRelations>.toUpdateItems(): List<UpdatesItem> {
        return this
            .distinctBy { it.chapterId }
            .map {
                val activeDownload = downloadManager.queue.find { download -> it.chapterId == download.chapter.id }
                val downloaded = downloadManager.isChapterDownloaded(
                    it.chapterName,
                    it.scanlator,
                    it.mangaTitle,
                    it.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                UpdatesItem(
                    update = it,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress ?: 0 },
                )
            }
    }

    private suspend fun observeDownloads() {
        observeDownloadsStatusJob?.cancel()
        observeDownloadsStatusJob = presenterScope.launchIO {
            downloadManager.queue.getStatusAsFlow()
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        observeDownloadsPageJob?.cancel()
        observeDownloadsPageJob = presenterScope.launchIO {
            downloadManager.queue.getProgressAsFlow()
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    /**
     * Update status of chapters.
     *
     * @param download download object containing progress.
     */
    private fun updateDownloadState(download: Download) {
        state.items = items.toMutableList().apply {
            val modifiedIndex = indexOfFirst {
                it.update.chapterId == download.chapter.id
            }
            if (modifiedIndex < 0) return@apply

            val item = removeAt(modifiedIndex)
                .copy(
                    downloadStateProvider = { download.status },
                    downloadProgressProvider = { download.progress },
                )
            add(modifiedIndex, item)
        }
    }

    fun downloadChapters(items: List<UpdatesItem>, action: ChapterDownloadAction) {
        if (items.isEmpty()) return
        presenterScope.launch {
            when (action) {
                ChapterDownloadAction.START -> {
                    downloadChapters(items)
                    if (items.any { it.downloadStateProvider() == Download.State.ERROR }) {
                        DownloadService.start(view!!.activity!!)
                    }
                }
                ChapterDownloadAction.START_NOW -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    startDownloadingNow(chapterId)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    cancelDownload(chapterId)
                }
                ChapterDownloadAction.DELETE -> {
                    deleteChapters(items)
                }
            }
            toggleAllSelection(false)
        }
    }

    private fun startDownloadingNow(chapterId: Long) {
        downloadManager.startDownloadNow(chapterId)
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.queue.find { chapterId == it.chapter.id } ?: return
        downloadManager.deletePendingDownload(activeDownload)
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    /**
     * Mark the selected updates list as read/unread.
     * @param updates the list of selected updates.
     * @param read whether to mark chapters as read or unread.
     */
    fun markUpdatesRead(updates: List<UpdatesItem>, read: Boolean) {
        presenterScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = updates
                    .mapNotNull { getChapter.await(it.update.chapterId) }
                    .toTypedArray(),
            )
        }
    }

    /**
     * Bookmarks the given list of chapters.
     * @param updates the list of chapters to bookmark.
     */
    fun bookmarkUpdates(updates: List<UpdatesItem>, bookmark: Boolean) {
        presenterScope.launchIO {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { ChapterUpdate(id = it.update.chapterId, bookmark = bookmark) }
                .let { updateChapter.awaitAll(it) }
        }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param updatesItem the list of chapters to download.
     */
    fun downloadChapters(updatesItem: List<UpdatesItem>) {
        presenterScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.mangaId }.values
            for (updates in groupedUpdates) {
                val mangaId = updates.first().update.mangaId
                val manga = getManga.await(mangaId) ?: continue
                // Don't download if source isn't available
                sourceManager.get(manga.source) ?: continue
                val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId)?.toDbChapter() }
                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Delete selected chapters
     *
     * @param updatesItem list of chapters
     */
    fun deleteChapters(updatesItem: List<UpdatesItem>) {
        presenterScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.mangaId }.values
            val deletedIds = groupedUpdates.flatMap { updates ->
                val mangaId = updates.first().update.mangaId
                val manga = getManga.await(mangaId) ?: return@flatMap emptyList()
                val source = sourceManager.get(manga.source) ?: return@flatMap emptyList()
                val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId)?.toDbChapter() }
                downloadManager.deleteChapters(chapters, manga, source).mapNotNull { it.id }
            }

            val deletedUpdates = items.filter {
                deletedIds.contains(it.update.chapterId)
            }
            if (deletedUpdates.isEmpty()) return@launchNonCancellable

            // TODO: Don't do this fake status update
            state.items = state.items.toMutableList().apply {
                deletedUpdates.forEach { deletedUpdate ->
                    val modifiedIndex = indexOf(deletedUpdate)
                    val item = removeAt(modifiedIndex).copy(
                        downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                        downloadProgressProvider = { 0 },
                    )
                    add(modifiedIndex, item)
                }
            }
        }
    }

    fun toggleSelection(
        item: UpdatesItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        state.items = items.toMutableList().apply {
            val modifiedIndex = indexOfFirst { it == item }
            if (modifiedIndex < 0) return@apply

            val oldItem = get(modifiedIndex)
            if (oldItem.selected == selected) return@apply

            val firstSelection = none { it.selected }
            var newItem = removeAt(modifiedIndex).copy(selected = selected)
            add(modifiedIndex, newItem)

            if (selected && userSelected && fromLongPress) {
                if (firstSelection) {
                    selectedPositions[0] = modifiedIndex
                    selectedPositions[1] = modifiedIndex
                } else {
                    // Try to select the items in-between when possible
                    val range: IntRange
                    if (modifiedIndex < selectedPositions[0]) {
                        range = modifiedIndex + 1 until selectedPositions[0]
                        selectedPositions[0] = modifiedIndex
                    } else if (modifiedIndex > selectedPositions[1]) {
                        range = (selectedPositions[1] + 1) until modifiedIndex
                        selectedPositions[1] = modifiedIndex
                    } else {
                        // Just select itself
                        range = IntRange.EMPTY
                    }

                    range.forEach {
                        newItem = removeAt(it).copy(selected = true)
                        add(it, newItem)
                    }
                }
            } else if (userSelected && !fromLongPress) {
                if (!selected) {
                    if (modifiedIndex == selectedPositions[0]) {
                        selectedPositions[0] = indexOfFirst { it.selected }
                    } else if (modifiedIndex == selectedPositions[1]) {
                        selectedPositions[1] = indexOfLast { it.selected }
                    }
                } else {
                    if (modifiedIndex < selectedPositions[0]) {
                        selectedPositions[0] = modifiedIndex
                    } else if (modifiedIndex > selectedPositions[1]) {
                        selectedPositions[1] = modifiedIndex
                    }
                }
            }
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        state.items = items.map {
            it.copy(selected = selected)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        state.items = items.map {
            it.copy(selected = !it.selected)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    sealed class Dialog {
        data class DeleteConfirmation(val toDelete: List<UpdatesItem>) : Dialog()
    }

    sealed class Event {
        object InternalError : Event()
    }
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
)
