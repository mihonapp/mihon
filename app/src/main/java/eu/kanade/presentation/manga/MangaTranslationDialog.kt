package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.displayName
import eu.kanade.presentation.components.message
import eu.kanade.tachiyomi.data.translation.TranslationError
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data class MangaTranslationUiState(
    val loading: Boolean = false,
    val title: String? = null,
    val description: String? = null,
    val source: TranslationLanguage? = null,
    val target: TranslationLanguage? = null,
    val error: TranslationError? = null,
    val errorDetail: String? = null,
)

@Composable
fun MangaTranslationDialog(
    state: MangaTranslationUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.translation_manga)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(MR.strings.translation_loading))
                        }
                    }
                    error != null -> {
                        Text(
                            text = error.message(state.errorDetail),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> {
                        val source = state.source
                        val target = state.target
                        if (source != null && target != null) {
                            Text(
                                text = "${source.displayName()} → ${target.displayName()}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        state.title?.let {
                            Text(
                                text = stringResource(MR.strings.translation_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(text = it, style = MaterialTheme.typography.titleMedium)
                        }
                        state.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = stringResource(MR.strings.translation_description),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_close))
            }
        },
        dismissButton = if (error != null) {
            {
                TextButton(onClick = onRetry) {
                    Text(stringResource(MR.strings.action_retry))
                }
            }
        } else {
            null
        },
    )
}
