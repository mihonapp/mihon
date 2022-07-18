package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.R

@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    // Text
    title: String?,
    subtitle: String? = null,
    // Up button
    navigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.Default.ArrowBack,
    // Menu
    actions: @Composable RowScope.() -> Unit = {},
    // Action mode
    actionModeCounter: Int = 0,
    onCancelActionMode: () -> Unit = {},
    actionModeActions: @Composable RowScope.() -> Unit = {},
    // Banners
    downloadedOnlyMode: Boolean = false,
    incognitoMode: Boolean = false,
) {
    val isActionMode by derivedStateOf { actionModeCounter > 0 }
    val backgroundColor = if (isActionMode) {
        TopAppBarDefaults.smallTopAppBarColors().containerColor(1f).value
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier.drawBehind { drawRect(backgroundColor) },
    ) {
        SmallTopAppBar(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            navigationIcon = {
                if (isActionMode) {
                    IconButton(onClick = onCancelActionMode) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.action_cancel),
                        )
                    }
                } else {
                    navigateUp?.let {
                        IconButton(onClick = it) {
                            Icon(
                                imageVector = navigationIcon,
                                contentDescription = stringResource(R.string.abc_action_bar_up_description),
                            )
                        }
                    }
                }
            },
            title = {
                if (isActionMode) {
                    AppBarTitle(actionModeCounter.toString())
                } else {
                    AppBarTitle(title, subtitle)
                }
            },
            actions = {
                if (isActionMode) {
                    actionModeActions()
                } else {
                    actions()
                }
            },
            // Background handled by parent
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        if (downloadedOnlyMode) {
            DownloadedOnlyModeBanner()
        }
        if (incognitoMode) {
            IncognitoModeBanner()
        }
    }
}

@Composable
fun AppBarTitle(
    title: String?,
    subtitle: String? = null,
) {
    Column {
        title?.let {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AppBarActions(
    actions: List<AppBar.AppBarAction>,
) {
    var showMenu by remember { mutableStateOf(false) }

    actions.filterIsInstance<AppBar.Action>().map {
        IconButton(
            onClick = it.onClick,
            enabled = it.enabled,
        ) {
            Icon(
                imageVector = it.icon,
                contentDescription = it.title,
            )
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflowActions.isNotEmpty()) {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.label_more))
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            overflowActions.map {
                DropdownMenuItem(
                    onClick = {
                        it.onClick()
                        showMenu = false
                    },
                    text = { Text(it.title, fontWeight = FontWeight.Normal) },
                )
            }
        }
    }
}

sealed interface AppBar {
    sealed interface AppBarAction

    data class Action(
        val title: String,
        val icon: ImageVector,
        val onClick: () -> Unit,
        val enabled: Boolean = true,
    ) : AppBarAction

    data class OverflowAction(
        val title: String,
        val onClick: () -> Unit,
    ) : AppBarAction
}
