package eu.kanade.presentation.more.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.tachiyomi.R

@Composable
fun SettingsMainScreen(
    navigateUp: () -> Unit,
    sections: List<SettingsSection>,
    onClickSearch: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(R.string.label_settings),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(R.string.action_search),
                                icon = Icons.Outlined.Search,
                                onClick = onClickSearch,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(
            contentPadding = contentPadding,
        ) {
            sections.map {
                item {
                    PreferenceRow(
                        title = stringResource(it.titleRes),
                        painter = it.painter,
                        onClick = it.onClick,
                    )
                }
            }
        }
    }
}

data class SettingsSection(
    @StringRes val titleRes: Int,
    val painter: Painter,
    val onClick: () -> Unit,
)
