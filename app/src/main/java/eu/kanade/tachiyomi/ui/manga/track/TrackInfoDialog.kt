package eu.kanade.tachiyomi.ui.manga.track

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMangaWithChapters
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.DeleteTrack
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AlertDialogContent
import eu.kanade.presentation.manga.TrackChapterSelector
import eu.kanade.presentation.manga.TrackDateSelector
import eu.kanade.presentation.manga.TrackInfoDialogHome
import eu.kanade.presentation.manga.TrackScoreSelector
import eu.kanade.presentation.manga.TrackServiceSearch
import eu.kanade.presentation.manga.TrackStatusSelector
import eu.kanade.presentation.util.LocalNavigatorContentPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class TrackInfoDialogHomeScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val sourceId: Long,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val sm = rememberScreenModel { Model(mangaId, sourceId) }

        val dateFormat = remember { UiPreferences.dateFormat(Injekt.get<UiPreferences>().dateFormat().get()) }
        val state by sm.state.collectAsState()

        TrackInfoDialogHome(
            trackItems = state.trackItems,
            dateFormat = dateFormat,
            contentPadding = LocalNavigatorContentPadding.current,
            onStatusClick = {
                navigator.push(
                    TrackStatusSelectorScreen(
                        track = it.track!!,
                        serviceId = it.service.id,
                    ),
                )
            },
            onChapterClick = {
                navigator.push(
                    TrackChapterSelectorScreen(
                        track = it.track!!,
                        serviceId = it.service.id,
                    ),
                )
            },
            onScoreClick = {
                navigator.push(
                    TrackScoreSelectorScreen(
                        track = it.track!!,
                        serviceId = it.service.id,
                    ),
                )
            },
            onStartDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track!!,
                        serviceId = it.service.id,
                        start = true,
                    ),
                )
            },
            onEndDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track!!,
                        serviceId = it.service.id,
                        start = false,
                    ),
                )
            },
            onNewSearch = {
                if (it.service is EnhancedTrackService) {
                    sm.registerEnhancedTracking(it)
                } else {
                    navigator.push(
                        TrackServiceSearchScreen(
                            mangaId = mangaId,
                            initialQuery = it.track?.title ?: mangaTitle,
                            currentUrl = it.track?.tracking_url,
                            serviceId = it.service.id,
                        ),
                    )
                }
            },
            onOpenInBrowser = { openTrackerInBrowser(context, it) },
            onRemoved = { sm.unregisterTracking(it.service.id) },
        )
    }

    /**
     * Opens registered tracker url in browser
     */
    private fun openTrackerInBrowser(context: Context, trackItem: TrackItem) {
        val url = trackItem.track?.tracking_url ?: return
        if (url.isNotBlank()) {
            context.openInBrowser(url)
        }
    }

    private class Model(
        private val mangaId: Long,
        private val sourceId: Long,
        private val getTracks: GetTracks = Injekt.get(),
        private val deleteTrack: DeleteTrack = Injekt.get(),
    ) : StateScreenModel<Model.State>(State()) {

        init {
            // Refresh data
            coroutineScope.launch {
                try {
                    val trackItems = getTracks.await(mangaId).mapToTrackItem()
                    val insertTrack = Injekt.get<InsertTrack>()
                    val getMangaWithChapters = Injekt.get<GetMangaWithChapters>()
                    val syncTwoWayService = Injekt.get<SyncChaptersWithTrackServiceTwoWay>()
                    trackItems.forEach {
                        val track = it.track ?: return@forEach
                        val domainTrack = it.service.refresh(track).toDomainTrack() ?: return@forEach
                        insertTrack.await(domainTrack)

                        if (it.service is EnhancedTrackService) {
                            val allChapters = getMangaWithChapters.awaitChapters(mangaId)
                            syncTwoWayService.await(allChapters, domainTrack, it.service)
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to refresh track data mangaId=$mangaId" }
                    withUIContext { Injekt.get<Application>().toast(e.message) }
                }
            }

            coroutineScope.launch {
                getTracks.subscribe(mangaId)
                    .catch { logcat(LogPriority.ERROR, it) }
                    .distinctUntilChanged()
                    .map { it.mapToTrackItem() }
                    .collectLatest { trackItems -> mutableState.update { it.copy(trackItems = trackItems) } }
            }
        }

        fun registerEnhancedTracking(item: TrackItem) {
            item.service as EnhancedTrackService
            coroutineScope.launchNonCancellable {
                val manga = Injekt.get<GetManga>().await(mangaId)?.toDbManga() ?: return@launchNonCancellable
                try {
                    val matchResult = item.service.match(manga) ?: throw Exception()
                    item.service.registerTracking(matchResult, mangaId)
                } catch (e: Exception) {
                    withUIContext { Injekt.get<Application>().toast(R.string.error_no_match) }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            coroutineScope.launchNonCancellable { deleteTrack.await(mangaId, serviceId) }
        }

        private fun List<eu.kanade.domain.track.model.Track>.mapToTrackItem(): List<TrackItem> {
            val dbTracks = map { it.toDbTrack() }
            val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
            val source = Injekt.get<SourceManager>().getOrStub(sourceId)
            return loggedServices
                // Map to TrackItem
                .map { service -> TrackItem(dbTracks.find { it.sync_id.toLong() == service.id }, service) }
                // Show only if the service supports this manga's source
                .filter { (it.service as? EnhancedTrackService)?.accept(source) ?: true }
        }

        data class State(
            val trackItems: List<TrackItem> = emptyList(),
        )
    }
}

private data class TrackStatusSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                track = track,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
            )
        }
        val state by sm.state.collectAsState()
        TrackStatusSelector(
            contentPadding = LocalNavigatorContentPadding.current,
            selection = state.selection,
            onSelectionChange = sm::setSelection,
            selections = remember { sm.getSelections() },
            onConfirm = { sm.setStatus(); navigator.pop() },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val service: TrackService,
    ) : StateScreenModel<Model.State>(State(track.status)) {

        fun getSelections(): Map<Int, String> {
            return service.getStatusList().associateWith { service.getStatus(it) }
        }

        fun setSelection(selection: Int) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            coroutineScope.launchNonCancellable {
                service.setRemoteStatus(track, state.value.selection)
            }
        }

        data class State(
            val selection: Int,
        )
    }
}

