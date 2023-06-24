package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import tachiyomi.core.preference.getAndSet

@Composable
fun ReaderColorFilterDialog(
    onDismissRequest: () -> Unit,
    readerPreferences: ReaderPreferences,
) {
    val colorFilterModes = buildList {
        addAll(
            listOf(
                R.string.label_default,
                R.string.filter_mode_multiply,
                R.string.filter_mode_screen,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addAll(
                listOf(
                    R.string.filter_mode_overlay,
                    R.string.filter_mode_lighten,
                    R.string.filter_mode_darken,
                ),
            )
        }
    }.map { stringResource(it) }

    val customBrightness by readerPreferences.customBrightness().collectAsState()
    val customBrightnessValue by readerPreferences.customBrightnessValue().collectAsState()
    val colorFilter by readerPreferences.colorFilter().collectAsState()
    val colorFilterValue by readerPreferences.colorFilterValue().collectAsState()
    val colorFilterMode by readerPreferences.colorFilterMode().collectAsState()

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

        CompositionLocalProvider(
            LocalPreferenceMinHeight provides 48.dp,
        ) {
            PreferenceScreen(
                items = listOfNotNull(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = readerPreferences.customBrightness(),
                        title = stringResource(R.string.pref_custom_brightness),
                    ),
                    /**
                     * Sets the brightness of the screen. Range is [-75, 100].
                     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
                     * From 1 to 100 it sets that value as brightness.
                     * 0 sets system brightness and hides the overlay.
                     */
                    Preference.PreferenceItem.SliderPreference(
                        value = customBrightnessValue,
                        title = stringResource(R.string.pref_custom_brightness),
                        min = -75,
                        max = 100,
                        onValueChanged = {
                            readerPreferences.customBrightnessValue().set(it)
                            true
                        },
                    ).takeIf { customBrightness },

                    Preference.PreferenceItem.SwitchPreference(
                        pref = readerPreferences.colorFilter(),
                        title = stringResource(R.string.pref_custom_color_filter),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = colorFilterValue.red,
                        title = stringResource(R.string.color_filter_r_value),
                        max = 255,
                        onValueChanged = { newRValue ->
                            readerPreferences.colorFilterValue().getAndSet {
                                getColorValue(it, newRValue, RED_MASK, 16)
                            }
                            true
                        },
                    ).takeIf { colorFilter },
                    Preference.PreferenceItem.SliderPreference(
                        value = colorFilterValue.green,
                        title = stringResource(R.string.color_filter_g_value),
                        max = 255,
                        onValueChanged = { newRValue ->
                            readerPreferences.colorFilterValue().getAndSet {
                                getColorValue(it, newRValue, GREEN_MASK, 8)
                            }
                            true
                        },
                    ).takeIf { colorFilter },
                    Preference.PreferenceItem.SliderPreference(
                        value = colorFilterValue.blue,
                        title = stringResource(R.string.color_filter_b_value),
                        max = 255,
                        onValueChanged = { newRValue ->
                            readerPreferences.colorFilterValue().getAndSet {
                                getColorValue(it, newRValue, BLUE_MASK, 0)
                            }
                            true
                        },
                    ).takeIf { colorFilter },
                    Preference.PreferenceItem.SliderPreference(
                        value = colorFilterValue.alpha,
                        title = stringResource(R.string.color_filter_a_value),
                        max = 255,
                        onValueChanged = { newRValue ->
                            readerPreferences.colorFilterValue().getAndSet {
                                getColorValue(it, newRValue, ALPHA_MASK, 24)
                            }
                            true
                        },
                    ).takeIf { colorFilter },
                    Preference.PreferenceItem.BasicListPreference(
                        value = colorFilterMode.toString(),
                        title = stringResource(R.string.pref_color_filter_mode),
                        entries = colorFilterModes
                            .mapIndexed { index, mode -> index.toString() to mode }
                            .toMap(),
                        onValueChanged = { newValue ->
                            readerPreferences.colorFilterMode().set(newValue.toInt())
                            true
                        },
                    ).takeIf { colorFilter },

                    Preference.PreferenceItem.SwitchPreference(
                        pref = readerPreferences.grayscale(),
                        title = stringResource(R.string.pref_grayscale),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = readerPreferences.invertedColors(),
                        title = stringResource(R.string.pref_inverted_colors),
                    ),
                ),
            )
        }
    }
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
