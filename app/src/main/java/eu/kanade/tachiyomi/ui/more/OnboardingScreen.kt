package eu.kanade.tachiyomi.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import eu.kanade.presentation.more.onboarding.OnboardingScreen
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataRoute
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.setting.SettingsDestination
import eu.kanade.tachiyomi.ui.setting.SettingsRoute
import eu.kanade.tachiyomi.ui.setting.addSettingsRoute
import kotlinx.serialization.Serializable
import mihon.app.di.appGraph
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Serializable
data object OnboardingRoute : NavKey

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val isTabletUi = isTabletUi()

    val basePreferences = remember { context.appGraph.basePreferences }
    val shownOnboardingFlow by basePreferences.shownOnboardingFlow.collectAsState()

    val finishOnboarding: () -> Unit = {
        basePreferences.shownOnboardingFlow.set(true)
        backStack.removeLastOrNull()
    }

    val restoreSettingKey = stringResource(SettingsDataRoute.restorePreferenceKeyString)

    BackHandler(enabled = !shownOnboardingFlow) {
        // Prevent exiting if onboarding hasn't been completed
    }

    OnboardingScreen(
        onComplete = finishOnboarding,
        onRestoreBackup = {
            finishOnboarding()
            SearchableSettings.highlightKey = restoreSettingKey
            backStack.addSettingsRoute(SettingsRoute(SettingsDestination.DataAndStorage), isTabletUi)
        },
    )
}
