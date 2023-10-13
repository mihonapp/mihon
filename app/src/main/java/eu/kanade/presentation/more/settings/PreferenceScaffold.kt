package eu.kanade.presentation.more.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun PreferenceScaffold(
    @StringRes titleRes: Int,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(titleRes),
                navigateUp = onBackPressed,
                actions = actions,
                scrollBehavior = it,
            )
        },
        content = { contentPadding ->
            PreferenceScreen(
                items = itemsProvider(),
                contentPadding = contentPadding,
            )
        },
    )
}
