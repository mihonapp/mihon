package eu.kanade.presentation.browse

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.SwipeRefresh
import eu.kanade.presentation.manga.components.DotSeparatorNoSpaceText
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.padding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.secondaryItemAlpha
import eu.kanade.presentation.util.topSmallPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun ExtensionScreen(
    presenter: ExtensionsPresenter,
    contentPadding: PaddingValues,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    SwipeRefresh(
        refreshing = presenter.isRefreshing,
        onRefresh = onRefresh,
        enabled = !presenter.isLoading,
    ) {
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(
                textResource = R.string.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                ExtensionContent(
                    state = presenter,
                    contentPadding = contentPadding,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                )
            }
        }
    }
}

@Composable
private fun ExtensionContent(
    state: ExtensionsState,
    contentPadding: PaddingValues,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
) {
    var trustState by remember { mutableStateOf<Extension.Untrusted?>(null) }

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        items(
            items = state.items,
            contentType = {
                when (it) {
                    is ExtensionUiModel.Header -> "header"
                    is ExtensionUiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is ExtensionUiModel.Header -> "extensionHeader-${it.hashCode()}"
                    is ExtensionUiModel.Item -> "extension-${it.hashCode()}"
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
                                is Extension.Installed -> onOpenExtension(it)
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
private fun ExtensionItem(
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
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                    )
                }

                val padding by animateDpAsState(targetValue = if (idle) 0.dp else 8.dp)
                ExtensionIcon(
                    extension = extension,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(padding),
                )
            }
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
            installStep = installStep,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExtensionItemContent(
    extension: Extension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = MaterialTheme.padding.medium),
    ) {
        Text(
            text = extension.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Won't look good but it's not like we can ellipsize overflowing content
        FlowRow(
            modifier = Modifier.secondaryItemAlpha(),
            mainAxisSpacing = 4.dp,
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                if (extension is Extension.Installed && extension.lang.isNotEmpty()) {
                    Text(
                        text = LocaleHelper.getSourceDisplayName(extension.lang, LocalContext.current),
                    )
                }

                if (extension.versionName.isNotEmpty()) {
                    Text(
                        text = extension.versionName,
                    )
                }

                val warning = when {
                    extension is Extension.Untrusted -> R.string.ext_untrusted
                    extension is Extension.Installed && extension.isUnofficial -> R.string.ext_unofficial
                    extension is Extension.Installed && extension.isObsolete -> R.string.ext_obsolete
                    extension.isNsfw -> R.string.ext_nsfw_short
                    else -> null
                }
                if (warning != null) {
                    Text(
                        text = stringResource(warning).uppercase(),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!installStep.isCompleted()) {
                    DotSeparatorNoSpaceText()
                    Text(
                        text = when (installStep) {
                            InstallStep.Pending -> stringResource(R.string.ext_pending)
                            InstallStep.Downloading -> stringResource(R.string.ext_downloading)
                            InstallStep.Installing -> stringResource(R.string.ext_installing)
                            else -> error("Must not show non-install process text")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionItemActions(
    extension: Extension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
    onClickItemCancel: (Extension) -> Unit = {},
    onClickItemAction: (Extension) -> Unit = {},
) {
    val isIdle = installStep.isCompleted()
    Row(modifier = modifier) {
        if (isIdle) {
            TextButton(
                onClick = { onClickItemAction(extension) },
            ) {
                Text(
                    text = when (installStep) {
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
                        else -> error("Must not show install process text")
                    },
                )
            }
        } else {
            IconButton(onClick = { onClickItemCancel(extension) }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        }
    }
}

@Composable
private fun ExtensionHeader(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    ExtensionHeader(
        text = stringResource(textRes),
        modifier = modifier,
        action = action,
    )
}

@Composable
private fun ExtensionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.padding(horizontal = MaterialTheme.padding.medium),
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
private fun ExtensionTrustDialog(
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
