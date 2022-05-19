package eu.kanade.presentation.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.SwipeRefreshIndicator
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionState
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun ExtensionScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: ExtensionsPresenter,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
    onLaunched: () -> Unit,
) {
    val state by presenter.state.collectAsState()
    val isRefreshing = presenter.isRefreshing

    SwipeRefresh(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        state = rememberSwipeRefreshState(isRefreshing),
        indicator = { s, trigger -> SwipeRefreshIndicator(s, trigger) },
        onRefresh = onRefresh,
    ) {
        when (state) {
            is ExtensionState.Initialized -> {
                ExtensionContent(
                    items = (state as ExtensionState.Initialized).list,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                    onLaunched = onLaunched,
                )
            }
            ExtensionState.Uninitialized -> {}
        }
    }
}

@Composable
fun ExtensionContent(
    items: List<ExtensionUiModel>,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onLaunched: () -> Unit,
) {
    var trustState by remember { mutableStateOf<Extension.Untrusted?>(null) }

    LazyColumn(
        contentPadding = WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
    ) {
        items(
            items = items,
            key = {
                when (it) {
                    is ExtensionUiModel.Header.Resource -> it.textRes
                    is ExtensionUiModel.Header.Text -> it.text
                    is ExtensionUiModel.Item -> it.key()
                }
            },
            contentType = {
                when (it) {
                    is ExtensionUiModel.Item -> "item"
                    else -> "header"
                }
            },
        ) { item ->
            when (item) {
                is ExtensionUiModel.Header.Resource -> {
                    val action: @Composable RowScope.() -> Unit =
                        if (item.textRes == R.string.ext_updates_pending) {
                            {
                                Button(onClick = { onClickUpdateAll() }) {
                                    Text(
                                        text = stringResource(R.string.ext_update_all),
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                            }
                        } else {
                            {}
                        }
                    ExtensionHeader(
                        textRes = item.textRes,
                        modifier = Modifier.animateItemPlacement(),
                        action = action,
                    )
                }
                is ExtensionUiModel.Header.Text -> {
                    ExtensionHeader(
                        text = item.text,
                        modifier = Modifier.animateItemPlacement(),
                    )
                }
                is ExtensionUiModel.Item -> {
                    ExtensionItem(
                        modifier = Modifier.animateItemPlacement(),
                        item = item,
                        onClickItem = {
                            when (it) {
                                is Extension.Available -> onInstallExtension(it)
                                is Extension.Installed -> {
                                    if (it.hasUpdate) {
                                        onUpdateExtension(it)
                                    } else {
                                        onOpenExtension(it)
                                    }
                                }
                                is Extension.Untrusted -> { trustState = it }
                            }
                        },
                        onLongClickItem = onLongClickItem,
                        onClickItemCancel = onClickItemCancel,
                        onClickItemAction = {
                            when (it) {
                                is Extension.Available -> onInstallExtension(it)
                                is Extension.Installed -> {
                                    if (it.hasUpdate) {
                                        onUpdateExtension(it)
                                    } else {
                                        onOpenExtension(it)
                                    }
                                }
                                is Extension.Untrusted -> { trustState = it }
                            }
                        },
                    )
                    LaunchedEffect(Unit) {
                        onLaunched()
                    }
                }
            }
        }
    }
    if (trustState != null) {
        ExtensionTrustDialog(
            onClickConfirm = {
                onTrustExtension(trustState!!)
                trustState = null
            },
            onClickDismiss = {
                onUninstallExtension(trustState!!)
                trustState = null
            },
            onDismissRequest = {
                trustState = null
            },
        )
    }
}

@Composable
fun ExtensionItem(
    modifier: Modifier = Modifier,
    item: ExtensionUiModel.Item,
    onClickItem: (Extension) -> Unit,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onClickItemAction: (Extension) -> Unit,
) {
    val (extension, installStep) = item
    BaseBrowseItem(
        modifier = modifier
            .combinedClickable(
                onClick = { onClickItem(extension) },
                onLongClick = { onLongClickItem(extension) },
            ),
        onClickItem = { onClickItem(extension) },
        onLongClickItem = { onLongClickItem(extension) },
        icon = {
            ExtensionIcon(extension = extension)
        },
        action = {
            ExtensionItemActions(
                extension = extension,
                installStep = installStep,
                onClickItemCancel = onClickItemCancel,
                onClickItemAction = onClickItemAction,
            )
        },
    ) {
        ExtensionItemContent(
            extension = extension,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ExtensionItemContent(
    extension: Extension,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val warning = remember(extension) {
        when {
            extension is Extension.Untrusted -> R.string.ext_untrusted
            extension is Extension.Installed && extension.isUnofficial -> R.string.ext_unofficial
            extension is Extension.Installed && extension.isObsolete -> R.string.ext_obsolete
            extension.isNsfw -> R.string.ext_nsfw_short
            else -> null
        }
    }

    Column(
        modifier = modifier.padding(start = horizontalPadding),
    ) {
        Text(
            text = extension.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (extension.lang.isNullOrEmpty().not()) {
                Text(
                    text = LocaleHelper.getSourceDisplayName(extension.lang, context),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (extension.versionName.isNotEmpty()) {
                Text(
                    text = extension.versionName,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (warning != null) {
                Text(
                    text = stringResource(id = warning).uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}

@Composable
fun ExtensionItemActions(
    extension: Extension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
    onClickItemCancel: (Extension) -> Unit = {},
    onClickItemAction: (Extension) -> Unit = {},
) {
    val isIdle = remember(installStep) {
        installStep == InstallStep.Idle || installStep == InstallStep.Error
    }
    Row(modifier = modifier) {
        TextButton(
            onClick = { onClickItemAction(extension) },
            enabled = isIdle,
        ) {
            Text(
                text = when (installStep) {
                    InstallStep.Pending -> stringResource(R.string.ext_pending)
                    InstallStep.Downloading -> stringResource(R.string.ext_downloading)
                    InstallStep.Installing -> stringResource(R.string.ext_installing)
                    InstallStep.Installed -> stringResource(R.string.ext_installed)
                    InstallStep.Error -> stringResource(R.string.action_retry)
                    InstallStep.Idle -> {
                        when (extension) {
                            is Extension.Installed -> {
                                if (extension.hasUpdate) {
                                    stringResource(R.string.ext_update)
                                } else {
                                    stringResource(R.string.action_settings)
                                }
                            }
                            is Extension.Untrusted -> stringResource(R.string.ext_trust)
                            is Extension.Available -> stringResource(R.string.ext_install)
                        }
                    }
                },
                style = LocalTextStyle.current.copy(
                    color = if (isIdle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceTint,
                ),
            )
        }
        if (isIdle.not()) {
            IconButton(onClick = { onClickItemCancel(extension) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
fun ExtensionHeader(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    ExtensionHeader(
        text = stringResource(id = textRes),
        modifier = modifier,
        action = action,
    )
}

@Composable
fun ExtensionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.header,
        )
        action()
    }
}

@Composable
fun ExtensionTrustDialog(
    onClickConfirm: () -> Unit,
    onClickDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.untrusted_extension))
        },
        text = {
            Text(text = stringResource(R.string.untrusted_extension_message))
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(R.string.ext_trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onClickDismiss) {
                Text(text = stringResource(R.string.ext_uninstall))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
