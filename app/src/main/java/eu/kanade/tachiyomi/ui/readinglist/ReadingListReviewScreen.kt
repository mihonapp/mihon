package eu.kanade.tachiyomi.ui.readinglist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.EvidenceAgreement
import tachiyomi.domain.readinglist.matching.MatchDecisionReason
import tachiyomi.domain.readinglist.matching.SourcePreferenceLevel
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListCandidateRejection
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListStoredMatchCandidate
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.Locale

class ReadingListReviewScreen(
    private val readingListId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { ReadingListReviewScreenModel(readingListId) }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        ReadingListReviewContent(
            state = state,
            snackbarHostState = snackbarHostState,
            navigateUp = navigator::pop,
            onReload = { screenModel.reload(showLoading = state.review == null) },
            onConfirmCandidate = screenModel::confirmCandidate,
            onRejectCandidate = screenModel::rejectCandidate,
            onRestoreCandidate = screenModel::restoreCandidate,
            onConfirmSeriesMapping = screenModel::confirmSeriesMapping,
            onClearSeriesMapping = screenModel::clearSeriesMapping,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                snackbarHostState.showSnackbar(
                    when (event) {
                        ReadingListReviewEvent.CandidateConfirmed -> R.string.reading_list_review_candidate_confirmed
                        ReadingListReviewEvent.CandidateRejected -> R.string.reading_list_review_candidate_rejected
                        ReadingListReviewEvent.CandidateRestored -> R.string.reading_list_review_candidate_restored
                        ReadingListReviewEvent.SeriesMappingConfirmed -> R.string.reading_list_review_series_confirmed
                        ReadingListReviewEvent.SeriesMappingCleared -> R.string.reading_list_review_series_cleared
                        ReadingListReviewEvent.ReadingListMissing -> R.string.reading_list_missing_error
                        ReadingListReviewEvent.ActionFailed -> R.string.reading_list_review_action_failed
                    }.let { resourceId -> context.getString(resourceId) },
                )
            }
        }
    }
}

@Composable
private fun ReadingListReviewContent(
    state: ReadingListReviewScreenState,
    snackbarHostState: SnackbarHostState,
    navigateUp: () -> Unit,
    onReload: () -> Unit,
    onConfirmCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRejectCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRestoreCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onConfirmSeriesMapping: (Long, ReadingListCandidateIdentity) -> Unit,
    onClearSeriesMapping: (Long) -> Unit,
) {
    val review = state.review
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = review?.readingList?.name ?: stringResource(R.string.reading_list_review_title),
                subtitle = stringResource(R.string.reading_list_review_subtitle),
                navigateUp = navigateUp,
                actions = {
                    TextButton(
                        onClick = onReload,
                        enabled = !state.isLoading && state.activeAction == null,
                    ) {
                        Text(stringResource(R.string.reading_list_review_reload))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            state.isLoading && review == null -> LoadingScreen(Modifier.padding(paddingValues))
            state.isMissing || review == null -> MissingReviewScreen(paddingValues)
            else -> ReviewList(
                review = review,
                activeAction = state.activeAction,
                contentPadding = paddingValues,
                onConfirmCandidate = onConfirmCandidate,
                onRejectCandidate = onRejectCandidate,
                onRestoreCandidate = onRestoreCandidate,
                onConfirmSeriesMapping = onConfirmSeriesMapping,
                onClearSeriesMapping = onClearSeriesMapping,
            )
        }
    }
}

