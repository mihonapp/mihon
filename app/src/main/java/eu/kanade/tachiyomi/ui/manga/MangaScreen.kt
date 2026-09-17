package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceRoute
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchRoute
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryRoute
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesRoute
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsDestination
import eu.kanade.tachiyomi.ui.setting.SettingsRoute
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewRoute
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import logcat.LogPriority
import mihon.feature.migration.config.MigrationConfigRoute
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data class MangaRoute(
    val mangaId: Long,
    val fromSource: Boolean = false,
) : NavKey

@Composable
fun MangaScreen(
    mangaId: Long,
    fromSource: Boolean,
) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val viewModel =
        assistedMetroViewModel<MangaViewModel, MangaViewModel.Factory> {
            create(mangaId = mangaId, isFromSource = fromSource)
        }

    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is MangaViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as MangaViewModel.State.Success
    val isHttpSource = remember { successState.source is HttpSource }

    LaunchedEffect(successState.manga, viewModel.source) {
        if (isHttpSource) {
            try {
                withIOContext {
                    // TODO(nav): assist
                    // assistUrl = getMangaUrl(viewModel.manga, viewModel.source)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
            }
        }
    }

    MangaScreen(
        state = successState,
        snackbarHostState = viewModel.snackbarHostState,
        nextUpdate = successState.manga.expectedNextUpdate,
        isTabletUi = isTabletUi(),
        chapterSwipeStartAction = viewModel.chapterSwipeStartAction,
        chapterSwipeEndAction = viewModel.chapterSwipeEndAction,
        navigateUp = backStack::removeLastOrNull,
        onChapterClicked = { openChapter(context, it) },
        onDownloadChapter = viewModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
        onAddToLibraryClicked = {
            viewModel.toggleFavorite()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onWebViewClicked = {
            openMangaInWebView(
                backStack,
                viewModel.manga,
                viewModel.source,
            )
        }.takeIf { isHttpSource },
        onWebViewLongClicked = {
            copyMangaUrl(
                context,
                viewModel.manga,
                viewModel.source,
            )
        }.takeIf { isHttpSource },
        onTrackingClicked = {
            if (!successState.hasLoggedInTrackers) {
                backStack.add(SettingsRoute(SettingsDestination.Tracking))
            } else {
                viewModel.showTrackDialog()
            }
        },
        onTagSearch = { scope.launch { performGenreSearch(backStack, it, viewModel.source!!) } },
        onFilterButtonClicked = viewModel::showSettingsDialog,
        onRefresh = viewModel::fetchAllFromSource,
        onContinueReading = { continueReading(context, viewModel.getNextUnreadChapter()) },
        onSearch = { query, global -> scope.launch { performSearch(backStack, query, global) } },
        onCoverClicked = viewModel::showCoverDialog,
        onShareClicked = { shareManga(context, viewModel.manga, viewModel.source) }.takeIf { isHttpSource },
        onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
        onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
        onEditFetchIntervalClicked = viewModel::showSetFetchIntervalDialog.takeIf {
            successState.manga.favorite
        },
        onMigrateClicked = {
            backStack.add(MigrationConfigRoute(successState.manga.id))
            Unit
        }.takeIf { successState.manga.favorite },
        onEditNotesClicked = { backStack.add(MangaNotesRoute(manga = successState.manga)) },
        onMultiBookmarkClicked = viewModel::bookmarkChapters,
        onMultiMarkAsReadClicked = viewModel::markChaptersRead,
        onMarkPreviousAsReadClicked = viewModel::markPreviousChapterRead,
        onMultiDeleteClicked = viewModel::showDeleteChapterDialog,
        onChapterSwipe = viewModel::chapterSwipe,
        onChapterSelected = viewModel::toggleSelection,
        onAllChapterSelected = viewModel::toggleAllSelection,
        onInvertSelection = viewModel::invertSelection,
    )

    var showScanlatorsDialog by remember { mutableStateOf(false) }

    val onDismissRequest = { viewModel.dismissDialog() }
    when (val dialog = successState.dialog) {
        null -> {}
        is MangaViewModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = { backStack.add(CategoryRoute) },
                onConfirm = { include, _ ->
                    viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                },
            )
        }
        is MangaViewModel.Dialog.DeleteChapters -> {
            DeleteChaptersDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    viewModel.toggleAllSelection(false)
                    viewModel.deleteChapters(dialog.chapters)
                },
            )
        }

        is MangaViewModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                duplicates = dialog.duplicates,
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                onOpenManga = { backStack.add(MangaRoute(it.id)) },
                onMigrate = { viewModel.showMigrateDialog(it) },
            )
        }

        is MangaViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                current = dialog.current,
                target = dialog.target,
                // Initiated from the context of [dialog.target] so we show [dialog.current].
                onClickTitle = { backStack.add(MangaRoute(dialog.current.id)) },
                onDismissRequest = onDismissRequest,
            )
        }
        MangaViewModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
            onDismissRequest = onDismissRequest,
            manga = successState.manga,
            onDownloadFilterChanged = viewModel::setDownloadedFilter,
            onUnreadFilterChanged = viewModel::setUnreadFilter,
            onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
            onSortModeChanged = viewModel::setSorting,
            onDisplayModeChanged = viewModel::setDisplayMode,
            onSetAsDefault = viewModel::setCurrentSettingsAsDefault,
            onResetToDefault = viewModel::resetToDefaultSettings,
            scanlatorFilterActive = successState.scanlatorFilterActive,
            onScanlatorFilterClicked = { showScanlatorsDialog = true },
        )
        MangaViewModel.Dialog.TrackSheet -> {
            // TODO(nav): sheets
            // NavigatorAdaptiveSheet(
            //     screen = TrackInfoDialogHomeScreen(
            //         mangaId = successState.manga.id,
            //         mangaTitle = successState.manga.title,
            //         sourceId = successState.source.id,
            //     ),
            //     enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
            //     onDismissRequest = onDismissRequest,
            // )
        }
        MangaViewModel.Dialog.FullCover -> {
            val vm =
                assistedMetroViewModel<MangaCoverViewModel, MangaCoverViewModel.Factory> {
                    create(mangaId = mangaId)
                }
            val manga by vm.state.collectAsStateWithLifecycle()
            if (manga != null) {
                val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                    if (it == null) return@rememberLauncherForActivityResult
                    vm.editCover(context, it)
                }
                MangaCoverDialog(
                    manga = manga!!,
                    snackbarHostState = vm.snackbarHostState,
                    isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                    onShareClick = { vm.shareCover(context) },
                    onSaveClick = { vm.saveCover(context) },
                    onEditClick = {
                        when (it) {
                            EditCoverAction.EDIT -> getContent.launch("image/*")
                            EditCoverAction.DELETE -> vm.deleteCustomCover(context)
                        }
                    },
                    onDismissRequest = onDismissRequest,
                )
            } else {
                LoadingScreen(Modifier.systemBarsPadding())
            }
        }
        is MangaViewModel.Dialog.SetFetchInterval -> {
            SetIntervalDialog(
                interval = dialog.manga.fetchInterval,
                nextUpdate = dialog.manga.expectedNextUpdate,
                onDismissRequest = onDismissRequest,
                onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.manga, interval) }
                    .takeIf { viewModel.isUpdateIntervalEnabled },
            )
        }
    }

    if (showScanlatorsDialog) {
        ScanlatorFilterDialog(
            availableScanlators = successState.availableScanlators,
            excludedScanlators = successState.excludedScanlators,
            onDismissRequest = { showScanlatorsDialog = false },
            onConfirm = viewModel::setExcludedScanlators,
        )
    }
}

