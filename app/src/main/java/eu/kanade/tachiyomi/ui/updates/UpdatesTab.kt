package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.library.components.LockedCategoryOverlay
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.Event
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { UpdatesScreenModel() }
        val settingsScreenModel = rememberScreenModel { UpdatesSettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        val getCategories = remember { Injekt.get<GetCategories>() }
        val categories by getCategories.subscribe().collectAsState(initial = emptyList())
        val hasLockedCategories = remember(categories) { categories.any { it.locked } }
        val isTabUnlocked by SecureActivityDelegate.lockedTabsUnlocked.collectAsState()

        val tabNavigator = LocalTabNavigator.current
        val isCurrentTab = tabNavigator.current.key == key
        LaunchedEffect(isCurrentTab) {
            if (!isCurrentTab) SecureActivityDelegate.lockLockedTabs()
        }

        if (hasLockedCategories && !isTabUnlocked) {
            LockedCategoryOverlay(
                contentPadding = PaddingValues(0.dp),
                onUnlockRequest = {
                    scope.launch {
                        val activity = context as? FragmentActivity ?: return@launch
                        val success = activity.authenticate(
                            title = context.stringResource(MR.strings.unlock_app_title, context.stringResource(MR.strings.label_recent_updates)),
                        )
                        if (success) SecureActivityDelegate.unlockLockedTabs()
                    }
                },
            )
            return
        }

        UpdateScreen(
            state = state,
            snackbarHostState = screenModel.snackbarHostState,
            lastUpdated = screenModel.lastUpdated,
            onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onUpdateLibrary = screenModel::updateLibrary,
            onDownloadChapter = screenModel::downloadChapters,
            onMultiBookmarkClicked = screenModel::bookmarkUpdates,
            onMultiMarkAsReadClicked = screenModel::markUpdatesRead,
            onMultiDeleteClicked = screenModel::showConfirmDeleteChapters,
            onUpdateSelected = screenModel::toggleSelection,
            onOpenChapter = {
                val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                context.startActivity(intent)
            },
            onCalendarClicked = { navigator.push(UpcomingScreen()) },
            onFilterClicked = screenModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
        )

        val onDismissDialog = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is UpdatesScreenModel.Dialog.DeleteConfirmation -> {
                UpdatesDeleteConfirmationDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = { screenModel.deleteChapters(dialog.toDelete) },
                )
            }
            is UpdatesScreenModel.Dialog.FilterSheet -> {
                UpdatesFilterDialog(
                    onDismissRequest = onDismissDialog,
                    screenModel = settingsScreenModel,
                )
            }
            null -> {}
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    Event.InternalError -> screenModel.snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.internal_error),
                    )
                    is Event.LibraryUpdateTriggered -> {
                        val msg = if (event.started) {
                            MR.strings.updating_library
                        } else {
                            MR.strings.update_already_running
                        }
                        screenModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                    }
                }
            }
        }

        LaunchedEffect(state.selectionMode) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
        DisposableEffect(Unit) {
            screenModel.resetNewUpdatesCount()

            onDispose {
                screenModel.resetNewUpdatesCount()
            }
        }
    }
}
