package mihon.feature.upcoming

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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun UpcomingScreen() {
    val navigator = LocalNavigator.current

    val viewModel = metroViewModel<UpcomingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.dialog) {
        is UpcomingViewModel.Dialog.FilterSheet -> {
            UpcomingFilterDialog(
                viewModel = viewModel,
            )
        }

        null -> {}
    }

    UpcomingScreenContent(
        state = state,
        setSelectedYearMonth = viewModel::setSelectedYearMonth,
        onClickUpcoming = { navigator.push(MangaRoute(it.id)) },
        hasActiveFilters = state.hasActiveFilters,
        onClickFilter = viewModel::showFilterDialog,
    )
}

@Composable
private fun ColumnScope.CategoryFilterSheet(
    viewModel: UpcomingViewModel,
) {
    Text(
        stringResource(MR.strings.pref_filter_upcoming_categories_details),
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

    val excluded by viewModel.upcomingPreferences.filterExcludedCategories.collectAsState()
    val included by viewModel.upcomingPreferences.filterIncludedCategories.collectAsState()

    Column {
        allCategories.fastForEach { category ->
            key(category.id) {
                val state = when (category.id) {
                    in included -> TriState.ENABLED_IS
                    in excluded -> TriState.ENABLED_NOT
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
}

@Composable
fun UpcomingFilterDialog(
    viewModel: UpcomingViewModel,
) {
    TabbedDialog(
        onDismissRequest = viewModel::resetDialog,
        tabTitles = listOf(
            stringResource(MR.strings.categories),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            CategoryFilterSheet(viewModel = viewModel)
        }
    }
}