private fun continueReading(context: Context, unreadChapter: Chapter?) {
    if (unreadChapter != null) openChapter(context, unreadChapter)
}

private fun openChapter(context: Context, chapter: Chapter) {
    context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
}

private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
    val manga = manga_ ?: return null
    val source = source_ as? HttpSource ?: return null

    return try {
        source.getMangaUrl(manga.toSManga())
    } catch (e: Exception) {
        null
    }
}

private fun openMangaInWebView(backStack: NavBackStack<NavKey>, manga_: Manga?, source_: Source?) {
    getMangaUrl(manga_, source_)?.let { url ->
        backStack.add(
            WebViewRoute(
                url = url,
                initialTitle = manga_?.title,
                sourceId = source_?.id,
            )
        )
    }
}

private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
    try {
        getMangaUrl(manga_, source_)?.let { url ->
            val intent = url.toUri().toShareIntent(context, type = "text/plain")
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        context.toast(e.message)
    }
}

/**
 * Perform a search using the provided query.
 *
 * @param query the search query to the parent controller
 */
private suspend fun performSearch(backStack: NavBackStack<NavKey>, query: String, global: Boolean) {
    if (global) {
        backStack.add(GlobalSearchRoute(query))
        return
    }

    if (backStack.size < 2) {
        return
    }

    // TODO(homescreen): search
    // when (val previousController = navigator.items[navigator.size - 2]) {
    //     is HomeScreen -> {
    //         navigator.pop()
    //         previousController.search(query)
    //     }
    //     is BrowseSourceScreen -> {
    //         navigator.pop()
    //         previousController.search(query)
    //     }
    // }
}

/**
 * Performs a genre search using the provided genre name.
 *
 * @param genreName the search genre to the parent controller
 */
private suspend fun performGenreSearch(backStack: NavBackStack<NavKey>, genreName: String, source: Source) {
    if (backStack.size < 2) {
        return
    }

    val previousController = backStack[backStack.size - 2]
    if (previousController is BrowseSourceRoute && source is HttpSource) {
        backStack.removeLastOrNull()
        // TODO(nav): event
        // previousController.searchGenre(genreName)
    } else {
        performSearch(backStack, genreName, global = false)
    }
}

/**
 * Copy Manga URL to Clipboard
 */
private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
    val manga = manga_ ?: return
    val source = source_ as? HttpSource ?: return
    val url = source.getMangaUrl(manga.toSManga())
    context.copyToClipboard(url, url)
}
