package eu.kanade.tachiyomi.ui.recent.updates

import android.os.Bundle
import androidx.compose.runtime.Immutable
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.updates.interactor.GetUpdates
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.toDateKey
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.asHotFlow
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.Date

class UpdatesPresenter(
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<UpdatesController>() {

    private val _state: MutableStateFlow<UpdatesState> = MutableStateFlow(UpdatesState.Loading)
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private fun updateSuccessState(func: (UpdatesState.Success) -> UpdatesState.Success) {
        _state.update { if (it is UpdatesState.Success) func(it) else it }
    }

    private var incognitoMode = false
        set(value) {
            updateSuccessState { it.copy(isIncognitoMode = value) }
            field = value
        }
    private var downloadOnlyMode = false
        set(value) {
            updateSuccessState { it.copy(isDownloadedOnlyMode = value) }
            field = value
        }

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
                .catch { exception ->
                    _state.value = UpdatesState.Error(exception)
                }
                .collectLatest { updates ->
                    val uiModels = updates.toUpdateUiModels()
                    _state.update { currentState ->
                        when (currentState) {
                            is UpdatesState.Success -> currentState.copy(uiModels)
                            is UpdatesState.Loading, is UpdatesState.Error ->
                                UpdatesState.Success(
                                    uiModels = uiModels,
                                    isIncognitoMode = incognitoMode,
                                    isDownloadedOnlyMode = downloadOnlyMode,
                                )
                        }
                    }

                    observeDownloads()
                }
        }

        preferences.incognitoMode()
            .asHotFlow { incognito ->
                incognitoMode = incognito
            }
            .launchIn(presenterScope)

        preferences.downloadedOnly()
            .asHotFlow { downloadedOnly ->
                downloadOnlyMode = downloadedOnly
            }
            .launchIn(presenterScope)
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
        updateSuccessState { successState ->
            val modifiedIndex = successState.uiModels.indexOfFirst {
                it is UpdatesUiModel.Item && it.item.update.chapterId == download.chapter.id
            }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newUiModels = successState.uiModels.toMutableList().apply {
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
            successState.copy(uiModels = newUiModels)
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
            updateSuccessState { successState ->
                val deletedUpdates = successState.uiModels.filter {
                    it is UpdatesUiModel.Item && deletedIds.contains(it.item.update.chapterId)
                }
                if (deletedUpdates.isEmpty()) return@updateSuccessState successState

                // TODO: Don't do this fake status update
                val newUiModels = successState.uiModels.toMutableList().apply {
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
                successState.copy(uiModels = newUiModels)
            }
        }
    }
}

sealed class UpdatesState {
    object Loading : UpdatesState()
    data class Error(val error: Throwable) : UpdatesState()
    data class Success(
        val uiModels: List<UpdatesUiModel>,
        val isIncognitoMode: Boolean = false,
        val isDownloadedOnlyMode: Boolean = false,
        val showSwipeRefreshIndicator: Boolean = false,
    ) : UpdatesState()
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
)
