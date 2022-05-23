package eu.kanade.presentation.more.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.search.SettingsSearchHelper
import eu.kanade.tachiyomi.ui.setting.search.SettingsSearchPresenter
import kotlin.reflect.full.createInstance

@Composable
fun SettingsSearchScreen(
    nestedScroll: NestedScrollConnection,
    presenter: SettingsSearchPresenter,
    onClickResult: (SettingsController) -> Unit,
) {
    val results by presenter.state.collectAsState()

    val scrollState = rememberLazyListState()
    ScrollbarLazyColumn(
        modifier = Modifier
            .nestedScroll(nestedScroll),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        state = scrollState,
    ) {
        items(
            items = results,
            key = { it.key.toString() },
        ) { result ->
            SearchResult(result, onClickResult)
        }
    }
}

@Composable
private fun SearchResult(
    result: SettingsSearchHelper.SettingsSearchResult,
    onClickResult: (SettingsController) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .clickable {
                // Must pass a new Controller instance to avoid this error
                // https://github.com/bluelinelabs/Conductor/issues/446
                val controller = result.searchController::class.createInstance()
                controller.preferenceKey = result.key
                onClickResult(controller)
            },
    ) {
        Text(
            text = result.title,
        )

        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.outline,
            ),
        )

        Text(
            text = result.breadcrumb,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
