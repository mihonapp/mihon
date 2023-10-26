package eu.kanade.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.util.ThemePreviews

@ThemePreviews
@Composable
private fun NoActionPreview() {
    TachiyomiTheme {
        Surface {
            EmptyScreen(
                textResource = R.string.empty_screen,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun WithActionPreview() {
    TachiyomiTheme {
        Surface {
            EmptyScreen(
                textResource = R.string.empty_screen,
                actions = listOf(
                    EmptyScreenAction(
                        stringResId = R.string.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = {},
                    ),
                    EmptyScreenAction(
                        stringResId = R.string.getting_started_guide,
                        icon = Icons.Outlined.HelpOutline,
                        onClick = {},
                    ),
                ),
            )
        }
    }
}