private data class TrackChapterSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                track = track,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
            )
        }
        val state by sm.state.collectAsState()

        TrackChapterSelector(
            contentPadding = LocalNavigatorContentPadding.current,
            selection = state.selection,
            onSelectionChange = sm::setSelection,
            range = remember { sm.getRange() },
            onConfirm = { sm.setChapter(); navigator.pop() },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val service: TrackService,
    ) : StateScreenModel<Model.State>(State(track.last_chapter_read.toInt())) {

        fun getRange(): Iterable<Int> {
            val endRange = if (track.total_chapters > 0) {
                track.total_chapters
            } else {
                10000
            }
            return 0..endRange
        }

        fun setSelection(selection: Int) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setChapter() {
            coroutineScope.launchNonCancellable {
                service.setRemoteLastChapterRead(track, state.value.selection)
            }
        }

        data class State(
            val selection: Int,
        )
    }
}

private data class TrackScoreSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                track = track,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
            )
        }
        val state by sm.state.collectAsState()

        TrackScoreSelector(
            contentPadding = LocalNavigatorContentPadding.current,
            selection = state.selection,
            onSelectionChange = sm::setSelection,
            selections = remember { sm.getSelections() },
            onConfirm = { sm.setScore(); navigator.pop() },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val service: TrackService,
    ) : StateScreenModel<Model.State>(State(service.displayScore(track))) {

        fun getSelections(): List<String> {
            return service.getScoreList()
        }

        fun setSelection(selection: String) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setScore() {
            coroutineScope.launchNonCancellable {
                service.setRemoteScore(track, state.value.selection)
            }
        }

        data class State(
            val selection: String,
        )
    }
}

