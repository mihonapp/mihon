package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.collections.immutable.toImmutableMap

internal class DualScreenStep : OnboardingStep {

    override val isComplete: Boolean = true

    private val basePreferences: BasePreferences = Injekt.get()

    @Composable
    override fun Content() {
        val dualScreenEnabled by basePreferences.enableDualScreenMode().collectAsState()
        val secondaryDisplayId by basePreferences.secondaryDisplayId().collectAsState()
        val swapRotation by basePreferences.swapPresentationRotation().collectAsState()
        
        val context = remember { Injekt.get<android.app.Application>() }
        val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
        
        // Dynamically detect available displays excluding the default one to populate the selection list
        val detectedDisplays = remember { 
            displayManager.displays
                .filter { it.displayId != Display.DEFAULT_DISPLAY }
                .map { it.displayId }
        }

        val displayOptions = listOf(-1) + detectedDisplays
        val displayLabels = displayOptions.associateWith { id ->
            if (id == -1) stringResource(MR.strings.label_auto) else "ID: $id"
        }

        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_dual_screen_info),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SwitchPreferenceWidget(
                title = stringResource(MR.strings.pref_dual_screen_mode),
                subtitle = stringResource(MR.strings.pref_dual_screen_mode_summary),
                checked = dualScreenEnabled,
                onCheckedChanged = { basePreferences.enableDualScreenMode().set(it) },
            )

            if (dualScreenEnabled) {
                ListPreferenceWidget(
                    value = secondaryDisplayId,
                    title = stringResource(MR.strings.pref_secondary_display_id),
                    subtitle = stringResource(MR.strings.pref_secondary_display_id_summary),
                    icon = null,
                    entries = displayLabels.toImmutableMap(),
                    onValueChange = { basePreferences.secondaryDisplayId().set(it) },
                )

                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.pref_swap_presentation_rotation),
                    subtitle = stringResource(MR.strings.pref_swap_presentation_rotation_summary),
                    checked = swapRotation,
                    onCheckedChanged = { basePreferences.swapPresentationRotation().set(it) },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = stringResource(MR.strings.onboarding_dual_screen_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