@Composable
private fun MissingReviewScreen(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reading_list_missing_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewList(
    review: ReadingListReviewData,
    activeAction: ReadingListReviewAction?,
    contentPadding: PaddingValues,
    onConfirmCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRejectCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRestoreCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onConfirmSeriesMapping: (Long, ReadingListCandidateIdentity) -> Unit,
    onClearSeriesMapping: (Long) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ReadingListReviewFilter.ATTENTION) }
    val expandedEntries = remember { mutableStateMapOf<Long, Boolean>() }
    val entries = when (filter) {
        ReadingListReviewFilter.ATTENTION -> review.entries.filter { item ->
            item.entry.needsManualAttention && !item.entry.skipped
        }
        ReadingListReviewFilter.ALL -> review.entries
        ReadingListReviewFilter.COMPLETED -> review.entries.filterNot { item -> item.entry.needsManualAttention }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            ReviewSummary(review)
        }
        item(key = "filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadingListReviewFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.reading_list_review_filter_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(
                items = entries,
                key = { item -> item.entry.id },
            ) { item ->
                val expanded = expandedEntries[item.entry.id]
                    ?: (item.entry.needsManualAttention && !item.entry.skipped)
                ReviewEntryCard(
                    item = item,
                    expanded = expanded,
                    actionsEnabled = activeAction == null,
                    onToggleExpanded = { expandedEntries[item.entry.id] = !expanded },
                    onConfirmCandidate = onConfirmCandidate,
                    onRejectCandidate = onRejectCandidate,
                    onRestoreCandidate = onRestoreCandidate,
                    onConfirmSeriesMapping = onConfirmSeriesMapping,
                    onClearSeriesMapping = onClearSeriesMapping,
                )
            }
        }
    }
}

