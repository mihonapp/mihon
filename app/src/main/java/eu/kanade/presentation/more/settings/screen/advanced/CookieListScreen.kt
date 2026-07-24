package eu.kanade.presentation.more.settings.screen.advanced

import android.webkit.CookieManager
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import mihon.domain.network.CookieIndex
import mihon.domain.network.CookieIndexRepository
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
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
                    val keyState = rememberTextFieldState(dialog.key)
                    val valueState = rememberTextFieldState(dialog.value)
                    val domainState = rememberTextFieldState(dialog.domain)
                    val pathState = rememberTextFieldState(dialog.path)

                    AlertDialog(
                        onDismissRequest = { model.setDialog(null) },
                        title = {
                            Text(
                                text = stringResource(
                                    if (dialog.key.isEmpty()) {
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
                                    label = { Text(stringResource(MR.strings.cookie_key)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = dialog.key.isEmpty(),
                                )
                                OutlinedTextField(
                                    state = valueState,
                                    label = { Text(stringResource(MR.strings.cookie_value)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    state = domainState,
                                    label = { Text(stringResource(MR.strings.cookie_domain)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    state = pathState,
                                    label = { Text(stringResource(MR.strings.cookie_path)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    model.addOrUpdate(
                                        key = keyState.text.toString(),
                                        value = valueState.text.toString(),
                                        domain = domainState.text.toString(),
                                        path = pathState.text.toString(),
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
                            items(s.filteredCookies, key = { it.id }) {
                                CookieItem(
                                    key = it.key,
                                    value = it.value,
                                    domain = it.domain,
                                    path = it.path,
                                    onClick = {
                                        model.setDialog(
                                            CookieListViewModel.Dialog(
                                                key = it.key,
                                                value = it.value,
                                                domain = it.domain,
                                                path = it.path,
                                            ),
                                        )
                                    },
                                    onClickDelete = { model.delete(it.id) },
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
        key: String,
        value: String,
        domain: String,
        path: String,
        onClick: () -> Unit,
        onClickDelete: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "$key = $value",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = "${stringResource(MR.strings.cookie_domain)} = $domain",
                    modifier = Modifier.secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    text = "${stringResource(MR.strings.cookie_path)} = $path",
                    modifier = Modifier.secondaryItemAlpha(),
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

class CookieListViewModel(
    private val host: String,
    private val cookieIndexRepository: CookieIndexRepository = Injekt.get(),
) : StateViewModel<CookieListViewModel.State>(State.Loading) {

    private val cookieManager = CookieManager.getInstance()

    init {
        viewModelScope.launchIO {
            cookieIndexRepository.getCookieIndex(host).collect { cookieIndices ->
                val cookies = cookieIndices.mapNotNull { cookieIndex ->
                    val domain = cookieIndex.domain.ifEmpty { host }
                    val urlString = "https://$domain${cookieIndex.path}"
                    val cookies = cookieManager.getCookie(urlString) ?: return@mapNotNull null
                    val targetCookie = cookies.split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("${cookieIndex.key}=") } ?: return@mapNotNull null
                    val value = targetCookie.substringAfter("=", "").trim()
                    Cookie(
                        cookieIndex.key,
                        value,
                        cookieIndex.domain,
                        cookieIndex.path,
                    )
                }

                mutableState.update {
                    when (it) {
                        is State.Ready -> it.copy(cookies = cookies)
                        is State.Loading -> State.Ready(cookies = cookies)
                    }
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

    fun delete(id: String) = mutableState.update { state ->
        if (state !is State.Ready) return@update state

        val cookie = state.cookies.firstOrNull { it.id == id } ?: return@update state
        val domain = cookie.domain.ifEmpty { host }
        val urlString = "https://$domain${cookie.path}"

        val cookieString = buildString {
            append("${cookie.key}=; Max-Age=0")
            if (cookie.domain.isNotEmpty()) append("; Domain=${cookie.domain}")
            append("; Path=${cookie.path}")
        }
        cookieManager.setCookie(urlString, cookieString)
        state.copy(cookies = state.cookies.filterNot { it.id == id })
    }

    fun addOrUpdate(key: String, value: String, domain: String, path: String) = mutableState.update { state ->
        if (state !is State.Ready) return@update state
        if (key.isBlank() || value.isBlank()) return@update state

        val targetDomain = domain.trim()
        val targetPath = if (path.isBlank()) "/" else "/${path.trim().removePrefix("/")}"

        val oldCookie = state.dialog?.takeIf { it.key.isNotBlank() }?.let { "${it.domain}${it.path}$${it.key}" }
            ?.let { id -> state.cookies.firstOrNull { it.id == id } }

        var cookieToOverwrite: Cookie? = null
        val cookiesToRemove = mutableListOf<Cookie>()

        for (cookie in state.cookies) {
            if (cookie.key != key) continue
            if (!cookie.conflicts(targetDomain, targetPath)) continue

            if (cookie.domain == targetDomain && cookie.path == targetPath) {
                cookieToOverwrite = cookie
            } else {
                cookiesToRemove.add(cookie)
            }
        }

        if (cookieToOverwrite == null && oldCookie != null) {
            if (oldCookie.domain == targetDomain || oldCookie.path == targetPath) {
                cookieToOverwrite = oldCookie
            } else {
                if (cookiesToRemove.none { it.id == oldCookie.id }) cookiesToRemove.add(oldCookie)
            }
        }

        val finalCookie = Cookie(
            key = key,
            value = value,
            domain = targetDomain,
            path = targetPath,
        )

        val updatedCookies = state.cookies
            .filterNot { cookie -> cookiesToRemove.any { it.id == cookie.id } }
            .toMutableList()

        cookieToOverwrite?.let { existing ->
            updatedCookies[updatedCookies.indexOfFirst { it.id == existing.id }] = finalCookie
        } ?: updatedCookies.add(finalCookie)

        cookiesToRemove.forEach { rm ->
            val rmUrl = "https://${rm.domain.ifEmpty { host }}${rm.path}"
            val expireString = buildString {
                append("${rm.key}=; Max-Age=0")
                if (rm.domain.isNotEmpty()) append("; Domain=${rm.domain}")
                append("; Path=${rm.path}")
            }
            cookieManager.setCookie(rmUrl, expireString)
        }

        val finalUrl = "https://${targetDomain.ifEmpty { host }}$targetPath"
        val finalCookieString = buildString {
            append("${finalCookie.key}=${finalCookie.value}")
            if (targetDomain.isNotEmpty()) append("; Domain=$targetDomain")
            append("; Path=$targetPath")
        }
        cookieManager.setCookie(finalUrl, finalCookieString)

        viewModelScope.launchIO {
            cookieIndexRepository.updateCookieIndex(
                host,
                CookieIndex(finalCookie.key, finalCookie.domain, finalCookie.path),
                cookiesToRemove.map {
                    CookieIndex(it.key, it.domain, it.path)
                },
            )
        }

        state.copy(cookies = updatedCookies)
    }

    data class Dialog(
        val key: String = "",
        val value: String = "",
        val domain: String = "",
        val path: String = "",
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
                    cookies.filter { it.key.contains(query, ignoreCase = true) }
                }
        }
    }
}

data class Cookie(
    val key: String,
    val value: String,
    val domain: String,
    val path: String,
) {
    val id get() = "$domain$path$$key"
}

private fun Cookie.conflicts(targetDomain: String, targetPath: String): Boolean {
    val domainConflict = (targetDomain.isEmpty() && domain.isEmpty()) || (
        targetDomain.isNotEmpty() && domain.isNotEmpty() &&
            (targetDomain.endsWith(domain) || domain.endsWith(targetDomain))
        )
    val pathConflict = targetPath.startsWith(path) || path.startsWith(targetPath)

    return domainConflict && pathConflict
}
