package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold

@Composable
fun PreferenceScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: () -> Unit = {},
    itemsProvider: @Composable () -> List<Preference>,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = onBackPressed,
                actions = actions,
                scrollBehavior = scrollBehavior,
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
