package eu.kanade.presentation.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.model.display
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.EmptyScreenAction
import eu.kanade.presentation.components.LibraryBottomActionMenu
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView

@Composable
fun LibraryScreen(
    presenter: LibraryPresenter,
    onMangaClicked: (Long) -> Unit,
    onContinueReadingClicked: (LibraryManga) -> Unit,
    onGlobalSearchClicked: () -> Unit,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    onDeleteClicked: () -> Unit,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: (Category?) -> Boolean,
    onClickOpenRandomManga: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = { scrollBehavior ->
            val title by presenter.getToolbarTitle()
            val tabVisible = presenter.tabVisibility && presenter.categories.size > 1
            LibraryToolbar(
                state = presenter,
                title = title,
                incognitoMode = !tabVisible && presenter.isIncognitoMode,
                downloadedOnlyMode = !tabVisible && presenter.isDownloadOnly,
                onClickUnselectAll = onClickUnselectAll,
                onClickSelectAll = onClickSelectAll,
                onClickInvertSelection = onClickInvertSelection,
                onClickFilter = onClickFilter,
                onClickRefresh = { onClickRefresh(null) },
                onClickOpenRandomManga = onClickOpenRandomManga,
                scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
            )
        },
        bottomBar = {
            LibraryBottomActionMenu(
                visible = presenter.selectionMode,
                onChangeCategoryClicked = onChangeCategoryClicked,
                onMarkAsReadClicked = onMarkAsReadClicked,
                onMarkAsUnreadClicked = onMarkAsUnreadClicked,
                onDownloadClicked = onDownloadClicked.takeIf { presenter.selection.fastAll { !it.manga.isLocal() } },
                onDeleteClicked = onDeleteClicked,
            )
        },
    ) { paddingValues ->
        if (presenter.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        val contentPadding = TachiyomiBottomNavigationView.withBottomNavPadding(paddingValues)
        if (presenter.searchQuery.isNullOrEmpty() && presenter.isLibraryEmpty) {
            val handler = LocalUriHandler.current
            EmptyScreen(
                textResource = R.string.information_empty_library,
                modifier = Modifier.padding(contentPadding),
                actions = listOf(
                    EmptyScreenAction(
                        stringResId = R.string.getting_started_guide,
                        icon = Icons.Outlined.HelpOutline,
                        onClick = { handler.openUri("https://tachiyomi.org/help/guides/getting-started") },
                    ),
                ),
            )
            return@Scaffold
        }

        LibraryContent(
            state = presenter,
            contentPadding = contentPadding,
            currentPage = { presenter.activeCategory },
            isLibraryEmpty = presenter.isLibraryEmpty,
            showPageTabs = presenter.tabVisibility,
            showMangaCount = presenter.mangaCountVisibility,
            onChangeCurrentPage = { presenter.activeCategory = it },
            onMangaClicked = onMangaClicked,
            onContinueReadingClicked = onContinueReadingClicked,
            onToggleSelection = { presenter.toggleSelection(it) },
            onToggleRangeSelection = {
                presenter.toggleRangeSelection(it)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onRefresh = onClickRefresh,
            onGlobalSearchClicked = onGlobalSearchClicked,
            getNumberOfMangaForCategory = { presenter.getMangaCountForCategory(it) },
            getDisplayModeForPage = { presenter.categories[it].display },
            getColumnsForOrientation = { presenter.getColumnsPreferenceForCurrentOrientation(it) },
            getLibraryForPage = { presenter.getMangaForCategory(page = it) },
            showDownloadBadges = presenter.showDownloadBadges,
            showUnreadBadges = presenter.showUnreadBadges,
            showLocalBadges = presenter.showLocalBadges,
            showLanguageBadges = presenter.showLanguageBadges,
            showContinueReadingButton = presenter.showContinueReadingButton,
            isIncognitoMode = presenter.isIncognitoMode,
            isDownloadOnly = presenter.isDownloadOnly,
        )
    }
}
