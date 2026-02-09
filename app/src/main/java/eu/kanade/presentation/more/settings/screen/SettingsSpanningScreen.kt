package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSpanningScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_spanning

    @Composable
    override fun getPreferences(): List<Preference> {
        val basePref = remember { Injekt.get<BasePreferences>() }
        val readerPref = remember { Injekt.get<ReaderPreferences>() }
        val context = remember { Injekt.get<android.app.Application>() }

        val dualScreenEnabled by basePref.enableDualScreenMode().collectAsState()

        return mutableListOf<Preference>().apply {
            add(getReaderGroup(readerPref))
            add(getHingeGroup(readerPref))
            add(getDualScreenModeGroup(basePref, context, dualScreenEnabled))
        }
    }

    @Composable
    private fun getReaderGroup(readerPref: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.book_mode),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.sideBySideMode(),
                    title = stringResource(MR.strings.book_mode),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.autoEnableBookMode(),
                    title = stringResource(MR.strings.pref_auto_enable_book_mode),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.autoDisableBookMode(),
                    title = stringResource(MR.strings.pref_auto_disable_book_mode),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.autoAdjustHingeGap(),
                    title = stringResource(MR.strings.pref_auto_adjust_hinge_gap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.autoDisableBookModeOnSingleScreenStart(),
                    title = stringResource(MR.strings.pref_auto_disable_on_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPref.centerSinglePage(),
                    title = stringResource(MR.strings.pref_center_single_page),
                    subtitle = stringResource(MR.strings.pref_center_single_page_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getHingeGroup(readerPref: ReaderPreferences): Preference.PreferenceGroup {
        val manualHingeGap by readerPref.manualHingeGap().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_hinge_gap),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = manualHingeGap,
                    valueRange = 0..200,
                    title = stringResource(MR.strings.pref_hinge_gap),
                    valueString = "${manualHingeGap}px",
                    onValueChanged = { readerPref.manualHingeGap().set(it) },
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_hinge_presets),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.hinge_duo1),
                    onClick = { readerPref.manualHingeGap().set(84) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.hinge_duo2),
                    onClick = { readerPref.manualHingeGap().set(66) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.hinge_fold),
                    onClick = { readerPref.manualHingeGap().set(0) },
                ),
            ),
        )
    }

    @Composable
    private fun getDualScreenModeGroup(
        basePref: BasePreferences, 
        context: Context,
        isEnabled: Boolean
    ): Preference.PreferenceGroup {
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

        val hasSecondaryDisplay = detectedDisplays.isNotEmpty()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()
        
        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = basePref.enableDualScreenMode(),
                title = stringResource(MR.strings.pref_dual_screen_mode),
                subtitle = if (hasSecondaryDisplay) {
                    stringResource(MR.strings.pref_dual_screen_mode_summary)
                } else {
                    stringResource(MR.strings.pref_dual_screen_no_secondary_display)
                },
                enabled = hasSecondaryDisplay,
            )
        )

        // Only show sub-options if Dual-screen mode is actually enabled
        if (isEnabled) {
            items.add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePref.alwaysShowDashboard(),
                    title = stringResource(MR.strings.pref_always_show_dashboard),
                    subtitle = stringResource(MR.strings.pref_always_show_dashboard_summary),
                )
            )
            items.add(
                Preference.PreferenceItem.ListPreference(
                    preference = basePref.secondaryDisplayId(),
                    entries = displayLabels.toImmutableMap(),
                    title = stringResource(MR.strings.pref_secondary_display_id),
                    subtitle = stringResource(MR.strings.pref_secondary_display_id_summary),
                )
            )
            items.add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePref.swapPresentationRotation(),
                    title = stringResource(MR.strings.pref_swap_presentation_rotation),
                    subtitle = stringResource(MR.strings.pref_swap_presentation_rotation_summary),
                )
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_secondary_display),
            preferenceItems = items.toImmutableList(),
        )
    }
}