@Composable
private fun ReviewSummary(review: ReadingListReviewData) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.reading_list_review_summary,
                    review.entries.size,
                    review.needsReviewCount,
                    review.completedCount,
                    review.protectedCount,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            review.readingList.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            if (review.readingList.warnings.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.reading_list_warning_count,
                        review.readingList.warnings.size,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.reading_list_review_offline_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewEntryCard(
    item: ReadingListReviewEntry,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onToggleExpanded: () -> Unit,
    onConfirmCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRejectCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRestoreCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onConfirmSeriesMapping: (Long, ReadingListCandidateIdentity) -> Unit,
    onClearSeriesMapping: (Long) -> Unit,
) {
    val entry = item.entry
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.reading_list_review_entry_title,
                            entry.position + 1,
                            entry.series,
                            entry.number,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusParts = mutableListOf(stringResource(entry.resolutionState.labelRes))
                    if (entry.userConfirmed) {
                        statusParts += stringResource(R.string.reading_list_review_user_confirmed)
                    }
                    if (entry.skipped) {
                        statusParts += stringResource(R.string.reading_list_review_skipped)
                    }
                    statusParts += stringResource(
                        R.string.reading_list_review_candidate_count,
                        item.candidates.size,
                    )
                    Text(
                        text = statusParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.needsManualAttention && !entry.skipped) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.reading_list_review_collapse_entry
                            } else {
                                R.string.reading_list_review_expand_entry
                            },
                        ),
                    )
                }
            }

            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OriginalEntryMetadata(item)
                    item.entryOverride?.let { entryOverride ->
                        Text(
                            text = stringResource(
                                R.string.reading_list_review_override,
                                entryOverride.sourceId,
                                entryOverride.mangaUrl ?: stringResource(R.string.reading_list_review_any_series),
                                entryOverride.chapterUrl ?: stringResource(R.string.reading_list_review_any_issue),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    item.seriesMapping?.let { mapping ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.reading_list_review_series_mapping,
                                    mapping.sourceId,
                                    mapping.mangaUrl,
                                    stringResource(
                                        if (mapping.userConfirmed) {
                                            R.string.reading_list_review_mapping_confirmed
                                        } else {
                                            R.string.reading_list_review_mapping_automatic
                                        },
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            TextButton(
                                onClick = { onClearSeriesMapping(entry.id) },
                                enabled = actionsEnabled,
                            ) {
                                Text(stringResource(R.string.reading_list_review_clear_series_mapping))
                            }
                        }
                    }

                    if (item.candidates.isEmpty() && item.orphanRejections.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reading_list_review_no_candidates),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.candidates.forEachIndexed { index, candidate ->
                        CandidateCard(
                            entry = item,
                            candidate = candidate,
                            rank = index + 1,
                            actionsEnabled = actionsEnabled,
                            onConfirmCandidate = onConfirmCandidate,
                            onRejectCandidate = onRejectCandidate,
                            onRestoreCandidate = onRestoreCandidate,
                            onConfirmSeriesMapping = onConfirmSeriesMapping,
                        )
                    }
                    item.orphanRejections.forEach { rejection ->
                        OrphanRejectionCard(
                            entryId = entry.id,
                            rejection = rejection,
                            actionsEnabled = actionsEnabled,
                            onRestoreCandidate = onRestoreCandidate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginalEntryMetadata(item: ReadingListReviewEntry) {
    val entry = item.entry
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(R.string.reading_list_review_original_metadata),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(stringResource(R.string.reading_list_review_original_series, entry.series))
        Text(stringResource(R.string.reading_list_review_original_issue, entry.number))
        entry.volume?.let { volume -> Text(stringResource(R.string.reading_list_review_original_volume, volume)) }
        entry.year?.let { year -> Text(stringResource(R.string.reading_list_review_original_year, year)) }
        Text(
            text = stringResource(
                R.string.reading_list_review_preserved_metadata,
                entry.databases.size,
                entry.extraAttributes.size,
                entry.extraElements.values.sumOf { values -> values.size },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateCard(
    entry: ReadingListReviewEntry,
    candidate: ReadingListStoredMatchCandidate,
    rank: Int,
    actionsEnabled: Boolean,
    onConfirmCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRejectCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onRestoreCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
    onConfirmSeriesMapping: (Long, ReadingListCandidateIdentity) -> Unit,
) {
    val snapshot = candidate.snapshot
    val currentMatch = entry.entry.matchedSourceId == snapshot.identity.sourceId &&
        entry.entry.matchedMangaUrl == snapshot.mangaUrl &&
        entry.entry.matchedChapterUrl == snapshot.chapterUrl
    val currentSeriesMapping = entry.seriesMapping?.let { mapping ->
        mapping.sourceId == snapshot.identity.sourceId && mapping.mangaUrl == snapshot.mangaUrl
    } == true

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.reading_list_review_candidate_rank,
                        rank,
                        snapshot.score.asScorePercent(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (candidate.rejected) {
                    Text(
                        text = stringResource(R.string.reading_list_review_rejected),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (currentMatch) {
                    Text(
                        text = stringResource(R.string.reading_list_review_current_match),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.reading_list_review_candidate_identity,
                    snapshot.seriesTitle,
                    snapshot.issueNumber,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.reading_list_review_candidate_source,
                    snapshot.sourceName,
                    snapshot.sourceLanguage.uppercase(Locale.ROOT),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.reading_list_review_decision,
                    stringResource(snapshot.decisionReason.labelRes),
                    snapshot.leadOverRunnerUp?.asScorePercent()
                        ?: stringResource(R.string.reading_list_review_no_runner_up),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CandidateBreakdown(candidate)

            Button(
                onClick = { onConfirmCandidate(entry.entry.id, snapshot.identity) },
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (currentMatch && entry.entry.userConfirmed) {
                            R.string.reading_list_review_confirm_again
                        } else {
                            R.string.reading_list_review_confirm_candidate
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = {
                    if (candidate.rejected) {
                        onRestoreCandidate(entry.entry.id, snapshot.identity)
                    } else {
                        onRejectCandidate(entry.entry.id, snapshot.identity)
                    }
                },
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (candidate.rejected) {
                            R.string.reading_list_review_restore_candidate
                        } else {
                            R.string.reading_list_review_reject_candidate
                        },
                    ),
                )
            }
            TextButton(
                onClick = { onConfirmSeriesMapping(entry.entry.id, snapshot.identity) },
                enabled = actionsEnabled && !currentSeriesMapping,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (currentSeriesMapping) {
                            R.string.reading_list_review_series_already_mapped
                        } else {
                            R.string.reading_list_review_confirm_series_mapping
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun CandidateBreakdown(candidate: ReadingListStoredMatchCandidate) {
    val breakdown = candidate.snapshot.breakdown
    val conflicts = mutableListOf<String>()
    if (!breakdown.issueEquivalent) conflicts += stringResource(R.string.reading_list_review_conflict_issue)
    if (breakdown.titleSimilarity < 0.85) conflicts += stringResource(R.string.reading_list_review_conflict_title)
    if (breakdown.yearEvidence == EvidenceAgreement.MISMATCH) {
        conflicts += stringResource(R.string.reading_list_review_conflict_year)
    }
    if (breakdown.volumeEvidence == EvidenceAgreement.MISMATCH) {
        conflicts += stringResource(R.string.reading_list_review_conflict_volume)
    }
    if (breakdown.externalIdentifierEvidence == EvidenceAgreement.MISMATCH) {
        conflicts += stringResource(R.string.reading_list_review_conflict_identifier)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.reading_list_review_breakdown),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(
                R.string.reading_list_review_breakdown_title,
                (breakdown.titleSimilarity * 100).asSimilarityPercent(),
                breakdown.titlePoints.asPoints(),
            ),
        )
        Text(
            stringResource(
                R.string.reading_list_review_breakdown_issue,
                stringResource(
                    if (breakdown.issueEquivalent) {
                        R.string.reading_list_review_equivalent
                    } else {
                        R.string.reading_list_review_not_equivalent
                    },
                ),
                breakdown.issuePoints.asPoints(),
            ),
        )
        EvidenceLine(
            label = stringResource(R.string.reading_list_review_year_evidence),
            evidence = breakdown.yearEvidence,
            points = breakdown.yearPoints,
        )
        EvidenceLine(
            label = stringResource(R.string.reading_list_review_volume_evidence),
            evidence = breakdown.volumeEvidence,
            points = breakdown.volumePoints,
        )
        EvidenceLine(
            label = stringResource(R.string.reading_list_review_identifier_evidence),
            evidence = breakdown.externalIdentifierEvidence,
            points = breakdown.externalIdentifierPoints,
        )
        Text(
            stringResource(
                R.string.reading_list_review_source_preference,
                stringResource(breakdown.sourcePreference.labelRes),
                breakdown.sourcePreferencePoints.asPoints(),
            ),
        )
        Text(
            stringResource(
                R.string.reading_list_review_confirmed_history,
                stringResource(breakdown.confirmedHistory.labelRes),
                breakdown.confirmedHistoryPoints.asPoints(),
            ),
        )
        Text(
            text = if (conflicts.isEmpty()) {
                stringResource(R.string.reading_list_review_no_conflicts)
            } else {
                stringResource(R.string.reading_list_review_conflicts, conflicts.joinToString())
            },
            color = if (conflicts.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.reading_list_review_remote_identity,
                candidate.snapshot.mangaUrl,
                candidate.snapshot.chapterUrl,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvidenceLine(
    label: String,
    evidence: EvidenceAgreement,
    points: Double,
) {
    Text(
        stringResource(
            R.string.reading_list_review_evidence,
            label,
            stringResource(evidence.labelRes),
            points.asPoints(),
        ),
    )
}

@Composable
private fun OrphanRejectionCard(
    entryId: Long,
    rejection: ReadingListCandidateRejection,
    actionsEnabled: Boolean,
    onRestoreCandidate: (Long, ReadingListCandidateIdentity) -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reading_list_review_removed_rejected_candidate),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(
                    R.string.reading_list_review_removed_candidate_identity,
                    rejection.identity.sourceId,
                    rejection.mangaUrl,
                    rejection.chapterUrl,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { onRestoreCandidate(entryId, rejection.identity) },
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reading_list_review_restore_candidate))
            }
        }
    }
}

private enum class ReadingListReviewFilter(
    @StringRes val labelRes: Int,
) {
    ATTENTION(R.string.reading_list_review_filter_attention),
    ALL(R.string.reading_list_review_filter_all),
    COMPLETED(R.string.reading_list_review_filter_completed),
}

private val ReadingListEntryResolutionState.labelRes: Int
    @StringRes get() = when (this) {
        ReadingListEntryResolutionState.UNSEARCHED -> R.string.reading_list_state_unsearched
        ReadingListEntryResolutionState.SEARCHING -> R.string.reading_list_state_searching
        ReadingListEntryResolutionState.AUTO_MATCHED -> R.string.reading_list_state_auto_matched
        ReadingListEntryResolutionState.USER_CONFIRMED -> R.string.reading_list_state_user_confirmed
        ReadingListEntryResolutionState.AMBIGUOUS -> R.string.reading_list_state_ambiguous
        ReadingListEntryResolutionState.UNRESOLVED -> R.string.reading_list_state_unresolved
        ReadingListEntryResolutionState.SOURCE_UNAVAILABLE -> R.string.reading_list_state_source_unavailable
        ReadingListEntryResolutionState.CHAPTER_REMOVED -> R.string.reading_list_state_chapter_removed
        ReadingListEntryResolutionState.NEEDS_REMATCH -> R.string.reading_list_state_needs_rematch
    }

private val MatchDecisionReason.labelRes: Int
    @StringRes get() = when (this) {
        MatchDecisionReason.NO_CANDIDATES -> R.string.reading_list_reason_no_candidates
        MatchDecisionReason.USER_CONFIRMED -> R.string.reading_list_reason_user_confirmed
        MatchDecisionReason.BELOW_REVIEW_THRESHOLD -> R.string.reading_list_reason_below_review
        MatchDecisionReason.BELOW_AUTO_THRESHOLD -> R.string.reading_list_reason_below_auto
        MatchDecisionReason.ISSUE_MISMATCH -> R.string.reading_list_reason_issue_mismatch
        MatchDecisionReason.TITLE_TOO_WEAK -> R.string.reading_list_reason_title_weak
        MatchDecisionReason.INSUFFICIENT_LEAD -> R.string.reading_list_reason_insufficient_lead
        MatchDecisionReason.AUTO_ACCEPTED -> R.string.reading_list_reason_auto_accepted
    }

private val EvidenceAgreement.labelRes: Int
    @StringRes get() = when (this) {
        EvidenceAgreement.UNKNOWN -> R.string.reading_list_evidence_unknown
        EvidenceAgreement.MATCH -> R.string.reading_list_evidence_match
        EvidenceAgreement.MISMATCH -> R.string.reading_list_evidence_mismatch
    }

private val SourcePreferenceLevel.labelRes: Int
    @StringRes get() = when (this) {
        SourcePreferenceLevel.NONE -> R.string.reading_list_preference_none
        SourcePreferenceLevel.GLOBAL -> R.string.reading_list_preference_global
        SourcePreferenceLevel.READING_LIST -> R.string.reading_list_preference_list
        SourcePreferenceLevel.SERIES -> R.string.reading_list_preference_series
        SourcePreferenceLevel.ENTRY -> R.string.reading_list_preference_entry
    }

private val ConfirmedHistoryEvidence.labelRes: Int
    @StringRes get() = when (this) {
        ConfirmedHistoryEvidence.NONE -> R.string.reading_list_history_none
        ConfirmedHistoryEvidence.SOURCE -> R.string.reading_list_history_source
        ConfirmedHistoryEvidence.SERIES -> R.string.reading_list_history_series
    }

private fun Double.asScorePercent(): String = String.format(Locale.ROOT, "%.1f%%", this)

private fun Double.asSimilarityPercent(): String = String.format(Locale.ROOT, "%.1f%%", this)

private fun Double.asPoints(): String = String.format(Locale.ROOT, "%+.1f", this)
