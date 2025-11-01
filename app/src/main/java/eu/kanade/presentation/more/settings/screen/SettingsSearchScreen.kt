package eu.kanade.presentation.more.settings.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

class SettingsSearchScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val softKeyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()

        // Hide keyboard on change screen
        DisposableEffect(Unit) {
            onDispose {
                softKeyboardController?.hide()
            }
        }

        // Hide keyboard on outside text field is touched
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                focusManager.clearFocus()
            }
        }

        // Request text field focus on launch
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }

        val textFieldState = rememberTextFieldState()
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            val canPop = remember { navigator.canPop }
                            if (canPop) {
                                IconButton(onClick = navigator::pop) {
                                    UpIcon()
                                }
                            }
                        },
                        title = {
                            BasicTextField(
                                state = textFieldState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .runOnEnterKeyPressed(action = focusManager::clearFocus),
                                textStyle = MaterialTheme.typography.bodyLarge
                                    .copy(color = MaterialTheme.colorScheme.onSurface),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                onKeyboardAction = { focusManager.clearFocus() },
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorator = {
                                    if (textFieldState.text.isEmpty()) {
                                        Text(
                                            text = stringResource(MR.strings.action_search_settings),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    it()
                                },
                            )
                        },
                        actions = {
                            if (textFieldState.text.isNotEmpty()) {
                                IconButton(onClick = { textFieldState.clearText() }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            },
        ) { contentPadding ->
            SearchResult(
                searchKey = textFieldState.text.toString(),
                listState = listState,
                contentPadding = contentPadding,
            ) { result ->
                SearchableSettings.highlightKey = result.highlightKey
                navigator.replace(result.route)
            }
        }
    }
}

@Composable
private fun SearchResult(
    searchKey: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    onItemClick: (SearchResultItem) -> Unit,
) {
    if (searchKey.isEmpty()) return

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val index = getIndex()
    val result by produceState<List<SearchResultItem>?>(initialValue = null, searchKey) {
        value = index.asSequence()
            .flatMap { settingsData ->
                settingsData.contents.asSequence()
                    // Only search from enabled prefs and one with valid title
                    .filter { it.enabled && it.title.isNotBlank() }
                    // Flatten items contained inside *enabled* PreferenceGroup
                    .flatMap { p ->
                        when (p) {
                            is Preference.PreferenceGroup -> {
                                if (p.enabled) {
                                    p.preferenceItems.asSequence()
                                        .filter { it.enabled && it.title.isNotBlank() }
                                        .map { p.title to it }
                                } else {
                                    emptySequence()
                                }
                            }
                            is Preference.PreferenceItem<*, *> -> sequenceOf(null to p)
                        }
                    }
                    // Don't show info preference
                    .filterNot { it.second is Preference.PreferenceItem.InfoPreference }
                    // Filter by search query
                    .filter { (_, p) ->
                        val inTitle = p.title.contains(searchKey, true)
                        val inSummary = p.subtitle?.contains(searchKey, true) ?: false
                        inTitle || inSummary
                    }
                    // Map result data
                    .map { (categoryTitle, p) ->
                        SearchResultItem(
                            route = settingsData.route,
                            title = p.title,
                            breadcrumbs = getLocalizedBreadcrumb(
                                path = settingsData.title,
                                node = categoryTitle,
                                isLtr = isLtr,
                            ),
                            highlightKey = p.title,
                        )
                    }
            }
            .take(10) // Just take top 10 result for quicker result
            .toList()
    }

    Crossfade(
        targetState = result,
        label = "results",
    ) {
        when {
            it == null -> {}
            it.isEmpty() -> {
                EmptyScreen(stringResource(MR.strings.no_results_found))
            }
            else -> {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(
                        items = it,
                        key = { i -> i.hashCode() },
                    ) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = item.title,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = item.breadcrumbs,
                                modifier = Modifier.paddingFromBaseline(top = 16.dp),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun getIndex() = settingScreens
    .map { screen ->
        SettingsData(
            title = stringResource(screen.getTitleRes()),
            route = screen,
            contents = screen.getPreferences(),
        )
    }

private fun getLocalizedBreadcrumb(path: String, node: String?, isLtr: Boolean): String {
    return if (node == null) {
        path
    } else {
        if (isLtr) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }
}

private val settingScreens = listOf(
    SettingsAppearanceScreen,
    SettingsLibraryScreen,
    SettingsReaderScreen,
    SettingsDownloadScreen,
    SettingsTrackingScreen,
    SettingsBrowseScreen,
    SettingsDataScreen,
    SettingsSecurityScreen,
    SettingsAdvancedScreen,
)

private data class SettingsData(
    val title: String,
    val route: VoyagerScreen,
    val contents: List<Preference>,
)

private data class SearchResultItem(
    val route: VoyagerScreen,
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
)
