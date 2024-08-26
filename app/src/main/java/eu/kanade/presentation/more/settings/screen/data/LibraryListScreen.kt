package eu.kanade.presentation.more.settings.screen.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BaseMangaListItem(
    manga: Manga,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .fillMaxHeight(),
            data = manga,
        )

        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.medium),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

class LibraryListScreen : Screen() {

    companion object {
        const val TITLE = "Library List"
    }

    private fun escapeCsvField(field: String): String {
        return field.replace("\"", "\"\"").replace("\r\n", "\n").replace("\r", "\n")
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val getFavorites: GetFavorites = Injekt.get()

        val favoritesFlow = remember { flow { emit(getFavorites.await()) } }
        val favoritesState by favoritesFlow.collectAsState(emptyList())

        var showDialog by remember { mutableStateOf(false) }

        // Declare the selection states
        var titleSelected by remember { mutableStateOf(true) }
        var authorSelected by remember { mutableStateOf(true) }
        var artistSelected by remember { mutableStateOf(true) }

        val coroutineScope = rememberCoroutineScope()

        // Setup the activity result launcher to handle the file save
        val saveFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                coroutineScope.launch {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        // Prepare CSV data
                        val csvData = buildString {
                            favoritesState.forEach { manga ->
                                val title = if (titleSelected) escapeCsvField(manga.title) else ""
                                val author = if (authorSelected) escapeCsvField(manga.author ?: "") else ""
                                val artist = if (artistSelected) escapeCsvField(manga.artist ?: "") else ""
                                val row = listOf(title, author, artist).filter {
                                    it.isNotEmpty()
                                }.joinToString(",") { "\"$it\"" }
                                appendLine(row)
                            }
                        }
                        // Write CSV data to output stream
                        outputStream.write(csvData.toByteArray())
                        outputStream.flush()
                    }
                }
            }
        }

        if (showDialog) {
            ColumnSelectionDialog(
                onDismissRequest = { showDialog = false },
                onConfirm = { selectedTitle, selectedAuthor, selectedArtist ->
                    titleSelected = selectedTitle
                    authorSelected = selectedAuthor
                    artistSelected = selectedArtist

                    // Launch the save document intent
                    saveFileLauncher.launch("manga_list.csv")
                    showDialog = false
                },
                isTitleSelected = titleSelected,
                isAuthorSelected = authorSelected,
                isArtistSelected = artistSelected,
            )
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = Icons.Default.Save,
                                    onClick = { showDialog = true },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { contentPadding ->

            if (favoritesState.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.empty_screen,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(8.dp),
            ) {
                items(favoritesState) { manga ->
                    BaseMangaListItem(
                        manga = manga,
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnSelectionDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean, Boolean, Boolean) -> Unit,
    isTitleSelected: Boolean,
    isAuthorSelected: Boolean,
    isArtistSelected: Boolean,
) {
    var titleSelected by remember { mutableStateOf(isTitleSelected) }
    var authorSelected by remember { mutableStateOf(isAuthorSelected) }
    var artistSelected by remember { mutableStateOf(isArtistSelected) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Select Fields")
        },
        text = {
            Column {
                // Title checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = titleSelected,
                        onCheckedChange = { checked ->
                            titleSelected = checked
                            if (!checked) {
                                authorSelected = false
                                artistSelected = false
                            }
                        },
                    )
                    Text(text = stringResource(MR.strings.title))
                }

                // Author checkbox, disabled if Title is not selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = authorSelected,
                        onCheckedChange = { authorSelected = it },
                        enabled = titleSelected,
                    )
                    Text(text = "Author")
                }

                // Artist checkbox, disabled if Title is not selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = artistSelected,
                        onCheckedChange = { artistSelected = it },
                        enabled = titleSelected,
                    )
                    Text(text = "Artist")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(titleSelected, authorSelected, artistSelected)
                    onDismissRequest()
                },
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
