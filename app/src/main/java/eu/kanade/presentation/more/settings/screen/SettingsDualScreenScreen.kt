package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object SettingsDualScreenScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_dual_screen_settings

    @Composable
    override fun getPreferences(): List<Preference> {
        val basePref = remember { Injekt.get<BasePreferences>() }
        val context = remember { Injekt.get<android.app.Application>() }
        
        val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
        val detectedDisplays = remember { 
            displayManager.displays
                .filter { it.displayId != Display.DEFAULT_DISPLAY }
                .map { it.displayId }
        }

        val displayOptions = listOf(-1) + detectedDisplays
        val displayLabels = displayOptions.associateWith { id ->
            if (id == -1) stringResource(MR.strings.label_auto) else "ID: $id"
        }

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = basePref.secondaryDisplayId(),
                entries = displayLabels.toImmutableMap(),
                title = stringResource(MR.strings.pref_secondary_display_id),
                subtitle = stringResource(MR.strings.pref_secondary_display_id_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = basePref.swapPresentationRotation(),
                title = stringResource(MR.strings.pref_swap_presentation_rotation),
                subtitle = stringResource(MR.strings.pref_swap_presentation_rotation_summary),
            ),
        )
    }
}
