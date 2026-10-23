package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.network.CookieRepository
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class CookieManagerScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = metroViewModel<CookieManagerViewModel>()
        val state by model.state.collectAsState()

        when (val s = state) {
            is CookieManagerViewModel.State.Loading -> LoadingScreen()
            is CookieManagerViewModel.State.Ready -> {
                if (s.showAddHostDialog) {
                    val textFieldState = rememberTextFieldState()

                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(focusRequester) {
                        focusRequester.requestFocus()
                    }

                    AlertDialog(
                        title = { Text(stringResource(MR.strings.action_add_host)) },
                        text = {
                            OutlinedTextField(
                                state = textFieldState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                        },
                        onDismissRequest = { model.setAddHostDialogVisible(false) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    model.addHost(textFieldState.text.toString())
                                    model.setAddHostDialogVisible(false)
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_add))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { model.setAddHostDialogVisible(false) },
                            ) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }

                if (s.hostToDelete != null) {
                    val host = s.hostToDelete
                    AlertDialog(
                        title = { Text(stringResource(MR.strings.are_you_sure)) },
                        text = { Text(stringResource(MR.strings.confirm_delete_host, host)) },
                        onDismissRequest = { model.setHostToDelete(null) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    model.deleteHost(host)
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { model.setHostToDelete(null) },
                            ) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }

                Scaffold(
                    topBar = {
                        AppBar(
                            title = stringResource(MR.strings.pref_cookie_manager),
                            navigateUp = navigator::pop,
                            actions = {
                                AppBarActions(
                                    actions = listOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_add_host),
                                            icon = MaterialSymbols.Rounded.Add,
                                            onClick = { model.setAddHostDialogVisible(true) },
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                ) { contentPadding ->
                    if (s.hosts.isEmpty()) {
                        EmptyScreen(MR.strings.no_hosts_indexed)
                    } else {
                        ScrollbarLazyColumn(
                            contentPadding = contentPadding,
                        ) {
                            items(s.hosts, key = { it }) { host ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navigator.push(CookieListScreen(host)) }
                                        .padding(
                                            horizontal = MaterialTheme.padding.medium,
                                            vertical = MaterialTheme.padding.small,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = host,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { model.setHostToDelete(host) },
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Delete,
                                            contentDescription = stringResource(MR.strings.action_delete),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class CookieManagerViewModel(
    private val cookieRepository: CookieRepository,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State.Loading)

    init {
        viewModelScope.launch {
            val hosts = cookieRepository.getAllHosts().sortedWith(String.CASE_INSENSITIVE_ORDER)
            state.update {
                when (it) {
                    is State.Ready -> it.copy(hosts = hosts)
                    is State.Loading -> State.Ready(hosts = hosts)
                }
            }
        }
    }

    fun setAddHostDialogVisible(visible: Boolean) = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showAddHostDialog = visible)
    }

    fun addHost(host: String) {
        if (host.isBlank()) return
        state.update { state ->
            if (state !is State.Ready) return@update state

            val hosts = (state.hosts + host.trim().lowercase())
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            state.copy(hosts = hosts)
        }
    }

    fun setHostToDelete(host: String?) = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(hostToDelete = host)
    }

    fun deleteHost(host: String) {
        viewModelScope.launch {
            cookieRepository.deleteHost(host)
            state.update { state ->
                if (state !is State.Ready) return@update state

                val hosts = state.hosts.filterNot { it == host }
                state.copy(hosts = hosts, hostToDelete = null)
            }
        }
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val hosts: List<String> = emptyList(),
            val showAddHostDialog: Boolean = false,
            val hostToDelete: String? = null,
        ) : State
    }
}