private data class TrackDateSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                track = track,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
                start = start,
            )
        }
        val state by sm.state.collectAsState()

        val canRemove = if (start) {
            track.started_reading_date > 0
        } else {
            track.finished_reading_date > 0
        }
        TrackDateSelector(
            contentPadding = LocalNavigatorContentPadding.current,
            title = if (start) {
                stringResource(R.string.track_started_reading_date)
            } else {
                stringResource(R.string.track_finished_reading_date)
            },
            selection = state.selection,
            onSelectionChange = sm::setSelection,
            onConfirm = { sm.setDate(); navigator.pop() },
            onRemove = { sm.confirmRemoveDate(navigator) }.takeIf { canRemove },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val track: Track,
        private val service: TrackService,
        private val start: Boolean,
    ) : StateScreenModel<Model.State>(
        State(
            (if (start) track.started_reading_date else track.finished_reading_date)
                .takeIf { it != 0L }
                ?.let {
                    Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                ?: LocalDate.now(),
        ),
    ) {

        fun setSelection(selection: LocalDate) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setDate() {
            coroutineScope.launchNonCancellable {
                val millis = state.value.selection.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
                if (start) {
                    service.setRemoteStartDate(track, millis)
                } else {
                    service.setRemoteFinishDate(track, millis)
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, service.id, start))
        }

        data class State(
            val selection: LocalDate,
        )
    }
}

private data class TrackDateRemoverScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                track = track,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
                start = start,
            )
        }
        AlertDialogContent(
            modifier = Modifier.padding(LocalNavigatorContentPadding.current),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.track_remove_date_conf_title),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                val serviceName = stringResource(sm.getServiceNameRes())
                Text(
                    text = if (start) {
                        stringResource(R.string.track_remove_start_date_conf_text, serviceName)
                    } else {
                        stringResource(R.string.track_remove_finish_date_conf_text, serviceName)
                    },
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                    FilledTonalButton(
                        onClick = { sm.removeDate(); navigator.popUntilRoot() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(R.string.action_remove))
                    }
                }
            },
        )
    }

    private class Model(
        private val track: Track,
        private val service: TrackService,
        private val start: Boolean,
    ) : ScreenModel {

        fun getServiceNameRes() = service.nameRes()

        fun removeDate() {
            coroutineScope.launchNonCancellable {
                if (start) {
                    service.setRemoteStartDate(track, 0)
                } else {
                    service.setRemoteFinishDate(track, 0)
                }
            }
        }
    }
}

data class TrackServiceSearchScreen(
    private val mangaId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sm = rememberScreenModel {
            Model(
                mangaId = mangaId,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                service = Injekt.get<TrackManager>().getService(serviceId)!!,
            )
        }

        val state by sm.state.collectAsState()

        var textFieldValue by remember { mutableStateOf(TextFieldValue(initialQuery)) }
        TrackServiceSearch(
            contentPadding = LocalNavigatorContentPadding.current,
            query = textFieldValue,
            onQueryChange = { textFieldValue = it },
            onDispatchQuery = { sm.trackingSearch(textFieldValue.text) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = sm::updateSelection,
            onConfirmSelection = { sm.registerTracking(state.selected!!); navigator.pop() },
            onDismissRequest = navigator::pop,
        )
    }

    private class Model(
        private val mangaId: Long,
        private val currentUrl: String? = null,
        initialQuery: String,
        private val service: TrackService,
    ) : StateScreenModel<Model.State>(State()) {

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            coroutineScope.launch {
                // To show loading state
                mutableState.update { it.copy(queryResult = null, selected = null) }

                val result = withIOContext {
                    try {
                        val results = service.search(query)
                        Result.success(results)
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
                mutableState.update { oldState ->
                    oldState.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                    )
                }
            }
        }

        fun registerTracking(item: Track) {
            coroutineScope.launchNonCancellable { service.registerTracking(item, mangaId) }
        }

        fun updateSelection(selected: TrackSearch) {
            mutableState.update { it.copy(selected = selected) }
        }

        data class State(
            val queryResult: Result<List<TrackSearch>>? = null,
            val selected: TrackSearch? = null,
        )
    }
}
