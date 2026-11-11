package mihon.feature.protectedfolder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import mihon.domain.protectedfolder.service.PinHasher
import mihon.feature.protectedfolder.ProtectedFolderViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration

@Composable
fun ProtectedFolderPinContent(
    state: ProtectedFolderViewModel.State,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onForgotPin: () -> Unit,
    onUseBiometrics: (() -> Unit)?,
    navigateUp: () -> Unit,
) {
    val lockedOut = state.lockoutRemaining > Duration.ZERO
    val keysEnabled = !lockedOut && !state.busy

    Scaffold(
        topBar = { AppBar(title = null, navigateUp = navigateUp) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.padding.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    when (val stage = state.stage) {
                        is ProtectedFolderViewModel.Stage.CreatePin -> if (stage.firstEntry == null) {
                            MR.strings.protected_folder_create_pin
                        } else {
                            MR.strings.protected_folder_confirm_pin
                        }
                        else -> MR.strings.protected_folder_enter_pin
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(MaterialTheme.padding.large))
            PinDots(filled = state.input.length)
            Spacer(Modifier.height(MaterialTheme.padding.medium))

            Text(
                text = when {
                    lockedOut -> stringResource(
                        MR.strings.protected_folder_locked_out,
                        state.lockoutRemaining.toCountdown(),
                    )
                    state.message != null -> stringResource(state.message)
                    else -> ""
                },
                modifier = Modifier.heightIn(min = 48.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                    KeyRow {
                        row.forEach { digit ->
                            Key(text = digit.toString(), enabled = keysEnabled, onClick = { onDigit(digit) })
                        }
                    }
                }
                KeyRow {
                    Spacer(Modifier.size(KeySize))
                    Key(text = "0", enabled = keysEnabled, onClick = { onDigit(0) })
                    val delete = stringResource(MR.strings.action_delete)
                    Key(
                        text = "⌫",
                        enabled = keysEnabled && state.input.isNotEmpty(),
                        onClick = onBackspace,
                        modifier = Modifier.semantics { contentDescription = delete },
                    )
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.medium))
            if (state.stage == ProtectedFolderViewModel.Stage.EnterPin) {
                if (onUseBiometrics != null) {
                    TextButton(onClick = onUseBiometrics) {
                        Text(text = stringResource(MR.strings.protected_folder_use_biometrics))
                    }
                }
                // Stays enabled during a lockout: it is the only way back in for someone who forgot the PIN
                TextButton(onClick = onForgotPin, enabled = !state.busy) {
                    Text(text = stringResource(MR.strings.protected_folder_forgot_pin))
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
        repeat(PinHasher.PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (index < filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large)) {
        content()
    }
}

@Composable
private fun Key(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.size(KeySize),
        enabled = enabled,
        shape = CircleShape,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

private fun Duration.toCountdown(): String {
    return toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

private val KeySize = 72.dp
