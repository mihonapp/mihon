package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.domain.manga.model.orientationType
import eu.kanade.domain.manga.model.readingModeType
import eu.kanade.tachiyomi.databinding.ReaderReadingModeSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity

class ReaderSettingsSheet(
    private val activity: ReaderActivity,
) : BottomSheetDialog(activity) {

    private lateinit var binding: ReaderReadingModeSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ReaderReadingModeSettingsBinding.inflate(activity.layoutInflater)
        setContentView(binding.root)

        initGeneralPreferences()
    }

    private fun initGeneralPreferences() {
        binding.viewer.onItemSelectedListener = { position ->
            val readingModeType = ReadingModeType.fromSpinner(position)
            activity.viewModel.setMangaReadingMode(readingModeType.flagValue)
        }
        binding.viewer.setSelection(activity.viewModel.manga?.readingModeType?.let { ReadingModeType.fromPreference(it.toInt()).prefValue } ?: ReadingModeType.DEFAULT.prefValue)

        binding.rotationMode.onItemSelectedListener = { position ->
            val rotationType = OrientationType.fromSpinner(position)
            activity.viewModel.setMangaOrientationType(rotationType.flagValue)
        }
        binding.rotationMode.setSelection(activity.viewModel.manga?.orientationType?.let { OrientationType.fromPreference(it.toInt()).prefValue } ?: OrientationType.DEFAULT.prefValue)
    }
}
