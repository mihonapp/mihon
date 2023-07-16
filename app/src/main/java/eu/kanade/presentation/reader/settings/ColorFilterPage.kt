package eu.kanade.presentation.reader.settings

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.core.preference.getAndSet
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsFlowRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.ChoiceChip

@Composable
internal fun ColumnScope.ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
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

    val customBrightness by screenModel.preferences.customBrightness().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_custom_brightness),
        checked = customBrightness,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::customBrightness)
        },
    )

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (customBrightness) {
        val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
        SliderItem(
            label = stringResource(R.string.pref_custom_brightness),
            min = -75,
            max = 100,
            value = customBrightnessValue,
            valueText = customBrightnessValue.toString(),
            onChange = { screenModel.preferences.customBrightnessValue().set(it) },
        )
    }

    val colorFilter by screenModel.preferences.colorFilter().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_custom_color_filter),
        checked = colorFilter,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::colorFilter)
        },
    )
    if (colorFilter) {
        val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
        SliderItem(
            label = stringResource(R.string.color_filter_r_value),
            max = 255,
            value = colorFilterValue.red,
            valueText = colorFilterValue.red.toString(),
            onChange = { newRValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newRValue, RED_MASK, 16)
                }
            },
        )
        SliderItem(
            label = stringResource(R.string.color_filter_g_value),
            max = 255,
            value = colorFilterValue.green,
            valueText = colorFilterValue.green.toString(),
            onChange = { newGValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newGValue, GREEN_MASK, 8)
                }
            },
        )
        SliderItem(
            label = stringResource(R.string.color_filter_b_value),
            max = 255,
            value = colorFilterValue.blue,
            valueText = colorFilterValue.blue.toString(),
            onChange = { newBValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newBValue, BLUE_MASK, 0)
                }
            },
        )
        SliderItem(
            label = stringResource(R.string.color_filter_a_value),
            max = 255,
            value = colorFilterValue.alpha,
            valueText = colorFilterValue.alpha.toString(),
            onChange = { newAValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newAValue, ALPHA_MASK, 24)
                }
            },
        )

        val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
        SettingsFlowRow(R.string.pref_color_filter_mode) {
            colorFilterModes.mapIndexed { index, it ->
                ChoiceChip(
                    isSelected = colorFilterMode == index,
                    onClick = { screenModel.preferences.colorFilterMode().set(index) },
                    content = { Text(it) },
                )
            }
        }
    }

    val grayscale by screenModel.preferences.grayscale().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_grayscale),
        checked = grayscale,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::grayscale)
        },
    )
    val invertedColors by screenModel.preferences.invertedColors().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_inverted_colors),
        checked = invertedColors,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::invertedColors)
        },
    )
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
