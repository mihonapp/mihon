package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderGeneralSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.preference.bindToPreference
import kotlinx.coroutines.flow.launchIn
import uy.kohesive.injekt.injectLazy

/**
 * Sheet to show reader and viewer preferences.
 */
class ReaderGeneralSettings @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    private val preferences: PreferencesHelper by injectLazy()

    private val binding = ReaderGeneralSettingsBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)

        initGeneralPreferences()
    }

    /**
     * Init general reader preferences.
     */
    private fun initGeneralPreferences() {
        binding.backgroundColor.bindToIntPreference(preferences.readerTheme(), R.array.reader_themes_values)
        binding.showPageNumber.bindToPreference(preferences.showPageNumber())
        binding.fullscreen.bindToPreference(preferences.fullscreen())
        preferences.fullscreen()
            .asImmediateFlow {
                // If the preference is explicitly disabled, that means the setting was configured since there is a cutout
                binding.cutoutShort.isVisible = it && ((context as ReaderActivity).hasCutout || !preferences.cutoutShort().get())
                binding.cutoutShort.bindToPreference(preferences.cutoutShort())
            }
            .launchIn((context as ReaderActivity).lifecycleScope)

        binding.keepscreen.bindToPreference(preferences.keepScreenOn())
        binding.longTap.bindToPreference(preferences.readWithLongTap())
        binding.alwaysShowChapterTransition.bindToPreference(preferences.alwaysShowChapterTransition())
        binding.pageTransitions.bindToPreference(preferences.pageTransitions())
    }
}
