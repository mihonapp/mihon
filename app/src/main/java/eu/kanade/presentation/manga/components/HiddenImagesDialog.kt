package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.HiddenImage
import eu.kanade.domain.manga.model.displayNameRes
import eu.kanade.domain.manga.model.next
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun HiddenImagesDialog(
    hiddenImages: List<HiddenImage>,
    onDismissRequest: () -> Unit,
    onRemove: (Long) -> Unit,
    onUpdateScope: (Long, HiddenImage.Scope) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.hidden_images_manage)) },
        text = {
            if (hiddenImages.isEmpty()) {
                Text(text = stringResource(MR.strings.hidden_images_none))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hiddenImages.size) { index ->
                        val item = hiddenImages[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = item.imageUrl?.takeIf { it.isNotBlank() }
                                    ?: item.normalizedImageUrl?.takeIf { it.isNotBlank() }
                                    ?: item.imageSha256?.takeIf { it.isNotBlank() }
                                    ?: "#${item.id}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { onUpdateScope(item.id, item.scope.next()) },
                                ) {
                                    Text(text = stringResource(item.scope.displayNameRes()))
                                }
                                TextButton(onClick = { onRemove(item.id) }) {
                                    Text(text = stringResource(MR.strings.action_remove))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
    )
}
