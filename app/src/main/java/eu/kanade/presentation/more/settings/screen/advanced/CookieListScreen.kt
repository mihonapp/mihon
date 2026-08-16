package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.network.Cookie
import mihon.domain.network.CookieRepository
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CookieListScreen(val host: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val modelFactory = remember {
            viewModelFactory { initializer { CookieListViewModel(host) } }
        }
        val model = viewModel<CookieListViewModel>(factory = modelFactory)
        val state by model.state.collectAsState()

        when (val s = state) {
            is CookieListViewModel.State.Loading -> LoadingScreen()
            is CookieListViewModel.State.Ready -> {
                s.dialog?.let { dialog ->
                    val keyState = rememberTextFieldState(dialog.name)
                    val valueState = rememberTextFieldState(dialog.value)
                    val pathState = rememberTextFieldState(dialog.path)
                    var hostOnly by remember { mutableStateOf(dialog.hostOnly) }

                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(focusRequester) {
                        focusRequester.requestFocus()
                    }

                    AlertDialog(
                        onDismissRequest = { model.setDialog(null) },
                        title = {
                            Text(
                                text = stringResource(
                                    if (dialog.name.isEmpty()) {
                                        MR.strings.action_add_cookie
                                    } else {
                                        MR.strings.action_edit_cookie
                                    },
                                ),
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    state = keyState,
                                    label = { Text(stringResource(MR.strings.cookie_name)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (dialog.name.isEmpty()) {
                                                Modifier.focusRequester(
                                                    focusRequester,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    enabled = dialog.name.isEmpty(),
                                )
                                OutlinedTextField(
                                    state = valueState,
                                    label = { Text(stringResource(MR.strings.cookie_value)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (dialog.name.isNotEmpty()) {
                                                Modifier.focusRequester(
                                                    focusRequester,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                                OutlinedTextField(
                                    state = pathState,
                                    label = { Text(stringResource(MR.strings.cookie_path)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier
                                        .clickableNoIndication { hostOnly = !hostOnly }
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Host only",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Switch(
                                        checked = hostOnly,
                                        onCheckedChange = { hostOnly = !hostOnly },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    model.addOrUpdate(
                                        Cookie(
                                            name = keyState.text.toString(),
                                            value = valueState.text.toString(),
                                            path = pathState.text.toString(),
                                            hostOnly = hostOnly,
                                        ),
                                    )
                                    model.setDialog(null)
                                },
                            ) {
                                Text(stringResource(MR.strings.action_save))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { model.setDialog(null) }) {
                                Text(stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }

                Scaffold(
                    topBar = {
                        SearchToolbar(
                            titleContent = { Text(host) },
                            navigateUp = navigator::pop,
                            actions = {
                                AppBarActions(
                                    actions = listOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_add_host),
                                            icon = Icons.Outlined.AddCircleOutline,
                                            onClick = {
                                                model.setDialog(CookieListViewModel.Dialog())
                                            },
                                        ),
                                    ),
                                )
                            },
                            searchQuery = s.query,
                            onChangeSearchQuery = model::onSearchQueryChange,
                        )
                    },
                ) { contentPadding ->
                    if (s.cookies.isEmpty()) {
                        EmptyScreen(stringResource(MR.strings.no_cookies_indexed))
                    } else if (s.filteredCookies.isEmpty()) {
                        EmptyScreen(stringResource(MR.strings.no_results_found))
                    } else {
                        ScrollbarLazyColumn(
                            contentPadding = contentPadding,
                        ) {
                            items(s.filteredCookies, key = { "${it.path}$${it.name}$${it.hostOnly}" }) {
                                CookieItem(
                                    name = it.name,
                                    value = it.value,
                                    path = it.path,
                                    hostOnly = it.hostOnly,
                                    onClick = {
                                        model.setDialog(
                                            CookieListViewModel.Dialog(
                                                name = it.name,
                                                value = it.value,
                                                path = it.path,
                                                hostOnly = it.hostOnly,
                                            ),
                                        )
                                    },
                                    onClickDelete = { model.delete(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CookieItem(
        name: String,
        value: String,
        path: String,
        hostOnly: Boolean,
        onClick: () -> Unit,
        onClickDelete: () -> Unit,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "$name = $value",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Text(
                        text = "${stringResource(MR.strings.cookie_path)} = $path",
                        modifier = Modifier.secondaryItemAlpha(),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text = stringResource(MR.strings.cookie_host_only),
                        modifier = Modifier.alpha(if (hostOnly) 1f else 0f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                IconButton(
                    onClick = onClickDelete,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                    )
                }
            }
        }
    }
}

class CookieListViewModel(
    private val host: String,
    private val cookieRepository: CookieRepository = Injekt.get(),
) : StateViewModel<CookieListViewModel.State>(State.Loading) {

    init {
        viewModelScope.launchIO {
            fetchCookies()
        }
    }

    private suspend fun fetchCookies() {
        cookieRepository.getCookiesForHost(host).let { cookies ->
            mutableState.update {
                when (it) {
                    is State.Ready -> it.copy(cookies = cookies)
                    is State.Loading -> State.Ready(cookies = cookies)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String?) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(query = query)
    }

    fun setDialog(dialog: Dialog?) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(dialog = dialog)
    }

    fun delete(cookie: Cookie) {
        viewModelScope.launchIO {
            cookieRepository.deleteCookie(host, cookie)
            fetchCookies()
        }
    }

    fun addOrUpdate(cookie: Cookie) {
        val state = state.value

        if (state !is State.Ready) return
        val dialog = state.dialog
        if (cookie.name.isBlank() || cookie.value.isBlank()) return

        val path = if (cookie.path.startsWith("/")) cookie.path else "/${cookie.path}"
        viewModelScope.launchIO {
            if (dialog != null && dialog.name.isNotBlank()) {
                if (dialog.path != path || dialog.hostOnly != cookie.hostOnly) {
                    cookieRepository.deleteCookie(
                        host = host,
                        cookie = Cookie(
                            name = dialog.name,
                            value = dialog.value,
                            path = path,
                            hostOnly = dialog.hostOnly,
                        ),
                    )
                }
            }

            cookieRepository.addOrUpdateCookie(host, cookie)
            fetchCookies()
        }
    }

    data class Dialog(
        val name: String = "",
        val value: String = "",
        val path: String = "",
        val hostOnly: Boolean = false,
    )

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val cookies: List<Cookie> = emptyList(),
            val query: String? = null,
            val dialog: Dialog? = null,
        ) : State {
            val filteredCookies: List<Cookie>
                get() = if (query.isNullOrBlank()) {
                    cookies
                } else {
                    cookies.filter { it.name.contains(query, ignoreCase = true) }
                }
        }
    }
}
