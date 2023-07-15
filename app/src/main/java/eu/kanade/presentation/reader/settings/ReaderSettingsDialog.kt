package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel

@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val tabTitles = listOf(
        stringResource(R.string.pref_category_reading_mode),
        stringResource(R.string.pref_category_general),
        stringResource(R.string.custom_filter),
    )
    val pagerState = rememberPagerState { tabTitles.size }

    TabbedDialog(
        onDismissRequest = {
            onDismissRequest()
            onShowMenus()
        },
        tabTitles = tabTitles,
        pagerState = pagerState,
    ) { page ->
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage == 2) {
                window?.setDimAmount(0f)
                onHideMenus()
            } else {
                window?.setDimAmount(0.5f)
                onShowMenus()
            }
        }

        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> ReadingModePage(screenModel)
                1 -> GeneralPage(screenModel)
                2 -> ColorFilterPage(screenModel)
            }
        }
    }
}
