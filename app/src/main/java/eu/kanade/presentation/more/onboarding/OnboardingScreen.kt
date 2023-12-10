package eu.kanade.presentation.more.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.system.toast
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun OnboardingScreen(
    storagePreferences: StoragePreferences,
    uiPreferences: UiPreferences,
    onComplete: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val context = LocalContext.current
    val slideDistance = rememberSlideDistance()

    var currentStep by remember { mutableIntStateOf(0) }
    val steps: List<@Composable () -> Unit> = remember {
        listOf(
            { ThemeStep(uiPreferences = uiPreferences) },
            { StorageStep(storagePref = storagePreferences.baseStorageDirectory()) },
            // TODO: prompt for notification permissions when bumping target to Android 13
            { GuidesStep(onRestoreBackup = onRestoreBackup) },
        )
    }
    val isLastStep = currentStep == steps.size - 1

    BackHandler(enabled = currentStep != 0, onBack = { currentStep-- })

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
            if (isLastStep) {
                onComplete()
            } else {
                // TODO: this is kind of janky
                if (currentStep == 1 && !storagePreferences.baseStorageDirectory().isSet()) {
                    context.toast(MR.strings.onboarding_storage_selection_required)
                } else {
                    currentStep++
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .clip(MaterialTheme.shapes.small)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    materialSharedAxisX(
                        forward = targetState > initialState,
                        slideDistance = slideDistance,
                    )
                },
                label = "stepContent",
            ) {
                steps[it]()
            }
        }
    }
}
