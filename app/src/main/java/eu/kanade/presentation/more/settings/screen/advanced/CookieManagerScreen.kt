package eu.kanade.presentation.more.settings.screen.advanced

import android.webkit.CookieManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.network.CookieIndexRepository
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CookieManagerScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = viewModel<CookieManagerViewModel>()
        val state by model.state.collectAsState()

        when (val s = state) {
            is CookieManagerViewModel.State.Loading -> LoadingScreen()
            is CookieManagerViewModel.State.Ready -> {
                if (s.showAddHostDialog) {
                    val textFieldState = rememberTextFieldState()
                    AlertDialog(
                        title = { Text(stringResource(MR.strings.action_add_host)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                                OutlinedTextField(
                                    state = textFieldState,
                                    modifier = Modifier.fillMaxWidth(),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                )
                            }
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
                                            icon = Icons.Outlined.AddCircleOutline,
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
                            items(s.hosts) { host ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { navigator.push(CookieListScreen(host)) })
                                        .padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = host,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { model.deleteHost(host) },
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
                }
            }
        }
    }
}

class CookieManagerViewModel(
    private val cookieIndexRepository: CookieIndexRepository = Injekt.get(),
) : StateViewModel<CookieManagerViewModel.State>(State.Loading) {

    private val cookieManager = CookieManager.getInstance()

    init {
        viewModelScope.launchIO {
            cookieIndexRepository.getHosts().collect { hosts ->
                mutableState.update {
                    val sorted = hosts.sortedWith(String.CASE_INSENSITIVE_ORDER)
                    when (it) {
                        is State.Ready -> it.copy(hosts = sorted)
                        is State.Loading -> State.Ready(hosts = sorted)
                    }
                }
            }
        }
    }

    fun setAddHostDialogVisible(visible: Boolean) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(showAddHostDialog = visible)
    }

    fun addHost(host: String) {
        viewModelScope.launchIO {
            if (host.isBlank()) return@launchIO
            cookieIndexRepository.insertHost(host)
        }
    }

    fun deleteHost(host: String) {
        viewModelScope.launchIO {
            val cookieIndices = cookieIndexRepository.getCookieIndex(host).first()

            for (cookieIndex in cookieIndices) {
                val domain = cookieIndex.domain.ifEmpty { host }
                val urlString = "https://$domain${cookieIndex.path}"
                val cookieString = buildString {
                    append("${cookieIndex.key}=; Max-Age=0")
                    if (domain.isNotEmpty()) append("; Domain=$domain")
                    append("; Path=${cookieIndex.path}")
                }
                cookieManager.setCookie(urlString, cookieString)
            }

            cookieManager.flush()
            cookieIndexRepository.deleteHost(host)
        }
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val hosts: List<String> = emptyList(),
            val showAddHostDialog: Boolean = false,
        ) : State
    }
}
