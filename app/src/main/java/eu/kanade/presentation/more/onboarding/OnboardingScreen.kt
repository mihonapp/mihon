package eu.kanade.presentation.more.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.domain.ui.UiPreferences
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun OnboardingScreen(
    storagePreferences: StoragePreferences,
    uiPreferences: UiPreferences,
    onComplete: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val steps: List<@Composable () -> Unit> = listOf(
        { ThemeStep(uiPreferences = uiPreferences) },
        { StorageStep(storagePref = storagePreferences.baseStorageDirectory()) },
        // TODO: prompt for notification permissions when bumping target to Android 13
    )
    val isLastStep = currentStep == steps.size - 1
    val slideDistance = rememberSlideDistance()

    InfoScreen(
        icon = Icons.Outlined.RocketLaunch,
        headingText = stringResource(MR.strings.onboarding_heading),
        subtitleText = stringResource(MR.strings.onboarding_description),
        acceptText = stringResource(
            if (isLastStep) {
                MR.strings.onboarding_action_finish
            } else {
                MR.strings.onboarding_action_next
            },
        ),
        onAcceptClick = {
            if (!isLastStep) {
                currentStep++
            } else {
                onComplete()
            }
        },
        rejectText = stringResource(MR.strings.onboarding_action_skip),
        onRejectClick = onComplete,
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                materialSharedAxisX(
                    forward = true,
                    slideDistance = slideDistance,
                )
            },
            label = "stepContent",
        ) {
            steps[it]()
        }
    }
}
