package eu.kanade.tachiyomi.ui.recent.updates

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.updates.interactor.GetUpdates
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.presentation.updates.UpdatesState
import eu.kanade.presentation.updates.UpdatesStateImpl
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
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
    preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<UpdatesController>(), UpdatesState by state {

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()

    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    val relativeTime: Int by preferences.relativeTime().asState()

    val dateFormat: DateFormat by mutableStateOf(preferences.dateFormat())

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

            getUpdates.subscribe(calendar)
                .distinctUntilChanged()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.InternalError)
                }
                .collectLatest { updates ->
                    state.uiModels = updates.toUpdateUiModels()
                    state.isLoading = false

                    observeDownloads()
                }
        }
    }

    private fun List<UpdatesWithRelations>.toUpdateUiModels(): List<UpdatesUiModel> {
        return this.map { update ->
            val activeDownload = downloadManager.queue.find { update.chapterId == it.chapter.id }
            val downloaded = downloadManager.isChapterDownloaded(
                update.chapterName,
                update.scanlator,
                update.mangaTitle,
                update.sourceId,
            )
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }
            val item = UpdatesItem(
                update = update,
                downloadStateProvider = { downloadState },
                downloadProgressProvider = { activeDownload?.progress ?: 0 },
            )
            UpdatesUiModel.Item(item)
        }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.update?.dateFetch?.toDateKey() ?: Date(0)
                val afterDate = after?.item?.update?.dateFetch?.toDateKey() ?: Date(0)
                when {
                    beforeDate.time != afterDate.time && afterDate.time != 0L ->
                        UpdatesUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
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
        state.uiModels = uiModels.toMutableList().apply {
            val modifiedIndex = uiModels.indexOfFirst {
                it is UpdatesUiModel.Item && it.item.update.chapterId == download.chapter.id
            }
            if (modifiedIndex < 0) return@apply

            var uiModel = removeAt(modifiedIndex)
            if (uiModel is UpdatesUiModel.Item) {
                val item = uiModel.item.copy(
                    downloadStateProvider = { download.status },
                    downloadProgressProvider = { download.progress },
                )
                uiModel = UpdatesUiModel.Item(item)
            }
            add(modifiedIndex, uiModel)
        }
    }

    fun startDownloadingNow(chapterId: Long) {
        downloadManager.startDownloadNow(chapterId)
    }

    fun cancelDownload(chapterId: Long) {
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
                values = updates
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
        launchIO {
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
        launchIO {
            val groupedUpdates = updatesItem.groupBy { it.update.mangaId }.values
            val deletedIds = groupedUpdates.flatMap { updates ->
                val mangaId = updates.first().update.mangaId
                val manga = getManga.await(mangaId) ?: return@flatMap emptyList()
                val source = sourceManager.get(manga.source) ?: return@flatMap emptyList()
                val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId)?.toDbChapter() }
                downloadManager.deleteChapters(chapters, manga, source).mapNotNull { it.id }
            }

            val deletedUpdates = uiModels.filter {
                it is UpdatesUiModel.Item && deletedIds.contains(it.item.update.chapterId)
            }
            if (deletedUpdates.isEmpty()) return@launchIO

            // TODO: Don't do this fake status update
            state.uiModels = uiModels.toMutableList().apply {
                deletedUpdates.forEach { deletedUpdate ->
                    val modifiedIndex = indexOf(deletedUpdate)
                    var uiModel = removeAt(modifiedIndex)
                    if (uiModel is UpdatesUiModel.Item) {
                        val item = uiModel.item.copy(
                            downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                            downloadProgressProvider = { 0 },
                        )
                        uiModel = UpdatesUiModel.Item(item)
                    }
                    add(modifiedIndex, uiModel)
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
        state.uiModels = uiModels.toMutableList().apply {
            val modifiedIndex = indexOfFirst {
                it is UpdatesUiModel.Item && it.item == item
            }
            if (modifiedIndex < 0) return@apply

            val oldItem = (get(modifiedIndex) as? UpdatesUiModel.Item)?.item ?: return@apply
            if ((oldItem.selected && selected) || (!oldItem.selected && !selected)) return@apply

            val firstSelection = none { it is UpdatesUiModel.Item && it.item.selected }
            var newItem = (removeAt(modifiedIndex) as? UpdatesUiModel.Item)?.item?.copy(selected = selected) ?: return@apply
            add(modifiedIndex, UpdatesUiModel.Item(newItem))

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
                        var uiModel = removeAt(it)
                        if (uiModel is UpdatesUiModel.Item) {
                            newItem = uiModel.item.copy(selected = true)
                            uiModel = UpdatesUiModel.Item(newItem)
                        }
                        add(it, uiModel)
                    }
                }
            } else if (userSelected && !fromLongPress) {
                if (!selected) {
                    if (modifiedIndex == selectedPositions[0]) {
                        selectedPositions[0] = indexOfFirst { it is UpdatesUiModel.Item && it.item.selected }
                    } else if (modifiedIndex == selectedPositions[1]) {
                        selectedPositions[1] = indexOfLast { it is UpdatesUiModel.Item && it.item.selected }
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
        state.uiModels = state.uiModels.map {
            when (it) {
                is UpdatesUiModel.Header -> it
                is UpdatesUiModel.Item -> {
                    val newItem = it.item.copy(selected = selected)
                    UpdatesUiModel.Item(newItem)
                }
            }
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        state.uiModels = state.uiModels.map {
            when (it) {
                is UpdatesUiModel.Header -> it
                is UpdatesUiModel.Item -> {
                    val newItem = it.item.let { item -> item.copy(selected = !item.selected) }
                    UpdatesUiModel.Item(newItem)
                }
            }
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
