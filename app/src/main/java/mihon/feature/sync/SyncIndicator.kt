package mihon.feature.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CloudOff
import mihon.icons.materialsymbols.rounded.Done
import mihon.icons.materialsymbols.rounded.Sync
import mihon.sync.SyncScheduler
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Floating badge that makes the delay before a sync visible and interruptible.
 *
 * A silent wait leaves the user unsure anything will happen; a countdown they can tap answers both
 * "is it going to sync?" and "can I make it hurry?" without a settings trip. It shows only while
 * something is actually pending or running, so it stays out of the way the rest of the time.
 */
@Composable
fun SyncIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheduler = remember { context.appGraph.syncScheduler }
    val state by scheduler.state.collectAsState()

    var showPrompt by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state !is SyncScheduler.State.Idle,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(INDICATOR_SIZE)
                .clip(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (val current = state) {
                    is SyncScheduler.State.Pending -> Countdown(
                        secondsLeft = current.secondsLeft,
                        onClick = { showPrompt = true },
                    )

                    SyncScheduler.State.Running -> CircularProgressIndicator(
                        modifier = Modifier.size(ICON_SIZE),
                        strokeWidth = 2.dp,
                    )

                    SyncScheduler.State.Finished -> Icon(
                        imageVector = MaterialSymbols.Rounded.Done,
                        contentDescription = stringResource(MR.strings.sync_complete),
                        modifier = Modifier.size(ICON_SIZE),
                    )

                    SyncScheduler.State.Failed -> Icon(
                        imageVector = MaterialSymbols.Rounded.CloudOff,
                        contentDescription = stringResource(MR.strings.sync_error),
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.error,
                    )

                    SyncScheduler.State.Idle -> Unit
                }
            }
        }
    }

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            icon = {
                Icon(imageVector = MaterialSymbols.Rounded.Sync, contentDescription = null)
            },
            title = { Text(stringResource(MR.strings.sync_pending_title)) },
            text = { Text(stringResource(MR.strings.sync_pending_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPrompt = false
                        scheduler.syncNow()
                    },
                ) {
                    Text(stringResource(MR.strings.pref_sync_now))
                }
            },
            dismissButton = {
                // Waiting means letting the countdown run out, not calling the sync off.
                TextButton(onClick = { showPrompt = false }) {
                    Text(stringResource(MR.strings.sync_pending_wait))
                }
            },
        )
    }
}

@Composable
private fun Countdown(secondsLeft: Int, onClick: () -> Unit) {
    // Animated so the ring sweeps smoothly instead of stepping once a second.
    val progress by animateFloatAsState(
        targetValue = secondsLeft / SyncScheduler.COUNTDOWN_SECONDS.toFloat(),
        label = "syncCountdown",
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(ICON_SIZE),
            strokeWidth = 2.dp,
        )
        TextButton(onClick = onClick, modifier = Modifier.size(INDICATOR_SIZE)) {
            Text(
                text = secondsLeft.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val INDICATOR_SIZE = 44.dp
private val ICON_SIZE = 24.dp
