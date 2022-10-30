package eu.kanade.tachiyomi.ui.download

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.OverflowMenu
import eu.kanade.presentation.components.Pill
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.util.lang.launchUI
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Controller that shows the currently active downloads.
 */
class DownloadController :
    FullComposeController<DownloadPresenter>(),
    DownloadAdapter.DownloadItemListener {

    private lateinit var controllerBinding: DownloadListBinding

    /**
     * Adapter containing the active downloads.
     */
    private var adapter: DownloadAdapter? = null

    /**
     * Map of subscriptions for active downloads.
     */
    private val progressSubscriptions by lazy { mutableMapOf<Download, Subscription>() }

    override fun createPresenter() = DownloadPresenter()

    @Composable
    override fun ComposeContent() {
        val context = LocalContext.current
        val downloadList by presenter.state.collectAsState()
        val downloadCount by remember {
            derivedStateOf { downloadList.sumOf { it.subItems.size } }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var fabExpanded by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            // All this lines just for fab state :/
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    fabExpanded = available.y >= 0
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.label_download_queue),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (downloadCount > 0) {
                                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                                Pill(
                                    text = "$downloadCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground
                                        .copy(alpha = pillAlpha),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    navigateUp = router::popCurrentController,
                    actions = {
                        if (downloadList.isNotEmpty()) {
                            OverflowMenu { closeMenu ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_reorganize_by)) },
                                    children = {
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(R.string.action_order_by_upload_date)) },
                                            children = {
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.action_newest)) },
                                                    onClick = {
                                                        reorderQueue(
                                                            { it.download.chapter.date_upload },
                                                            true,
                                                        )
                                                        closeMenu()
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.action_oldest)) },
                                                    onClick = {
                                                        reorderQueue(
                                                            { it.download.chapter.date_upload },
                                                            false,
                                                        )
                                                        closeMenu()
                                                    },
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(R.string.action_order_by_chapter_number)) },
                                            children = {
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.action_asc)) },
                                                    onClick = {
                                                        reorderQueue(
                                                            { it.download.chapter.chapter_number },
                                                            false,
                                                        )
                                                        closeMenu()
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.action_desc)) },
                                                    onClick = {
                                                        reorderQueue(
                                                            { it.download.chapter.chapter_number },
                                                            true,
                                                        )
                                                        closeMenu()
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_cancel_all)) },
                                    onClick = {
                                        presenter.clearQueue(context)
                                        closeMenu()
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = downloadList.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    val isRunning by DownloadService.isRunning.collectAsState()
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (isRunning) {
                                R.string.action_pause
                            } else {
                                R.string.action_resume
                            }
                            Text(text = stringResource(id))
                        },
                        icon = {
                            val icon = if (isRunning) {
                                Icons.Outlined.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            }
                            Icon(imageVector = icon, contentDescription = null)
                        },
                        onClick = {
                            if (isRunning) {
                                DownloadService.stop(context)
                                presenter.pauseDownloads()
                            } else {
                                DownloadService.start(context)
                            }
                        },
                        expanded = fabExpanded,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            },
        ) { contentPadding ->
            if (downloadList.isEmpty()) {
                EmptyScreen(
                    textResource = R.string.information_no_downloads,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val left = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx().roundToInt() }
            val top = with(density) { contentPadding.calculateTopPadding().toPx().roundToInt() }
            val right = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx().roundToInt() }
            val bottom = with(density) { contentPadding.calculateBottomPadding().toPx().roundToInt() }

            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                AndroidView(
                    factory = { context ->
                        controllerBinding = DownloadListBinding.inflate(LayoutInflater.from(context))
                        adapter = DownloadAdapter(this@DownloadController)
                        controllerBinding.recycler.adapter = adapter
                        adapter?.isHandleDragEnabled = true
                        adapter?.fastScroller = controllerBinding.fastScroller
                        controllerBinding.recycler.layoutManager = LinearLayoutManager(context)

                        ViewCompat.setNestedScrollingEnabled(controllerBinding.root, true)

                        viewScope.launchUI {
                            presenter.getDownloadStatusFlow()
                                .collect(this@DownloadController::onStatusChange)
                        }
                        viewScope.launchUI {
                            presenter.getDownloadProgressFlow()
                                .collect(this@DownloadController::onUpdateDownloadedPages)
                        }

                        controllerBinding.root
                    },
                    update = {
                        controllerBinding.recycler
                            .updatePadding(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                            )

                        controllerBinding.fastScroller
                            .updateLayoutParams<MarginLayoutParams> {
                                leftMargin = left
                                topMargin = top
                                rightMargin = right
                                bottomMargin = bottom
                            }

                        adapter?.updateDataSet(downloadList)
                    },
                )
            }
        }
    }

    override fun onDestroyView(view: View) {
        for (subscription in progressSubscriptions.values) {
            subscription.unsubscribe()
        }
        progressSubscriptions.clear()
        adapter = null
        super.onDestroyView(view)
    }

    private fun <R : Comparable<R>> reorderQueue(selector: (DownloadItem) -> R, reverse: Boolean = false) {
        val adapter = adapter ?: return
        val newDownloads = mutableListOf<Download>()
        adapter.headerItems.forEach { headerItem ->
            headerItem as DownloadHeaderItem
            headerItem.subItems = headerItem.subItems.sortedBy(selector).toMutableList().apply {
                if (reverse) {
                    reverse()
                }
            }
            newDownloads.addAll(headerItem.subItems.map { it.download })
        }
        presenter.reorder(newDownloads)
    }

    /**
     * Called when the status of a download changes.
     *
     * @param download the download whose status has changed.
     */
    private fun onStatusChange(download: Download) {
        when (download.status) {
            Download.State.DOWNLOADING -> {
                observeProgress(download)
                // Initial update of the downloaded pages
                onUpdateDownloadedPages(download)
            }
            Download.State.DOWNLOADED -> {
                unsubscribeProgress(download)
                onUpdateProgress(download)
                onUpdateDownloadedPages(download)
            }
            Download.State.ERROR -> unsubscribeProgress(download)
            else -> {
                /* unused */
            }
        }
    }

    /**
     * Observe the progress of a download and notify the view.
     *
     * @param download the download to observe its progress.
     */
    private fun observeProgress(download: Download) {
        val subscription = Observable.interval(50, TimeUnit.MILLISECONDS)
            // Get the sum of percentages for all the pages.
            .flatMap {
                Observable.from(download.pages)
                    .map(Page::progress)
                    .reduce { x, y -> x + y }
            }
            // Keep only the latest emission to avoid backpressure.
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { progress ->
                // Update the view only if the progress has changed.
                if (download.totalProgress != progress) {
                    download.totalProgress = progress
                    onUpdateProgress(download)
                }
            }

        // Avoid leaking subscriptions
        progressSubscriptions.remove(download)?.unsubscribe()

        progressSubscriptions[download] = subscription
    }

    /**
     * Unsubscribes the given download from the progress subscriptions.
     *
     * @param download the download to unsubscribe.
     */
    private fun unsubscribeProgress(download: Download) {
        progressSubscriptions.remove(download)?.unsubscribe()
    }

    /**
     * Called when the progress of a download changes.
     *
     * @param download the download whose progress has changed.
     */
    private fun onUpdateProgress(download: Download) {
        getHolder(download)?.notifyProgress()
    }

    /**
     * Called when a page of a download is downloaded.
     *
     * @param download the download whose page has been downloaded.
     */
    private fun onUpdateDownloadedPages(download: Download) {
        getHolder(download)?.notifyDownloadedPages()
    }

    /**
     * Returns the holder for the given download.
     *
     * @param download the download to find.
     * @return the holder of the download or null if it's not bound.
     */
    private fun getHolder(download: Download): DownloadHolder? {
        return controllerBinding.recycler.findViewHolderForItemId(download.chapter.id!!) as? DownloadHolder
    }

    /**
     * Called when an item is released from a drag.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        val adapter = adapter ?: return
        val downloads = adapter.headerItems.flatMap { header ->
            adapter.getSectionItems(header).map { item ->
                (item as DownloadItem).download
            }
        }
        presenter.reorder(downloads)
    }

    /**
     * Called when the menu item of a download is pressed
     *
     * @param position The position of the item
     * @param menuItem The menu Item pressed
     */
    override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
        val item = adapter?.getItem(position) ?: return
        if (item is DownloadItem) {
            when (menuItem.itemId) {
                R.id.move_to_top, R.id.move_to_bottom -> {
                    val headerItems = adapter?.headerItems ?: return
                    val newDownloads = mutableListOf<Download>()
                    headerItems.forEach { headerItem ->
                        headerItem as DownloadHeaderItem
                        if (headerItem == item.header) {
                            headerItem.removeSubItem(item)
                            if (menuItem.itemId == R.id.move_to_top) {
                                headerItem.addSubItem(0, item)
                            } else {
                                headerItem.addSubItem(item)
                            }
                        }
                        newDownloads.addAll(headerItem.subItems.map { it.download })
                    }
                    presenter.reorder(newDownloads)
                }
                R.id.move_to_top_series -> {
                    val (selectedSeries, otherSeries) = adapter?.currentItems
                        ?.filterIsInstance<DownloadItem>()
                        ?.map(DownloadItem::download)
                        ?.partition { item.download.manga.id == it.manga.id }
                        ?: Pair(listOf(), listOf())
                    presenter.reorder(selectedSeries + otherSeries)
                }
                R.id.cancel_download -> {
                    presenter.cancelDownload(item.download)
                }
                R.id.cancel_series -> {
                    val allDownloadsForSeries = adapter?.currentItems
                        ?.filterIsInstance<DownloadItem>()
                        ?.filter { item.download.manga.id == it.download.manga.id }
                        ?.map(DownloadItem::download)
                    if (!allDownloadsForSeries.isNullOrEmpty()) {
                        presenter.cancelDownloads(allDownloadsForSeries)
                    }
                }
            }
        }
    }
}
