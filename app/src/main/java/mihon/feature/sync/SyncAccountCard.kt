package mihon.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.relativeTimeSpanString
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Cloud
import mihon.icons.materialsymbols.rounded.MoreVert
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The account, and what can be done with it, at the top of the sync settings.
 *
 * These are actions rather than settings — linking, syncing right away, reading the history,
 * looking at the storage used — so they are gathered here, leaving the list below to the choices
 * that shape the sync. Unlinking is the one to think twice about, so it sits in the menu.
 */
@Composable
fun SyncAccountCard(
    accountEmail: String,
    isLinked: Boolean,
    isEnabled: Boolean,
    lastSyncAt: Long,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The account is still known but its grant expired: it has to be linked again, not set up anew.
    val needsRelink = !isLinked && accountEmail.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.small,
                bottom = if (isLinked) MaterialTheme.padding.small else MaterialTheme.padding.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MaterialTheme.padding.medium),
                ) {
                    if (accountEmail.isNotBlank()) {
                        Text(
                            text = stringResource(MR.strings.pref_sync_group_account),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // An address has no space to break at, so a narrow screen cut it anywhere — or
                        // hid its end. Offering a break right after the @ keeps both halves readable.
                        Text(
                            text = accountEmail.replace("@", "@\u200B"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = stringResource(MR.strings.pref_sync_summary),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    val status = when {
                        needsRelink -> stringResource(MR.strings.sync_error_auth)
                        !isLinked -> null
                        // The time is kept in one piece: "just now" split across two lines reads as two things.
                        lastSyncAt > 0 -> stringResource(
                            MR.strings.pref_sync_last,
                            relativeTimeSpanString(lastSyncAt).replace(' ', '\u00A0'),
                        )
                        else -> stringResource(MR.strings.pref_sync_never)
                    }
                    if (status != null) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (needsRelink) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (isLinked || needsRelink) {
                    AccountMenu(onUnlink = onUnlink)
                }
            }

            Column(modifier = Modifier.padding(end = MaterialTheme.padding.small)) {
                if (isLinked) {
                    FilledTonalButton(
                        onClick = onSyncNow,
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(MR.strings.pref_sync_now))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onOpenHistory) {
                            Text(stringResource(MR.strings.history))
                        }
                        TextButton(onClick = onOpenStorage) {
                            Text(stringResource(MR.strings.pref_sync_group_storage))
                        }
                    }
                } else {
                    Button(onClick = onLink, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(MR.strings.pref_sync_sign_in))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountMenu(onUnlink: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.pref_sync_sign_out)) },
                onClick = {
                    expanded = false
                    onUnlink()
                },
            )
        }
    }
}
