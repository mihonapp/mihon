package eu.kanade.presentation.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.showSoftKeyboard

const val SEARCH_DEBOUNCE_MILLIS = 250L

@Composable
fun AppBar(
    title: String?,

    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    // Text
    subtitle: String? = null,
    // Up button
    navigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    // Menu
    actions: @Composable RowScope.() -> Unit = {},
    // Action mode
    actionModeCounter: Int = 0,
    onCancelActionMode: () -> Unit = {},
    actionModeActions: @Composable RowScope.() -> Unit = {},

    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val isActionMode by remember(actionModeCounter) {
        derivedStateOf { actionModeCounter > 0 }
    }

    AppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(title, subtitle = subtitle)
            }
        },
        navigateUp = navigateUp,
        navigationIcon = navigationIcon,
        actions = {
            if (isActionMode) {
                actionModeActions()
            } else {
                actions()
            }
        },
        isActionMode = isActionMode,
        onCancelActionMode = onCancelActionMode,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun AppBar(
    // Title
    titleContent: @Composable () -> Unit,

    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    // Up button
    navigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    // Menu
    actions: @Composable RowScope.() -> Unit = {},
    // Action mode
    isActionMode: Boolean = false,
    onCancelActionMode: () -> Unit = {},

    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Column(
        modifier = modifier,
    ) {
        TopAppBar(
            navigationIcon = {
                if (isActionMode) {
                    IconButton(onClick = onCancelActionMode) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_cancel),
                        )
                    }
                } else {
                    navigateUp?.let {
                        IconButton(onClick = it) {
                            UpIcon(navigationIcon = navigationIcon)
                        }
                    }
                }
            },
            title = titleContent,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceColorAtElevation(
                    elevation = if (isActionMode) 3.dp else 0.dp,
                ),
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun AppBarTitle(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier) {
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
                modifier = Modifier.basicMarquee(
                    repeatDelayMillis = 2_000,
                ),
            )
        }
    }
}

@Composable
fun AppBarActions(
    actions: ImmutableList<AppBar.AppBarAction>,
) {
    var showMenu by remember { mutableStateOf(false) }

    actions.filterIsInstance<AppBar.Action>().map {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(it.title)
                }
            },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = it.onClick,
                enabled = it.enabled,
            ) {
                Icon(
                    imageVector = it.icon,
                    tint = it.iconTint ?: LocalContentColor.current,
                    contentDescription = it.title,
                )
            }
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflowActions.isNotEmpty()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(stringResource(MR.strings.action_menu_overflow_description))
                }
            },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = { showMenu = !showMenu },
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                )
            }
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

/**
 * @param searchEnabled Set to false if you don't want to show search action.
 * @param searchQuery If null, use normal toolbar.
 * @param placeholderText If null, [MR.strings.action_search_hint] is used.
 */
@Composable
fun SearchToolbar(
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    modifier: Modifier = Modifier,
    titleContent: @Composable () -> Unit = {},
    navigateUp: (() -> Unit)? = null,
    searchEnabled: Boolean = true,
    placeholderText: String? = null,
    onSearch: (String) -> Unit = {},
    onClickCloseSearch: () -> Unit = { onChangeSearchQuery(null) },
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focusRequester = remember { FocusRequester() }

    AppBar(
        modifier = modifier,
        titleContent = {
            if (searchQuery == null) return@AppBar titleContent()

            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current

            val searchAndClearFocus: () -> Unit = f@{
                if (searchQuery.isBlank()) return@f
                onSearch(searchQuery)
                focusManager.clearFocus()
                keyboardController?.hide()
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = onChangeSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .runOnEnterKeyPressed(action = searchAndClearFocus)
                    .showSoftKeyboard(remember { searchQuery.isEmpty() })
                    .clearFocusOnSoftKeyboardHide(),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchAndClearFocus() }),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    TextFieldDefaults.DecorationBox(
                        value = searchQuery,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        placeholder = {
                            Text(
                                modifier = Modifier.secondaryItemAlpha(),
                                text = (placeholderText ?: stringResource(MR.strings.action_search_hint)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                        },
                        container = {},
                    )
                },
            )
        },
        navigateUp = if (searchQuery == null) navigateUp else onClickCloseSearch,
        actions = {
            key("search") {
                val onClick = { onChangeSearchQuery("") }

                if (!searchEnabled) {
                    // Don't show search action
                } else if (searchQuery == null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(MR.strings.action_search))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = onClick,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(MR.strings.action_search),
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty()) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(MR.strings.action_reset))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                onClick()
                                focusRequester.requestFocus()
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_reset),
                            )
                        }
                    }
                }
            }

            key("actions") { actions() }
        },
        isActionMode = false,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun UpIcon(
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
) {
    val icon = navigationIcon
        ?: Icons.AutoMirrored.Outlined.ArrowBack
    Icon(
        imageVector = icon,
        contentDescription = stringResource(MR.strings.action_bar_up_description),
        modifier = modifier,
    )
}

sealed interface AppBar {
    sealed interface AppBarAction

    data class Action(
        val title: String,
        val icon: ImageVector,
        val iconTint: Color? = null,
        val onClick: () -> Unit,
        val enabled: Boolean = true,
    ) : AppBarAction

    data class OverflowAction(
        val title: String,
        val onClick: () -> Unit,
    ) : AppBarAction
}
