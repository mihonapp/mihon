package eu.kanade.presentation.reader.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    viewModel: ReaderSettingsViewModel,
    onOpacityPreview: (Int?) -> Unit = {},
) {
    val tabTitles = listOf(
        stringResource(MR.strings.pref_category_reading_mode),
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.custom_filter),
    )
    val pagerState = rememberPagerState { tabTitles.size }

    // While a live-preview control (the panel opacity slider) is actively being dragged, the
    // settings sheet itself fades out — same idea as the color filter tab's window-dim trick
    // below, but scoped to just the moment of dragging instead of a whole tab.
    var dialogContentHidden by remember { mutableStateOf(false) }
    val dialogAlpha by animateFloatAsState(
        targetValue = if (dialogContentHidden) 0f else 1f,
        animationSpec = tween(150),
        label = "settingsDialogAlpha",
    )

    BoxWithConstraints {
        TabbedDialog(
            modifier = Modifier
                .heightIn(max = maxHeight * 0.75f)
                .alpha(dialogAlpha),
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
                    0 -> ReadingModePage(
                        viewModel,
                        onHideMenus = {
                            dialogContentHidden = true
                            window?.setDimAmount(0f)
                            onHideMenus()
                        },
                        onShowMenus = {
                            dialogContentHidden = false
                            window?.setDimAmount(0.5f)
                            onShowMenus()
                        },
                        onOpacityPreview = onOpacityPreview,
                    )
                    1 -> GeneralPage(viewModel)
                    2 -> ColorFilterPage(viewModel)
                }
            }
        }
    }
}
