package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderColorFilterSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.preference.bindToPreference
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import uy.kohesive.injekt.injectLazy

/**
 * Color filter sheet to toggle custom filter and brightness overlay.
 */
class ReaderColorFilterSettings @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    private val preferences: PreferencesHelper by injectLazy()

    private val binding = ReaderColorFilterSettingsBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)

        preferences.colorFilter().asFlow()
            .onEach { setColorFilter(it) }
            .launchIn((context as ReaderActivity).lifecycleScope)

        preferences.colorFilterMode().asFlow()
            .onEach { setColorFilter(preferences.colorFilter().get()) }
            .launchIn(context.lifecycleScope)

        preferences.customBrightness().asFlow()
            .onEach { setCustomBrightness(it) }
            .launchIn(context.lifecycleScope)

        // Get color and update values
        val color = preferences.colorFilterValue().get()
        val brightness = preferences.customBrightnessValue().get()

        val argb = setValues(color)

        // Set brightness value
        binding.txtBrightnessSeekbarValue.text = brightness.toString()
        binding.sliderBrightness.value = brightness.toFloat()

        // Initialize seekBar progress
        binding.sliderColorFilterAlpha.value = argb[0].toFloat()
        binding.sliderColorFilterRed.value = argb[1].toFloat()
        binding.sliderColorFilterGreen.value = argb[2].toFloat()
        binding.sliderColorFilterBlue.value = argb[3].toFloat()

        // Set listeners
        binding.switchColorFilter.bindToPreference(preferences.colorFilter())
        binding.customBrightness.bindToPreference(preferences.customBrightness())
        binding.colorFilterMode.bindToPreference(preferences.colorFilterMode())
        binding.grayscale.bindToPreference(preferences.grayscale())
        binding.invertedColors.bindToPreference(preferences.invertedColors())

        binding.sliderColorFilterAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setColorValue(value.toInt(), ALPHA_MASK, 24)
            }
        }
        binding.sliderColorFilterRed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setColorValue(value.toInt(), RED_MASK, 16)
            }
        }
        binding.sliderColorFilterGreen.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setColorValue(value.toInt(), GREEN_MASK, 8)
            }
        }
        binding.sliderColorFilterBlue.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setColorValue(value.toInt(), BLUE_MASK, 0)
            }
        }

        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.customBrightnessValue().set(value.toInt())
            }
        }
    }

    /**
     * Set enabled status of seekBars belonging to color filter
     * @param enabled determines if seekBar gets enabled
     */
    private fun setColorFilterSeekBar(enabled: Boolean) {
        binding.sliderColorFilterRed.isEnabled = enabled
        binding.sliderColorFilterGreen.isEnabled = enabled
        binding.sliderColorFilterBlue.isEnabled = enabled
        binding.sliderColorFilterAlpha.isEnabled = enabled
    }

    /**
     * Set enabled status of seekBars belonging to custom brightness
     * @param enabled value which determines if seekBar gets enabled
     */
    private fun setCustomBrightnessSeekBar(enabled: Boolean) {
        binding.sliderBrightness.isEnabled = enabled
    }

    /**
     * Set the text value's of color filter
     * @param color integer containing color information
     */
    private fun setValues(color: Int): Array<Int> {
        val alpha = color.alpha
        val red = color.red
        val green = color.green
        val blue = color.blue

        // Initialize values
        binding.txtColorFilterAlphaValue.text = "$alpha"
        binding.txtColorFilterRedValue.text = "$red"
        binding.txtColorFilterGreenValue.text = "$green"
        binding.txtColorFilterBlueValue.text = "$blue"

        return arrayOf(alpha, red, green, blue)
    }

    /**
     * Manages the custom brightness value subscription
     * @param enabled determines if the subscription get (un)subscribed
     */
    private fun setCustomBrightness(enabled: Boolean) {
        if (enabled) {
            preferences.customBrightnessValue().asFlow()
                .sample(100)
                .onEach { setCustomBrightnessValue(it) }
                .launchIn((context as ReaderActivity).lifecycleScope)
        } else {
            setCustomBrightnessValue(0, true)
        }
        setCustomBrightnessSeekBar(enabled)
    }

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    private fun setCustomBrightnessValue(value: Int, isDisabled: Boolean = false) {
        if (!isDisabled) {
            binding.txtBrightnessSeekbarValue.text = value.toString()
        }
    }

    /**
     * Manages the color filter value subscription
     * @param enabled determines if the subscription get (un)subscribed
     */
    private fun setColorFilter(enabled: Boolean) {
        if (enabled) {
            preferences.colorFilterValue().asFlow()
                .sample(100)
                .onEach { setColorFilterValue(it) }
                .launchIn((context as ReaderActivity).lifecycleScope)
        }
        setColorFilterSeekBar(enabled)
    }

    /**
     * Sets the color filter overlay of the screen. Determined by HEX of integer
     * @param color hex of color.
     */
    private fun setColorFilterValue(@ColorInt color: Int) {
        setValues(color)
    }

    /**
     * Updates the color value in preference
     * @param color value of color range [0,255]
     * @param mask contains hex mask of chosen color
     * @param bitShift amounts of bits that gets shifted to receive value
     */
    private fun setColorValue(color: Int, mask: Long, bitShift: Int) {
        val currentColor = preferences.colorFilterValue().get()
        val updatedColor = (color shl bitShift) or (currentColor and mask.inv().toInt())
        preferences.colorFilterValue().set(updatedColor)
    }
}

private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
