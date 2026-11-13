package mihon.feature.protectedfolder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticateWithBiometrics
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isBiometricAuthenticationSupported
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.feature.protectedfolder.components.ProtectedFolderConfirmDialog
import mihon.feature.protectedfolder.components.ProtectedFolderForgotPinDialog
import mihon.feature.protectedfolder.components.ProtectedFolderOptionsDialog
import mihon.feature.protectedfolder.components.ProtectedFolderPinContent
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

class ProtectedFolderScreen(
    private val purpose: ProtectedFolderViewModel.Purpose = ProtectedFolderViewModel.Purpose.Open,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val viewModel = assistedMetroViewModel<ProtectedFolderViewModel, ProtectedFolderViewModel.Factory> {
            create(purpose)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val preferences = remember { context.appGraph.protectedFolderPreferences }
        val biometricsSupported = remember { context.isBiometricAuthenticationSupported() }

        if (state.stage == ProtectedFolderViewModel.Stage.Unlocked) {
            FolderContent(viewModel, state)
        } else {
            ProtectedFolderPinContent(
                state = state,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onForgotPin = viewModel::showForgotPinDialog,
                onUseBiometrics = {
                    scope.launch {
                        val authenticated = (context as FragmentActivity).authenticateWithBiometrics(
                            title = context.stringResource(MR.strings.protected_folder),
                            negativeButtonText = context.stringResource(MR.strings.protected_folder_use_pin),
                        )
                        if (authenticated) viewModel.onBiometricSuccess()
                    }
                    Unit
                }.takeIf {
                    purpose == ProtectedFolderViewModel.Purpose.Open && biometricsSupported &&
                        preferences.useBiometrics.get()
                },
                navigateUp = navigator::pop,
            )
        }

        when (val dialog = state.dialog) {
            ProtectedFolderViewModel.Dialog.ForgotPin -> {
                ProtectedFolderForgotPinDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onConfirm = viewModel::wipe,
                )
            }
            ProtectedFolderViewModel.Dialog.RemovePin -> {
                ProtectedFolderConfirmDialog(
                    title = stringResource(MR.strings.protected_folder_remove_pin),
                    message = stringResource(MR.strings.protected_folder_remove_pin_message),
                    onDismissRequest = viewModel::dismissDialog,
                    onConfirm = viewModel::removePin,
                )
            }
            is ProtectedFolderViewModel.Dialog.RemoveFromFolder -> {
                ProtectedFolderConfirmDialog(
                    title = stringResource(MR.strings.action_remove_from_protected_folder),
                    message = stringResource(MR.strings.protected_folder_remove_confirmation),
                    onDismissRequest = viewModel::dismissDialog,
                    onConfirm = { viewModel.removeFromFolder(dialog.mangaIds) },
                )
            }
            ProtectedFolderViewModel.Dialog.Options -> {
                ProtectedFolderOptionsDialog(
                    preferences = preferences,
                    biometricsSupported = biometricsSupported,
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            null -> {}
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is ProtectedFolderViewModel.Event.Message -> context.toast(event.message)
                    is ProtectedFolderViewModel.Event.Finished -> {
                        event.message?.let { context.toast(it) }
                        navigator.pop()
                    }
                }
            }
        }
    }

    @Composable
    private fun FolderContent(viewModel: ProtectedFolderViewModel, state: ProtectedFolderViewModel.State) {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.protected_folder),
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.protected_folder_options),
                                    onClick = viewModel::showOptionsDialog,
                                ),
                            ),
                        )
                    },
                    actionModeCounter = state.selection.size,
                    onCancelActionMode = viewModel::clearSelection,
                    actionModeActions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = MaterialSymbols.Rounded.SelectAll,
                                    onClick = viewModel::selectAll,
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_remove_from_protected_folder),
                                    onClick = viewModel::showRemoveFromFolderDialog,
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (state.items.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.protected_folder_empty,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }
            LibraryContent(
                categories = FolderCategories,
                searchQuery = null,
                selection = state.selection,
                contentPadding = contentPadding,
                currentPage = 0,
                hasActiveFilters = false,
                showPageTabs = false,
                onChangeCurrentPage = {},
                onClickManga = { navigator.push(MangaScreen(it)) },
                onContinueReadingClicked = null,
                onToggleSelection = { _, manga -> viewModel.toggleSelection(manga) },
                onToggleRangeSelection = { _, manga ->
                    viewModel.toggleSelection(manga)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                // Protected entries are updated with the rest of the library, without notifications
                onRefresh = { LibraryUpdateWorker.startNow(context.workManager) },
                onGlobalSearchClicked = {},
                getItemCountForCategory = { null },
                getDisplayMode = { viewModel.getDisplayMode() },
                getColumnsForOrientation = { viewModel.getColumnsForOrientation(it) },
                getItemsForCategory = { state.items },
            )
        }

        BackHandler(enabled = state.selection.isNotEmpty(), onBack = viewModel::clearSelection)
    }
}

/**
 * The library's grid shows its entries a category at a time, and the folder is shown as a single one.
 */
private val FolderCategories = listOf(Category(id = Long.MIN_VALUE, name = "", order = 0, flags = 0))
