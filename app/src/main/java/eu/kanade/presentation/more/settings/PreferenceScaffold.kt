package eu.kanade.presentation.more.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.UpIcon
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
            TopAppBar(
                title = { Text(text = stringResource(titleRes)) },
                navigationIcon = {
                    if (onBackPressed != null) {
                        IconButton(onClick = onBackPressed) {
                            UpIcon()
                        }
                    }
                },
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
