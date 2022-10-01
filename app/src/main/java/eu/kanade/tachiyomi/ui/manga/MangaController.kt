package eu.kanade.tachiyomi.ui.manga

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.data.chapter.NoChaptersException
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.DownloadCustomAmountDialog
import eu.kanade.presentation.util.calculateWindowWidthSizeClass
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.search.SearchController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaPresenter.Dialog
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersSettingsSheet
import eu.kanade.tachiyomi.ui.manga.info.MangaFullCoverDialog
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackSearchDialog
import eu.kanade.tachiyomi.ui.manga.track.TrackSheet
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recent.history.HistoryController
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import eu.kanade.domain.chapter.model.Chapter as DomainChapter

class MangaController : FullComposeController<MangaPresenter> {

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    constructor(
        mangaId: Long,
        fromSource: Boolean = false,
    ) : super(bundleOf(MANGA_EXTRA to mangaId, FROM_SOURCE_EXTRA to fromSource)) {
        this.mangaId = mangaId
    }

    var mangaId: Long

    val fromSource: Boolean
        get() = presenter.isFromSource

    // Sheet containing filter/sort/display items.
    private lateinit var settingsSheet: ChaptersSettingsSheet

    private lateinit var trackSheet: TrackSheet

    private val snackbarHostState = SnackbarHostState()

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        if (type.isEnter) {
            actionBar?.hide()
        } else {
            actionBar?.show()
        }
    }

    override fun createPresenter(): MangaPresenter {
        return MangaPresenter(
            mangaId = mangaId,
            isFromSource = args.getBoolean(FROM_SOURCE_EXTRA, false),
        )
    }

    @Composable
    override fun ComposeContent() {
        val state by presenter.state.collectAsState()

        if (state is MangaScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenState.Success
        val isHttpSource = remember { successState.source is HttpSource }
        val scope = rememberCoroutineScope()

        MangaScreen(
            state = successState,
            snackbarHostState = snackbarHostState,
            windowWidthSizeClass = calculateWindowWidthSizeClass(),
            onBackClicked = router::popCurrentController,
            onChapterClicked = this::openChapter,
            onDownloadChapter = this::onDownloadChapters.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = this::onFavoriteClick,
            onWebViewClicked = this::openMangaInWebView.takeIf { isHttpSource },
            onTrackingClicked = trackSheet::show.takeIf { successState.trackingAvailable },
            onTagClicked = this::performGenreSearch,
            onFilterButtonClicked = settingsSheet::show,
            onRefresh = presenter::fetchAllFromSource,
            onContinueReading = this::continueReading,
            onSearch = this::performSearch,
            onCoverClicked = this::openCoverDialog,
            onShareClicked = this::shareManga.takeIf { isHttpSource },
            onDownloadActionClicked = this::runDownloadChapterAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = presenter::promptChangeCategories.takeIf { successState.manga.favorite },
            onMigrateClicked = this::migrateManga.takeIf { successState.manga.favorite },
            onMultiBookmarkClicked = presenter::bookmarkChapters,
            onMultiMarkAsReadClicked = presenter::markChaptersRead,
            onMarkPreviousAsReadClicked = presenter::markPreviousChapterRead,
            onMultiDeleteClicked = presenter::showDeleteChapterDialog,
            onChapterSelected = presenter::toggleSelection,
            onAllChapterSelected = presenter::toggleAllSelection,
            onInvertSelection = presenter::invertSelection,
        )

        val onDismissRequest = { presenter.dismissDialog() }
        when (val dialog = (state as? MangaScreenState.Success)?.dialog) {
            is Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        router.pushController(CategoryController())
                    },
                    onConfirm = { include, _ ->
                        presenter.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.toggleAllSelection(false)
                        deleteChapters(dialog.chapters)
                    },
                )
            }
            is Dialog.DownloadCustomAmount -> {
                DownloadCustomAmountDialog(
                    maxAmount = dialog.max,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { amount ->
                        val chaptersToDownload = presenter.getUnreadChaptersSorted().take(amount)
                        if (chaptersToDownload.isNotEmpty()) {
                            scope.launch { downloadChapters(chaptersToDownload) }
                        }
                    },
                )
            }
            is Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.toggleFavorite(
                            onRemoved = {},
                            onAdded = {},
                            checkDuplicate = false,
                        )
                    },
                    onOpenManga = { router.pushController(MangaController(dialog.duplicate.id)) },
                    duplicateFrom = presenter.getSourceOrStub(dialog.duplicate),
                )
            }
            null -> {}
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedViewState: Bundle?): View {
        settingsSheet = ChaptersSettingsSheet(router, presenter)
        trackSheet = TrackSheet(this, (activity as MainActivity).supportFragmentManager)
        return super.onCreateView(inflater, container, savedViewState)
    }

    // Manga info - start

    fun onFetchMangaInfoError(error: Throwable) {
        // Ignore early hints "errors" that aren't handled by OkHttp
        if (error is HttpException && error.code == 103) {
            return
        }
        activity?.toast(error.message)
    }

    private fun openMangaInWebView() {
        val manga = presenter.manga ?: return
        val source = presenter.source as? HttpSource ?: return

        val url = try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            return
        }

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity, url, source.id, manga.title)
        startActivity(intent)
    }

    private fun shareManga() {
        val context = view?.context ?: return
        val manga = presenter.manga ?: return
        val source = presenter.source as? HttpSource ?: return
        try {
            val url = source.getMangaUrl(manga.toSManga())
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    private fun onFavoriteClick() {
        presenter.toggleFavorite(
            onRemoved = this::onFavoriteRemoved,
            onAdded = { activity?.toast(R.string.manga_added_library) },
        )
    }

    private fun onFavoriteRemoved() {
        val context = activity ?: return
        context.toast(R.string.manga_removed_library)
        viewScope.launch {
            if (!presenter.hasDownloads()) return@launch
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.delete_downloads_for_manga),
                actionLabel = context.getString(R.string.action_delete),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                presenter.deleteDownloads()
            }
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private fun performSearch(query: String, global: Boolean) {
        if (global) {
            router.pushController(GlobalSearchController(query))
            return
        }

        if (router.backstackSize < 2) {
            return
        }

        when (val previousController = router.backstack[router.backstackSize - 2].controller) {
            is LibraryController -> {
                router.handleBack()
                previousController.search(query)
            }
            is UpdatesController,
            is HistoryController,
            -> {
                // Manually navigate to LibraryController
                router.handleBack()
                (router.activity as MainActivity).setSelectedNavItem(R.id.nav_library)
                val controller = router.getControllerWithTag(R.id.nav_library.toString()) as LibraryController
                controller.search(query)
            }
            is BrowseSourceController -> {
                router.handleBack()
                previousController.searchWithQuery(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private fun performGenreSearch(genreName: String) {
        if (router.backstackSize < 2) {
            return
        }

        val previousController = router.backstack[router.backstackSize - 2].controller
        val presenterSource = presenter.source

        if (previousController is BrowseSourceController &&
            presenterSource is HttpSource
        ) {
            router.handleBack()
            previousController.searchWithGenre(genreName)
        } else {
            performSearch(genreName, global = false)
        }
    }

    private fun openCoverDialog() {
        val mangaId = presenter.manga?.id ?: return
        router.pushController(MangaFullCoverDialog(mangaId).withFadeTransaction())
    }

    /**
     * Initiates source migration for the specific manga.
     */
    private fun migrateManga() {
        val manga = presenter.manga ?: return
        val controller = SearchController(manga)
        controller.targetController = this
        router.pushController(controller)
    }

    // Manga info - end

    // Chapters list - start

    private fun continueReading() {
        val chapter = presenter.getNextUnreadChapter()
        if (chapter != null) openChapter(chapter)
    }

    private fun openChapter(chapter: DomainChapter) {
        activity?.run {
            startActivity(ReaderActivity.newIntent(this, chapter.mangaId, chapter.id))
        }
    }

    fun onFetchChaptersError(error: Throwable) {
        if (error is NoChaptersException) {
            activity?.toast(R.string.no_chapters_error)
        } else {
            activity?.toast(error.message)
        }
    }

    // SELECTION MODE ACTIONS

    private fun onDownloadChapters(
        items: List<ChapterItem>,
        action: ChapterDownloadAction,
    ) {
        viewScope.launch {
            when (action) {
                ChapterDownloadAction.START -> {
                    downloadChapters(items.map { it.chapter })
                    if (items.any { it.downloadState == Download.State.ERROR }) {
                        DownloadService.start(activity!!)
                    }
                }
                ChapterDownloadAction.START_NOW -> {
                    val chapterId = items.singleOrNull()?.chapter?.id ?: return@launch
                    presenter.startDownloadingNow(chapterId)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterId = items.singleOrNull()?.chapter?.id ?: return@launch
                    presenter.cancelDownload(chapterId)
                }
                ChapterDownloadAction.DELETE -> {
                    deleteChapters(items.map { it.chapter })
                }
            }
        }
    }

    private suspend fun downloadChapters(chapters: List<DomainChapter>) {
        presenter.downloadChapters(chapters)

        if (!presenter.isFavoritedManga) {
            val result = snackbarHostState.showSnackbar(
                message = activity!!.getString(R.string.snack_add_to_library),
                actionLabel = activity!!.getString(R.string.action_add),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && !presenter.isFavoritedManga) {
                onFavoriteClick()
            }
        }
    }

    private fun deleteChapters(chapters: List<DomainChapter>) {
        if (chapters.isEmpty()) return
        presenter.deleteChapters(chapters)
    }

    // OVERFLOW MENU DIALOGS

    private fun runDownloadChapterAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> presenter.getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> presenter.getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> presenter.getUnreadChaptersSorted().take(10)
            DownloadAction.CUSTOM -> {
                presenter.showDownloadCustomDialog()
                return
            }
            DownloadAction.UNREAD_CHAPTERS -> presenter.getUnreadChapters()
            DownloadAction.ALL_CHAPTERS -> {
                (presenter.state.value as? MangaScreenState.Success)?.chapters?.map { it.chapter }
            }
        }
        if (!chaptersToDownload.isNullOrEmpty()) {
            viewScope.launch { downloadChapters(chaptersToDownload) }
        }
    }

    // Chapters list - end

    // Tracker sheet - start
    fun onNextTrackers(trackers: List<TrackItem>) {
        trackSheet.onNextTrackers(trackers)
    }

    fun onTrackingRefreshDone() {
    }

    fun onTrackingRefreshError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        activity?.toast(error.message)
    }

    fun onTrackingSearchResults(results: List<TrackSearch>) {
        getTrackingSearchDialog()?.onSearchResults(results)
    }

    fun onTrackingSearchResultsError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        getTrackingSearchDialog()?.onSearchResultsError(error.message)
    }

    private fun getTrackingSearchDialog(): TrackSearchDialog? {
        return trackSheet.getSearchDialog()
    }

    // Tracker sheet - end

    companion object {
        const val FROM_SOURCE_EXTRA = "from_source"
        const val MANGA_EXTRA = "manga"
    }
}
