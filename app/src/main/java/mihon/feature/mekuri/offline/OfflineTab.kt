package mihon.feature.mekuri.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * Mekuri: the Offline tab. The download queue (only while there is one) and every library manga with downloaded
 * chapters, so it is obvious what can be read without internet.
 */
data object OfflineTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = stringResource(MR.strings.mekuri_tab_offline),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Download),
        )

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<OfflineViewModel>()
        val queue by viewModel.queueState.collectAsStateWithLifecycle()
        val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.mekuri_tab_offline),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val items = downloaded
            if (items != null && items.isEmpty() && queue == DownloadQueueState.Stopped) {
                EmptyScreen(
                    message = stringResource(MR.strings.mekuri_offline_empty),
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }
            ScrollbarLazyColumn(contentPadding = contentPadding) {
                if (queue != DownloadQueueState.Stopped) {
                    item(key = "queue") {
                        QueueCard(queue = queue, onClick = { navigator.push(DownloadQueueScreen) })
                    }
                }
                if (!items.isNullOrEmpty()) {
                    item(key = "header") {
                        Text(
                            text = stringResource(MR.strings.mekuri_offline_ready),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(items = items, key = { it.manga.id }) { item ->
                        DownloadedRow(item = item, onClick = { navigator.push(MangaScreen(item.manga.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(queue: DownloadQueueState, onClick: () -> Unit) {
    val (label, pending) = when (queue) {
        is DownloadQueueState.Downloading -> stringResource(MR.strings.mekuri_offline_downloading) to queue.pending
        is DownloadQueueState.Paused -> stringResource(MR.strings.paused) to queue.pending
        DownloadQueueState.Stopped -> return
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (queue is DownloadQueueState.Downloading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DownloadedRow(item: OfflineViewModel.Item, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MangaCover.Book(
            data = item.manga,
            modifier = Modifier.height(72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.manga.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(MR.strings.mekuri_offline_chapters, item.chapters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class OfflineViewModel(
    downloadManager: DownloadManager,
    downloadCache: DownloadCache,
    getLibraryManga: GetLibraryManga,
) : ViewModel() {

    data class Item(val manga: Manga, val chapters: Int)

    val queueState: StateFlow<DownloadQueueState> = combine(
        downloadManager.isDownloaderRunning,
        downloadManager.queueState,
    ) { running, queue ->
        when {
            queue.isEmpty() -> DownloadQueueState.Stopped
            !running -> DownloadQueueState.Paused(queue.size)
            else -> DownloadQueueState.Downloading(queue.size)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQueueState.Stopped)

    /** null while loading. */
    val downloaded: StateFlow<List<Item>?> = combine(
        getLibraryManga.subscribe(),
        downloadCache.changes,
    ) { library, _ ->
        val mangas = library.map { it.manga }.distinctBy { it.id }
        val counts = downloadManager.getDownloadCounts(mangas)
        mangas
            .mapNotNull { manga -> counts[manga.id]?.takeIf { it > 0 }?.let { Item(manga, it) } }
            .sortedBy { it.manga.title.lowercase() }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
