package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.ColorPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CustomThemeEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val uiPreferences = Injekt.get<UiPreferences>()
        val handleBack = LocalBackPress.current

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.theme_custom_editor),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                item {
                    ColorPreferenceWidget(
                        title = stringResource(MR.strings.color_accent),
                        preference = uiPreferences.customColorAccent,
                    )
                }
                item {
                    ColorPreferenceWidget(
                        title = stringResource(MR.strings.color_on),
                        preference = uiPreferences.customColorOn,
                    )
                }
                item {
                    ColorPreferenceWidget(
                        title = stringResource(MR.strings.color_surface_bg),
                        preference = uiPreferences.customColorSurface,
                    )
                }
            }
        }
    }
}
