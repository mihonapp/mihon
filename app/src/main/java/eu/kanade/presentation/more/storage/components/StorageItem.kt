package eu.kanade.presentation.more.storage.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.tachiyomi.util.storage.toSize
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

@Composable
fun StorageItem(
    item: StorageData,
    modifier: Modifier = Modifier,
    onDelete: (Boolean) -> Unit,
    onClickCover: () -> Unit,
) {
    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            MangaCover.Square(
                modifier = Modifier.height(48.dp),
                data = item.manga.asMangaCover(),
                contentDescription = item.manga.title,
                onClick = onClickCover,
            )
            Column(
                modifier = Modifier.weight(1f),
                content = {
                    Text(
                        text = item.manga.title,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            Box(
                                modifier = Modifier
                                    .background(item.color, CircleShape)
                                    .size(12.dp),
                            )
                            Spacer(Modifier.width(MaterialTheme.padding.small))
                            Text(
                                text = item.size.toSize(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = MaterialTheme.padding.small / 2)
                                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .size(MaterialTheme.padding.small / 2),
                            )
                            Text(
                                text = pluralStringResource(
                                    MR.plurals.manga_num_chapters,
                                    count = item.chapterCount,
                                    item.chapterCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                },
            )
            IconButton(
                onClick = {
                    showDeleteDialog = true
                },
                content = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                    )
                },
            )
        },
    )

    if (showDeleteDialog) {
        ItemDeleteDialog(
            manga = item.manga,
            onDismissRequest = { showDeleteDialog = false },
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ItemDeleteDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var removeFromLibrary by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete(removeFromLibrary)
                    onDismissRequest()
                },
                content = {
                    Text(text = stringResource(MR.strings.action_ok))
                },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = {
                    Text(text = stringResource(MR.strings.action_cancel))
                },
            )
        },
        title = {
            Text(
                text = stringResource(
                    if (manga.isLocal()) MR.strings.delete_local_manga else MR.strings.delete_downloads_for_manga,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(MR.strings.delete_manga_confirm, manga.title))

                LabeledCheckbox(
                    label = stringResource(MR.strings.remove_from_library),
                    checked = removeFromLibrary,
                    onCheckedChange = { removeFromLibrary = it },
                )
            }
        },
    )
}
