package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Frame
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val screen = remember { ScreenBounds(0, 0, screenSize.width, screenSize.height) }
    val savedPlacement = remember(preferences.windowPlacement) {
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false)).sanitize(screen)
    }
    val navigator = remember {
        DesktopNavigator(preferences.lastDestination) { destination ->
            preferences = preferences.copy(lastDestination = destination)
            runtime.preferences.save(preferences)
        }
    }
    val windowState = rememberWindowState(
        placement = if (savedPlacement.maximized) {
            ComposeWindowPlacement.Maximized
        } else {
            ComposeWindowPlacement.Floating
        },
        position = WindowPosition(savedPlacement.x.dp, savedPlacement.y.dp),
        width = savedPlacement.width.dp,
        height = savedPlacement.height.dp,
    )
    var composeWindow: ComposeWindow? by remember { mutableStateOf(null) }
    val presenterScope = rememberCoroutineScope()
    val libraryPresenter = remember(runtime.library) { LibraryPresenter(runtime.library, presenterScope) }
    val libraryState by libraryPresenter.state.collectAsState()
    val mangaDetailState by libraryPresenter.detailState.collectAsState()
    val importController = remember(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot) {
        LibraryImportController(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot)
    }
    val importActions = remember(importController) {
        LibraryImportActions(
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }
    var importState: ImportActionState by remember { mutableStateOf(ImportActionState.Idle) }
    DisposableEffect(libraryPresenter) {
        onDispose(libraryPresenter::close)
    }

    Window(
        onCloseRequest = {
            composeWindow?.let { window ->
                preferences = preferences.copy(
                    windowPlacement = WindowPlacement(
                        x = window.x,
                        y = window.y,
                        width = window.width,
                        height = window.height,
                        maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
                    ).sanitize(screen),
                )
                runtime.preferences.save(preferences)
            }
            exitApplication()
        },
        state = windowState,
        title = "Mihon W",
    ) {
        SideEffect { composeWindow = window }
        MihonDesktopTheme(preferences.themeMode) {
            DesktopShell(
                selected = navigator.current,
                onDestinationSelected = navigator::navigate,
                libraryState = libraryState,
                mangaDetailState = mangaDetailState,
                onLibraryQueryChange = libraryPresenter::setQuery,
                onMangaSelected = libraryPresenter::selectManga,
                onBackFromMangaDetail = { libraryPresenter.selectManga(null) },
                onMangaDetailRetry = libraryPresenter::retryDetail,
                onImportBackup = {
                    presenterScope.launch {
                        importState = ImportActionState.Running
                        importState = importActions.chooseAndImportBackup()
                    }
                },
                onImportLocal = {
                    presenterScope.launch {
                        importState = ImportActionState.Running
                        importState = importActions.chooseAndImportLocal()
                    }
                },
                onLibraryRetry = libraryPresenter::retry,
            )
            ImportStateDialog(importState) { importState = ImportActionState.Idle }
        }
    }
}

@Composable
private fun ImportStateDialog(state: ImportActionState, onDismiss: () -> Unit) {
    when (state) {
        ImportActionState.Idle -> Unit
        ImportActionState.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing library") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Checking and importing the selected content…")
                }
            },
        )
        is ImportActionState.Completed -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
                title = { Text("Import complete") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Report ${result.reportId}")
                        Text("Manga: ${result.counts.mangaInserted} inserted, ${result.counts.mangaMerged} merged")
                        Text(
                            "Chapters: ${result.counts.chaptersInserted} inserted, ${result.counts.chaptersMerged} merged",
                        )
                        Text("Categories linked: ${result.counts.categoriesLinked}")
                        Text(
                            "Preferences: ${result.counts.preferencesImported} imported, " +
                                "${result.counts.preferencesSkipped} skipped",
                        )
                        if (result.skipCategories.isNotEmpty()) {
                            Text("Skip categories: ${result.skipCategories.joinToString()}")
                        }
                    }
                },
            )
        }
        is ImportActionState.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            title = { Text("Import rejected") },
            text = { Text("Category: ${state.category}") },
        )
        is ImportActionState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            title = { Text("Import failed") },
            text = { Text(state.message) },
        )
    }
}
