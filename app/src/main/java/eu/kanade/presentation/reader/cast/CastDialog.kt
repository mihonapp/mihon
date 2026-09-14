package eu.kanade.presentation.reader.cast

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.cast.CastController
import eu.kanade.tachiyomi.ui.reader.cast.CastDisplayInfo
import eu.kanade.tachiyomi.ui.reader.cast.CastWebInfo
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CastConnected
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.SettingsRemote
import mihon.icons.materialsymbols.rounded.Tv
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Target picker: lists the external displays that can show the reader and lets the user start the
 * embedded web receiver. [onOpenRemote] is invoked once a display cast has started so the caller can
 * swap this sheet for the remote.
 */
@Composable
fun CastDialog(
    onDismissRequest: () -> Unit,
    castController: CastController,
    onOpenRemote: () -> Unit,
) {
    val context = LocalContext.current
    val state by castController.state.collectAsState()
    val displays by castController.displays.collectAsState()
    val webInfo by castController.webInfo.collectAsState()

    LaunchedEffect(Unit) { castController.refreshDisplays() }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            CastHeader(
                active = state.active,
                targetName = state.targetName,
            )

            DisplaySection(
                displays = displays,
                onSelectDisplay = { display ->
                    if (castController.startDisplayCast(display.displayId)) {
                        context.toast(MR.strings.cast_started)
                        onOpenRemote()
                    } else {
                        context.toast(MR.strings.cast_display_error)
                    }
                },
                onOpenSettings = { context.openCastSettings() },
            )

            WebSection(
                webInfo = webInfo,
                onStart = {
                    castController.startWebCast()
                        .onSuccess { context.toast(MR.strings.cast_started) }
                        .onFailure { e ->
                            context.toast(context.stringResource(MR.strings.cast_web_error, e.message.orEmpty()))
                        }
                },
                onCopyUrl = { url -> context.copyToClipboard(url, url) },
                onOpenRemote = onOpenRemote,
            )

            if (state.active) {
                Button(
                    onClick = { castController.stopCasting() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.large),
                ) {
                    Text(text = stringResource(MR.strings.cast_action_stop))
                }
            }
        }
    }
}

@Composable
private fun CastHeader(
    active: Boolean,
    targetName: String?,
) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = stringResource(MR.strings.cast_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            if (active) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.CastConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = if (active && targetName != null) {
                    stringResource(MR.strings.cast_status_casting_to, targetName)
                } else {
                    stringResource(MR.strings.cast_status_not_casting)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
    }
}

@Composable
private fun DisplaySection(
    displays: List<CastDisplayInfo>,
    onSelectDisplay: (CastDisplayInfo) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        HeadingItem(MR.strings.cast_section_display)
        if (displays.isEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(
                    text = stringResource(MR.strings.cast_display_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(MR.strings.cast_display_help),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )
                OutlinedButton(onClick = onOpenSettings) {
                    Text(text = stringResource(MR.strings.cast_display_open_settings))
                }
            }
        } else {
            displays.forEach { display ->
                DisplayRow(
                    display = display,
                    onClick = { onSelectDisplay(display) },
                )
            }
        }
    }
}

@Composable
private fun DisplayRow(
    display: CastDisplayInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.large, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = display.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(MR.strings.cast_display_size, display.width, display.height),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
    }
}

@Composable
private fun WebSection(
    webInfo: CastWebInfo?,
    onStart: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenRemote: () -> Unit,
) {
    Column {
        HeadingItem(MR.strings.cast_section_web)
        Column(
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.cast_web_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )
            if (webInfo == null) {
                Button(onClick = onStart) {
                    Text(text = stringResource(MR.strings.cast_web_start))
                }
            } else {
                WebReceiverInfo(
                    webInfo = webInfo,
                    onCopyUrl = { onCopyUrl(webInfo.url) },
                    onOpenRemote = onOpenRemote,
                )
            }
        }
    }
}

@Composable
private fun WebReceiverInfo(
    webInfo: CastWebInfo,
    onCopyUrl: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    Text(
        text = stringResource(MR.strings.cast_web_open_url),
        style = MaterialTheme.typography.bodyMedium,
    )
    SelectionContainer {
        Text(
            text = webInfo.url,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = stringResource(MR.strings.cast_web_clients, webInfo.clientCount),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.secondaryItemAlpha(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        OutlinedButton(
            onClick = onCopyUrl,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(MR.strings.cast_web_copy_url))
        }
        Button(
            onClick = onOpenRemote,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.SettingsRemote,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(MR.strings.cast_action_remote))
        }
    }
}

/**
 * Opens the system screen where screen casting / wireless display can be started. Not every OEM
 * exposes the cast screen, so fall back to the generic wireless display screen and then to the main
 * settings screen.
 */
private fun Context.openCastSettings() {
    val actions = listOf(
        Settings.ACTION_CAST_SETTINGS,
        "android.settings.WIFI_DISPLAY_SETTINGS",
        Settings.ACTION_SETTINGS,
    )
    for (action in actions) {
        try {
            startActivity(Intent(action))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next, more generic screen.
        }
    }
}
