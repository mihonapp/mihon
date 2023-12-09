package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.onboarding.OnboardingScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class OnboardingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val storagePreferences = remember { Injekt.get<StoragePreferences>() }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        OnboardingScreen(
            storagePreferences = storagePreferences,
            uiPreferences = uiPreferences,
            onComplete = {
                basePreferences.shownOnboardingFlow().set(true)
                navigator.pop()
            },
        )
    }
}
