package eu.kanade.presentation.more.settings.screen

import android.content.res.Resources
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.isLTR

class SettingsSearchScreen : Screen {
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

        var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = navigator::pop) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        title = {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge
                                    .copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.action_search_settings),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    it()
                                },
                            )
                        },
                        actions = {
                            if (textFieldValue.text.isNotEmpty()) {
                                IconButton(onClick = { textFieldValue = TextFieldValue() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    Divider()
                }
            },
        ) { contentPadding ->
            SearchResult(
                searchKey = textFieldValue.text,
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
                            is Preference.PreferenceItem<*> -> sequenceOf(null to p)
                            else -> emptySequence() // Ignore other prefs
                        }
                    }
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
                            breadcrumbs = getLocalizedBreadcrumb(path = settingsData.title, node = categoryTitle),
                            highlightKey = p.title,
                        )
                    }
            }
            .take(10) // Just take top 10 result for quicker result
            .toList()
    }

    Crossfade(targetState = result) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                it == null -> {
                    /* Don't show anything just yet */
                }
                // No result
                it.isEmpty() -> item { EmptyScreen(stringResource(R.string.no_results_found)) }
                // Show result list
                else -> items(
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

private fun getLocalizedBreadcrumb(path: String, node: String?): String {
    return if (node == null) {
        path
    } else {
        if (Resources.getSystem().isLTR) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }
}

private val settingScreens = listOf(
    SettingsGeneralScreen(),
    SettingsAppearanceScreen(),
    SettingsLibraryScreen(),
    SettingsReaderScreen(),
    SettingsDownloadScreen(),
    SettingsTrackingScreen(),
    SettingsBrowseScreen(),
    SettingsBackupScreen(),
    SettingsSecurityScreen(),
    SettingsAdvancedScreen(),
)

private data class SettingsData(
    val title: String,
    val route: Screen,
    val contents: List<Preference>,
)

private data class SearchResultItem(
    val route: Screen,
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
)
