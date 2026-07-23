package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.manga.interactor.DuplicateEntry
import tachiyomi.domain.manga.interactor.DuplicateGroup
import tachiyomi.domain.manga.interactor.DuplicateSearchMode
import tachiyomi.domain.manga.interactor.FindDuplicates
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class DuplicatesFinderScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<DuplicatesFinderViewModel>()
        val state by viewModel.state.collectAsState()

        var showResolveAllDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_duplicate_finder_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when (val currentStep = state.step) {
                    is DuplicatesFinderViewModel.Step.Configuring -> {
                        ConfiguringContent(
                            mode = state.searchMode,
                            onModeChange = viewModel::setSearchMode,
                            threshold = state.threshold,
                            onThresholdChange = viewModel::setThreshold,
                            mergeProgress = state.mergeProgress,
                            onMergeProgressChange = viewModel::setMergeProgress,
                            deleteDownloads = state.deleteDownloads,
                            onDeleteDownloadsChange = viewModel::setDeleteDownloads,
                            onStartSearch = viewModel::startSearch,
                        )
                    }

                    is DuplicatesFinderViewModel.Step.Searching -> {
                        SearchingContent(
                            processed = currentStep.processed,
                            total = currentStep.total,
                            onCancel = viewModel::cancelSearch,
                        )
                    }

                    is DuplicatesFinderViewModel.Step.Results -> {
                        if (currentStep.groups.isEmpty()) {
                            EmptyResultsContent(onBack = navigator::pop)
                        } else {
                            ResultsContent(
                                groups = currentStep.groups,
                                onSelectEntry = viewModel::selectEntryForGroup,
                                onResolveGroup = viewModel::resolveGroup,
                                onResolveAll = { showResolveAllDialog = true },
                                onCancel = navigator::pop,
                            )
                        }
                    }
                }
            }

            if (showResolveAllDialog) {
                val groupsCount = (state.step as? DuplicatesFinderViewModel.Step.Results)?.groups?.size ?: 0
                AlertDialog(
                    onDismissRequest = { showResolveAllDialog = false },
                    title = { Text(text = stringResource(MR.strings.action_resolve_all_suggested)) },
                    text = { Text(text = stringResource(MR.strings.duplicate_resolve_all_confirm, groupsCount)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResolveAllDialog = false
                                viewModel.resolveAllGroups()
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_resolve_all_suggested))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResolveAllDialog = false }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun ConfiguringContent(
        mode: DuplicateSearchMode,
        onModeChange: (DuplicateSearchMode) -> Unit,
        threshold: Float,
        onThresholdChange: (Float) -> Unit,
        mergeProgress: Boolean,
        onMergeProgressChange: (Boolean) -> Unit,
        deleteDownloads: Boolean,
        onDeleteDownloadsChange: (Boolean) -> Unit,
        onStartSearch: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                    Text(
                        text = stringResource(MR.strings.duplicate_search_options),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeChange(DuplicateSearchMode.EXACT) }
                            .padding(vertical = MaterialTheme.padding.small),
                    ) {
                        RadioButton(
                            selected = mode == DuplicateSearchMode.EXACT,
                            onClick = { onModeChange(DuplicateSearchMode.EXACT) },
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                        Column {
                            Text(
                                text = stringResource(MR.strings.duplicate_search_type_exact),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(MR.strings.duplicate_search_type_exact_summary),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeChange(DuplicateSearchMode.FUZZY) }
                            .padding(vertical = MaterialTheme.padding.small),
                    ) {
                        RadioButton(
                            selected = mode == DuplicateSearchMode.FUZZY,
                            onClick = { onModeChange(DuplicateSearchMode.FUZZY) },
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                        Column {
                            Text(
                                text = stringResource(MR.strings.duplicate_search_type_fuzzy),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(MR.strings.duplicate_search_type_fuzzy_summary),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    if (mode == DuplicateSearchMode.FUZZY) {
                        Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                        Column(modifier = Modifier.padding(horizontal = MaterialTheme.padding.small)) {
                            Text(
                                text = stringResource(
                                    MR.strings.duplicate_search_tolerance,
                                    (threshold * 100).roundToInt(),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Slider(
                                value = threshold,
                                onValueChange = onThresholdChange,
                                valueRange = 0.5f..1.0f,
                                steps = 9,
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(MaterialTheme.padding.small)) {
                    LabeledCheckbox(
                        label = stringResource(MR.strings.duplicate_search_option_merge),
                        checked = mergeProgress,
                        onCheckedChange = onMergeProgressChange,
                    )
                    LabeledCheckbox(
                        label = stringResource(MR.strings.duplicate_search_option_delete_downloads),
                        checked = deleteDownloads,
                        onCheckedChange = onDeleteDownloadsChange,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onStartSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(MR.strings.action_filter))
            }
        }
    }

    @Composable
    private fun SearchingContent(
        processed: Int,
        total: Int,
        onCancel: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.padding.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
            Text(
                text = stringResource(MR.strings.duplicate_search_in_progress),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { processed.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                Text(
                    text = stringResource(MR.strings.duplicate_search_progress_count, processed, total),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.padding.large))
            OutlinedButton(onClick = onCancel) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        }
    }

    @Composable
    private fun EmptyResultsContent(onBack: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.padding.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(MR.strings.duplicate_search_no_duplicates),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
            Button(onClick = onBack) {
                Text(text = stringResource(MR.strings.action_bar_up_description))
            }
        }
    }

    @Composable
    private fun ResultsContent(
        groups: List<DuplicateGroup>,
        onSelectEntry: (groupIndex: Int, selectedId: Long) -> Unit,
        onResolveGroup: (group: DuplicateGroup) -> Unit,
        onResolveAll: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.duplicate_search_found_groups, groups.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onResolveAll) {
                    Text(text = stringResource(MR.strings.action_resolve_all_suggested))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                items(
                    items = groups,
                    key = { it.mainTitle + it.entries.firstOrNull()?.manga?.id },
                ) { group ->
                    val groupIndex = groups.indexOf(group)
                    DuplicateGroupCard(
                        group = group,
                        onSelect = { selectedId -> onSelectEntry(groupIndex, selectedId) },
                        onResolve = { onResolveGroup(group) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        }
    }

    @Composable
    private fun DuplicateGroupCard(
        group: DuplicateGroup,
        onSelect: (selectedId: Long) -> Unit,
        onResolve: () -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                Text(
                    text = group.mainTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                group.entries.forEach { entry ->
                    DuplicateEntryRow(
                        entry = entry,
                        isSelected = entry.manga.id == group.selectedId,
                        onSelect = { onSelect(entry.manga.id) },
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onResolve) {
                        Text(text = stringResource(MR.strings.action_keep_selected))
                    }
                }
            }
        }
    }

    @Composable
    private fun DuplicateEntryRow(
        entry: DuplicateEntry,
        isSelected: Boolean,
        onSelect: () -> Unit,
    ) {
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val sourceName = remember(entry.manga.source) {
            sourceManager.get(entry.manga.source)?.name ?: "Unknown"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
                .padding(vertical = MaterialTheme.padding.extraSmall),
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
            )
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
            MangaCover.Book(
                data = entry.manga.asMangaCover(),
                modifier = Modifier.height(60.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
                BadgeGroup {
                    if (entry.isSuggested) {
                        Badge(
                            text = stringResource(MR.strings.duplicate_suggested_preserve),
                            color = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (entry.isAlive) {
                        Badge(
                            text = stringResource(MR.strings.duplicate_status_alive),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    } else {
                        Badge(
                            text = stringResource(MR.strings.duplicate_status_dead),
                            color = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Badge(
                        text = stringResource(MR.strings.duplicate_chapters_count, entry.chapterCount),
                    )
                }
            }
        }
    }
}

class DuplicatesFinderViewModel : StateViewModel<DuplicatesFinderViewModel.State>(State()) {

    private val findDuplicates: FindDuplicates = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val updateChapter: UpdateChapter = Injekt.get()
    private val getCategories: GetCategories = Injekt.get()
    private val setMangaCategories: SetMangaCategories = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()

    fun setSearchMode(mode: DuplicateSearchMode) {
        mutableState.update { it.copy(searchMode = mode) }
    }

    fun setThreshold(threshold: Float) {
        mutableState.update { it.copy(threshold = threshold) }
    }

    fun setMergeProgress(merge: Boolean) {
        mutableState.update { it.copy(mergeProgress = merge) }
    }

    fun setDeleteDownloads(delete: Boolean) {
        mutableState.update { it.copy(deleteDownloads = delete) }
    }

    fun startSearch() {
        val mode = state.value.searchMode
        val threshold = state.value.threshold

        mutableState.update { it.copy(step = Step.Searching(processed = 0, total = 0)) }

        viewModelScope.launchIO {
            try {
                val groups = findDuplicates.await(
                    mode = mode,
                    threshold = threshold,
                    onProgress = { processed, total ->
                        mutableState.update {
                            it.copy(step = Step.Searching(processed = processed, total = total))
                        }
                    },
                )
                mutableState.update { it.copy(step = Step.Results(groups = groups)) }
            } catch (e: Exception) {
                mutableState.update { it.copy(step = Step.Results(groups = emptyList())) }
            }
        }
    }

    fun cancelSearch() {
        mutableState.update { it.copy(step = Step.Configuring) }
    }

    fun selectEntryForGroup(groupIndex: Int, selectedId: Long) {
        val currentResults = state.value.step as? Step.Results ?: return
        val updatedGroups = currentResults.groups.toMutableList()
        if (groupIndex in updatedGroups.indices) {
            val group = updatedGroups[groupIndex]
            updatedGroups[groupIndex] = group.copy(selectedId = selectedId)
            mutableState.update { it.copy(step = Step.Results(groups = updatedGroups)) }
        }
    }

    fun resolveGroup(group: DuplicateGroup) {
        viewModelScope.launchIO {
            executeResolveGroup(group)
            val currentResults = state.value.step as? Step.Results ?: return@launchIO
            val remainingGroups = currentResults.groups.filterNot { it.mainTitle == group.mainTitle }
            mutableState.update { it.copy(step = Step.Results(groups = remainingGroups)) }
        }
    }

    fun resolveAllGroups() {
        val currentResults = state.value.step as? Step.Results ?: return
        val groupsToResolve = currentResults.groups.toList()

        viewModelScope.launchIO {
            for (group in groupsToResolve) {
                executeResolveGroup(group)
            }
            mutableState.update { it.copy(step = Step.Results(groups = emptyList())) }
        }
    }

    private suspend fun executeResolveGroup(group: DuplicateGroup) {
        val preservedEntry = group.entries.firstOrNull { it.manga.id == group.selectedId }
            ?: group.entries.firstOrNull() ?: return
        val discardedEntries = group.entries.filter { it.manga.id != preservedEntry.manga.id }

        val merge = state.value.mergeProgress
        val deleteDownloads = state.value.deleteDownloads

        for (discarded in discardedEntries) {
            val currentManga = discarded.manga
            val targetManga = preservedEntry.manga
            val currentSource = sourceManager.get(currentManga.source)

            if (merge) {
                try {
                    val prevChapters = getChaptersByMangaId.await(currentManga.id)
                    val targetChapters = getChaptersByMangaId.await(targetManga.id)
                    val maxChapterRead = prevChapters.filter { it.read }.maxOfOrNull { it.chapterNumber }

                    val updatedTargetChapters = targetChapters.map { targetCh ->
                        var ch = targetCh
                        if (ch.isRecognizedNumber) {
                            val prevCh = prevChapters.find {
                                it.isRecognizedNumber && it.chapterNumber == ch.chapterNumber
                            }
                            if (prevCh != null) {
                                ch = ch.copy(
                                    dateFetch = prevCh.dateFetch,
                                    bookmark = prevCh.bookmark,
                                )
                            }
                            if (maxChapterRead != null && ch.chapterNumber <= maxChapterRead) {
                                ch = ch.copy(read = true)
                            }
                        }
                        ch
                    }
                    updateChapter.awaitAll(updatedTargetChapters.map { it.toChapterUpdate() })

                    val categories = getCategories.await(currentManga.id).map { it.id }
                    if (categories.isNotEmpty()) {
                        val existingCategories = getCategories.await(targetManga.id).map { it.id }
                        setMangaCategories.await(targetManga.id, (categories + existingCategories).distinct())
                    }

                    if (currentManga.hasCustomCover() && !targetManga.hasCustomCover()) {
                        val customFile = coverCache.getCustomCoverFile(currentManga.id)
                        if (customFile.exists()) {
                            customFile.inputStream().use { stream ->
                                coverCache.setCustomCoverToCache(targetManga, stream)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (deleteDownloads && currentSource != null) {
                try {
                    downloadManager.deleteManga(currentManga, currentSource)
                } catch (_: Exception) {
                }
            }

            updateManga.await(
                MangaUpdate(
                    id = currentManga.id,
                    favorite = false,
                    dateAdded = 0,
                ),
            )
        }

        updateManga.await(
            MangaUpdate(
                id = preservedEntry.manga.id,
                favorite = true,
            ),
        )
    }

    @Immutable
    data class State(
        val step: Step = Step.Configuring,
        val searchMode: DuplicateSearchMode = DuplicateSearchMode.EXACT,
        val threshold: Float = 0.80f,
        val mergeProgress: Boolean = true,
        val deleteDownloads: Boolean = true,
    )

    sealed interface Step {
        data object Configuring : Step
        data class Searching(val processed: Int, val total: Int) : Step
        data class Results(val groups: List<DuplicateGroup>) : Step
    }
}
