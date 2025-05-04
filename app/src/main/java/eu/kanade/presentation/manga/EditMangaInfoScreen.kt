package eu.kanade.presentation.manga

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.tachiyomi.ui.manga.edit.EditMangaInfoScreen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditMangaInfoScreen(
    state: EditMangaInfoScreen.State,
    navigateUp: () -> Unit,
    onUpdate: (Manga) -> Unit,
) {
    val manga = state.manga
    var editedManga by remember {
        mutableStateOf(
            manga.copy(
                customTitle = manga.customTitle ?: manga.title,
                customAuthor = manga.customAuthor ?: manga.author,
                customArtist = manga.customArtist ?: manga.artist,
                customDescription = manga.customDescription ?: manga.description,
                customThumbnailUrl = manga.customThumbnailUrl ?: manga.thumbnailUrl,
                customGenre = manga.customGenre ?: manga.genre,
                customStatus = manga.customStatus ?: manga.status,
            ),
        )
    }
    // Dropdown for publishing status
    var expanded by remember { mutableStateOf(false) }
    val statusOptions = listOf(
        MR.strings.ongoing,
        MR.strings.completed,
        MR.strings.licensed,
        MR.strings.publishing_finished,
        MR.strings.cancelled,
        MR.strings.on_hiatus,
        MR.strings.unknown,
    )
    val statusMap = mapOf(
        MR.strings.ongoing to 1L,
        MR.strings.completed to 2L,
        MR.strings.licensed to 3L,
        MR.strings.publishing_finished to 4L,
        MR.strings.cancelled to 5L,
        MR.strings.on_hiatus to 6L,
        MR.strings.unknown to 7L,
    )
    var selectedStatus by remember {
        mutableStateOf(
            statusOptions.first {
                statusMap[it] == editedManga.customStatus
            },
        )
    }
    // Text field for adding genres when pressing enter
    var newGenre by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = { topAppBarScrollBehavior ->
            AppBar(
                titleContent = {
                    AppBarTitle(
                        title = stringResource(MR.strings.action_edit_info),
                    )
                },
                navigateUp = {
                    onUpdate(editedManga)
                    navigateUp()
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ClearableTextField(
                value = editedManga.customTitle.orEmpty(),
                onValueChange = {
                    editedManga = editedManga.copy(customTitle = it)
                },
                label = { Text(text = stringResource(MR.strings.title)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ClearableTextField(
                value = editedManga.customAuthor.orEmpty(),
                onValueChange = {
                    editedManga = editedManga.copy(customAuthor = it)
                },
                label = { Text(text = stringResource(MR.strings.author)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ClearableTextField(
                value = editedManga.customArtist.orEmpty(),
                onValueChange = {
                    editedManga = editedManga.copy(customArtist = it)
                },
                label = { Text(text = stringResource(MR.strings.artist)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ClearableTextField(
                value = editedManga.customDescription.orEmpty(),
                onValueChange = {
                    editedManga = editedManga.copy(customDescription = it)
                },
                label = { Text(text = stringResource(MR.strings.description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            ClearableTextField(
                value = editedManga.customThumbnailUrl.orEmpty(),
                onValueChange = {
                    editedManga = editedManga.copy(customThumbnailUrl = it)
                },
                label = { Text(text = stringResource(MR.strings.thumbnail_url)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), // Add rounded border
            ) {
                Text(
                    text = stringResource(selectedStatus),
                    modifier = Modifier.padding(16.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                statusOptions.forEach { status ->
                    RadioMenuItem(
                        text = { Text(stringResource(status)) },
                        isChecked = selectedStatus == status,
                        onClick = {
                            selectedStatus = status
                            editedManga = editedManga.copy(customStatus = statusMap[status])
                            expanded = false
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(vertical = 12.dp)
                    .animateContentSize(animationSpec = spring())
                    .fillMaxWidth(),
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    editedManga.customGenre?.forEach {
                        InputChip(
                            onClick = {
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        editedManga = editedManga.copy(
                                            customGenre = editedManga.customGenre?.filter { genre ->
                                                genre != it
                                            },
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = null,
                                        Modifier.size(InputChipDefaults.AvatarSize),
                                    )
                                }
                            },
                            label = { Text(it) },
                            selected = false,
                            modifier = Modifier.padding(4.dp),
                        )
                    }

                    OutlinedTextField(
                        value = newGenre,
                        onValueChange = {
                            newGenre = it
                        },
                        label = { Text(text = stringResource(MR.strings.add_genre)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newGenre.isNotBlank()) {
                                    editedManga = editedManga.copy(
                                        customGenre = (editedManga.customGenre ?: emptyList()) + newGenre.trim(),
                                    )
                                    newGenre = ""
                                    keyboardController?.hide()
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(MR.strings.action_reset),
                    )
                }
            }
        },
        modifier = modifier,
    )
}
