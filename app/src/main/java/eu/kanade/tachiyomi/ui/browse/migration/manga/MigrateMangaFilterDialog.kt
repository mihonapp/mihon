package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MigrateMangaFilterDialog(
    onDismissRequest: () -> Unit,
    viewModel: MigrateMangaViewModel,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.categories),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterSheet(viewModel = viewModel)
                1 -> CategoryFilterSheet(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterSheet(
    viewModel: MigrateMangaViewModel,
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = filters.downloaded,
        onClick = { viewModel.toggleFilterDownloaded() },
    )

    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filters.unread,
        onClick = { viewModel.toggleFilterUnread() },
    )

    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filters.started,
        onClick = { viewModel.toggleFilterStarted() },
    )

    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filters.bookmarked,
        onClick = { viewModel.toggleFilterBookmarked() },
    )
}

@Composable
private fun ColumnScope.CategoryFilterSheet(
    viewModel: MigrateMangaViewModel,
) {
    Text(
        stringResource(MR.strings.pref_filter_migrate_manga_categories_details),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))

    val allCategories by viewModel.getCategories.subscribe().collectAsState(initial = emptyList())

    if (allCategories.isEmpty()) {
        LoadingScreen(modifier = Modifier.padding(16.dp))
        return
    }

    val filters by viewModel.filters.collectAsStateWithLifecycle()

    Column {
        allCategories.fastForEach { category ->
            val state = when (category.id) {
                in filters.includedCategories -> TriState.ENABLED_IS
                in filters.excludedCategories -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
            TriStateItem(
                label = category.visualName,
                state = state,
                onClick = { viewModel.cycleCategory(category) },
            )
        }
    }
}
