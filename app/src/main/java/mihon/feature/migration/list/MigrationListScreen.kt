package mihon.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchRoute
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.serialization.Serializable
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationMangaDialog
import mihon.feature.migration.list.components.MigrationProgressDialog
import mihon.navigation.util.LocalBackStack
import tachiyomi.i18n.MR

@Serializable
data class MigrationListRoute(
    val mangaIds: Collection<Long>,
    val extraSearchQuery: String?,
) : NavKey

@Composable
fun MigrationListScreen(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
) {
    val backStack = LocalBackStack.current
    val viewModel =
        assistedMetroViewModel<MigrationListViewModel, MigrationListViewModel.Factory> {
            create(mangaIds = mangaIds, extraSearchQuery = extraSearchQuery)
        }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    ResultEffect<MatchOverrideEvent> {
        viewModel.useMangaForMigration(
            current = it.current,
            target = it.target,
            onMissingChapters = {
                context.toast(MR.strings.migrationListScreen_matchWithoutChapterToast, Toast.LENGTH_LONG)
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateBackEvent.collect {
            backStack.removeLastOrNull()
        }
    }
    MigrationListScreenContent(
        items = state.items,
        migrationComplete = state.migrationComplete,
        finishedCount = state.finishedCount,
        onItemClick = {
            backStack.add(MangaRoute(it.id, true))
        },
        onSearchManually = { migrationItem ->
            backStack.add(MigrateSearchRoute(migrationItem.manga.id))
        },
        onSkip = { viewModel.removeManga(it) },
        onMigrate = { viewModel.migrateNow(mangaId = it, replace = true) },
        onCopy = { viewModel.migrateNow(mangaId = it, replace = false) },
        openMigrationDialog = viewModel::showMigrateDialog,
    )

    when (val dialog = state.dialog) {
        is MigrationListViewModel.Dialog.Migrate -> {
            MigrationMangaDialog(
                onDismissRequest = viewModel::dismissDialog,
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = {
                    if (dialog.copy) {
                        viewModel.copyMangas()
                    } else {
                        viewModel.migrateMangas()
                    }
                },
            )
        }
        is MigrationListViewModel.Dialog.Progress -> {
            MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = viewModel::cancelMigrate,
            )
        }
        MigrationListViewModel.Dialog.Exit -> {
            MigrationExitDialog(
                onDismissRequest = viewModel::dismissDialog,
                exitMigration = backStack::removeLastOrNull,
            )
        }
        null -> Unit
    }

    BackHandler(true) {
        viewModel.showExitDialog()
    }
}

data class MatchOverrideEvent(val current: Long, val target: Long)
