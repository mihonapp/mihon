package eu.kanade.presentation.more.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.components.ScrollbarLazyColumn

@Composable
fun SettingsMainScreen(
    nestedScrollInterop: NestedScrollConnection,
    sections: List<SettingsSection>,
) {
    ScrollbarLazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
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

data class SettingsSection(
    @StringRes val titleRes: Int,
    val painter: Painter,
    val onClick: () -> Unit,
)
