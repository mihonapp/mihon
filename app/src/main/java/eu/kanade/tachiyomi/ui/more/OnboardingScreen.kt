package eu.kanade.tachiyomi.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.onboarding.OnboardingScreen
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.openSettings
import mihon.app.di.appGraph
import mihon.core.navigation.LocalNavigator
import mihon.presentation.core.util.isTabletUi
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val isTabletUi = isTabletUi()

    val basePreferences = remember { context.appGraph.basePreferences }
    val shownOnboardingFlow by basePreferences.shownOnboardingFlow.collectAsState()

    val finishOnboarding: () -> Unit = {
        basePreferences.shownOnboardingFlow.set(true)
        navigator.pop()
    }

    val restoreSettingKey = stringResource(SettingsDataScreen.restorePreferenceKeyString)

    BackHandler(enabled = !shownOnboardingFlow) {
        // Prevent exiting if onboarding hasn't been completed
    }

    OnboardingScreen(
        onComplete = finishOnboarding,
        onRestoreBackup = {
            finishOnboarding()
            SearchableSettings.highlightKey = restoreSettingKey
            navigator.openSettings(SettingsDataScreen, isTabletUi)
        },
    )
}
