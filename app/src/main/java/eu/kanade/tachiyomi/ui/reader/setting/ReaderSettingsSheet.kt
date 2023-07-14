package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.domain.manga.model.orientationType
import eu.kanade.domain.manga.model.readingModeType
import eu.kanade.tachiyomi.databinding.ReaderReadingModeSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.preference.bindToPreference
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

class ReaderSettingsSheet(
    private val activity: ReaderActivity,
) : BottomSheetDialog(activity) {

    private val readerPreferences: ReaderPreferences by injectLazy()

    private lateinit var binding: ReaderReadingModeSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ReaderReadingModeSettingsBinding.inflate(activity.layoutInflater)
        setContentView(binding.root)

        initGeneralPreferences()

        when (activity.viewModel.state.value.viewer) {
            is PagerViewer -> initPagerPreferences()
            is WebtoonViewer -> initWebtoonPreferences()
        }
    }

    private fun initGeneralPreferences() {
        binding.viewer.onItemSelectedListener = { position ->
            val readingModeType = ReadingModeType.fromSpinner(position)
            activity.viewModel.setMangaReadingMode(readingModeType.flagValue)

            val mangaViewer = activity.viewModel.getMangaReadingMode()
            if (mangaViewer == ReadingModeType.WEBTOON.flagValue || mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue) {
                initWebtoonPreferences()
            } else {
                initPagerPreferences()
            }
        }
        binding.viewer.setSelection(activity.viewModel.manga?.readingModeType?.let { ReadingModeType.fromPreference(it.toInt()).prefValue } ?: ReadingModeType.DEFAULT.prefValue)

        binding.rotationMode.onItemSelectedListener = { position ->
            val rotationType = OrientationType.fromSpinner(position)
            activity.viewModel.setMangaOrientationType(rotationType.flagValue)
        }
        binding.rotationMode.setSelection(activity.viewModel.manga?.orientationType?.let { OrientationType.fromPreference(it.toInt()).prefValue } ?: OrientationType.DEFAULT.prefValue)
    }

    private fun initPagerPreferences() {
        binding.webtoonPrefsGroup.root.isVisible = false
        binding.pagerPrefsGroup.root.isVisible = true

        binding.pagerPrefsGroup.tappingInverted.bindToPreference(readerPreferences.pagerNavInverted(), ReaderPreferences.TappingInvertMode::class.java)

        binding.pagerPrefsGroup.pagerNav.bindToPreference(readerPreferences.navigationModePager())
        readerPreferences.navigationModePager().changes()
            .onEach {
                val isTappingEnabled = it != 5
                binding.pagerPrefsGroup.tappingInverted.isVisible = isTappingEnabled
            }
            .launchIn(activity.lifecycleScope)
        binding.pagerPrefsGroup.scaleType.bindToPreference(readerPreferences.imageScaleType(), 1)

        binding.pagerPrefsGroup.zoomStart.bindToPreference(readerPreferences.zoomStart(), 1)
    }

    private fun initWebtoonPreferences() {
        binding.pagerPrefsGroup.root.isVisible = false
        binding.webtoonPrefsGroup.root.isVisible = true

        binding.webtoonPrefsGroup.tappingInverted.bindToPreference(readerPreferences.webtoonNavInverted(), ReaderPreferences.TappingInvertMode::class.java)

        binding.webtoonPrefsGroup.webtoonNav.bindToPreference(readerPreferences.navigationModeWebtoon())
        readerPreferences.navigationModeWebtoon().changes()
            .onEach { binding.webtoonPrefsGroup.tappingInverted.isVisible = it != 5 }
            .launchIn(activity.lifecycleScope)
    }
}
