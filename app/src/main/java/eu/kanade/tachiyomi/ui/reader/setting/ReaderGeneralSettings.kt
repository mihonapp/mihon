package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderGeneralSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.preference.bindToPreference
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

/**
 * Sheet to show reader and viewer preferences.
 */
class ReaderGeneralSettings @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    private val readerPreferences: ReaderPreferences by injectLazy()

    private val binding = ReaderGeneralSettingsBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)

        initGeneralPreferences()
    }

    /**
     * Init general reader preferences.
     */
    private fun initGeneralPreferences() {
        binding.backgroundColor.bindToIntPreference(readerPreferences.readerTheme(), R.array.reader_themes_values)
        binding.showPageNumber.bindToPreference(readerPreferences.showPageNumber())
        binding.fullscreen.bindToPreference(readerPreferences.fullscreen())
        readerPreferences.fullscreen().changes()
            .onEach {
                // If the preference is explicitly disabled, that means the setting was configured since there is a cutout
                binding.cutoutShort.isVisible = it && ((context as ReaderActivity).hasCutout || !readerPreferences.cutoutShort().get())
                binding.cutoutShort.bindToPreference(readerPreferences.cutoutShort())
            }
            .launchIn((context as ReaderActivity).lifecycleScope)

        binding.keepscreen.bindToPreference(readerPreferences.keepScreenOn())
        binding.longTap.bindToPreference(readerPreferences.readWithLongTap())
        binding.alwaysShowChapterTransition.bindToPreference(readerPreferences.alwaysShowChapterTransition())
        binding.pageTransitions.bindToPreference(readerPreferences.pageTransitions())
    }
}
