package mihon.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationMangaDialog
import mihon.feature.migration.list.components.MigrationProgressDialog
import tachiyomi.i18n.MR

class MigrationListScreen(private val mangaIds: Collection<Long>, private val extraSearchQuery: String?) : Screen() {

    private var matchOverride: Pair<Long, Long>? = null

    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<MigrationListViewModel>(
            factory = MigrationListViewModel.Factory,
            extras = CreationExtras {
                set(MigrationListViewModel.MANGA_IDS_KEY, mangaIds)
                set(MigrationListViewModel.EXTRA_SEARCH_QUERY_KEY, extraSearchQuery)
            },
        )
        val state by viewModel.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            viewModel.useMangaForMigration(
                current = current,
                target = target,
                onMissingChapters = {
                    context.toast(MR.strings.migrationListScreen_matchWithoutChapterToast, Toast.LENGTH_LONG)
                },
            )
            matchOverride = null
        }

        LaunchedEffect(viewModel) {
            viewModel.navigateBackEvent.collect {
                navigator.pop()
            }
        }
        MigrationListScreenContent(
            items = state.items,
            migrationComplete = state.migrationComplete,
            finishedCount = state.finishedCount,
            onItemClick = {
                navigator.push(MangaScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator push MigrateSearchScreen(migrationItem.manga.id)
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
                    exitMigration = navigator::pop,
                )
            }
            null -> Unit
        }

        BackHandler(true) {
            viewModel.showExitDialog()
        }
    }
}
