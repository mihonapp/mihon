package eu.kanade.tachiyomi.ui.reader.setting

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import kotlin.math.max

enum class ReadingModeType(val prefValue: Int, @StringRes val stringRes: Int) {
    DEFAULT(0, R.string.default_viewer),
    LEFT_TO_RIGHT(1, R.string.left_to_right_viewer),
    RIGHT_TO_LEFT(2, R.string.right_to_left_viewer),
    VERTICAL(3, R.string.vertical_viewer),
    WEBTOON(4, R.string.webtoon_viewer),
    CONTINUOUS_VERTICAL(5, R.string.vertical_plus_viewer),
    ;

    companion object {
        fun fromPreference(preference: Int): ReadingModeType = values().find { it.prefValue == preference } ?: DEFAULT

        fun getNextReadingMode(preference: Int): ReadingModeType {
            // There's only 6 options (0 to 5)
            val newReadingMode = max(0, (preference + 1) % 6)
            return fromPreference(newReadingMode)
        }
    }
}
