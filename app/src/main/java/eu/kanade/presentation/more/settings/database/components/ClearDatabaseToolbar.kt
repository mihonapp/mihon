package eu.kanade.presentation.more.settings.database.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.database.ClearDatabaseState
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseToolbar(
    state: ClearDatabaseState,
    navigateUp: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        title = stringResource(R.string.pref_clear_database),
        navigateUp = navigateUp,
        actions = {
            if (state.isEmpty.not()) {
                AppBarActions(
                    actions = listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_select_all),
                            icon = Icons.Outlined.SelectAll,
                            onClick = onClickSelectAll,
                        ),
                        AppBar.Action(
                            title = stringResource(R.string.action_select_all),
                            icon = Icons.Outlined.FlipToBack,
                            onClick = onClickInvertSelection,
                        ),
                    ),
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
