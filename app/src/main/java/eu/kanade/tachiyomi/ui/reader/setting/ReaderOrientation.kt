package eu.kanade.tachiyomi.ui.reader.setting

import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Mobile
import mihon.icons.materialsymbols.rounded.MobileLandscape
import mihon.icons.materialsymbols.rounded.MobileLockLandscape
import mihon.icons.materialsymbols.rounded.MobileLockPortrait
import mihon.icons.materialsymbols.rounded.MobileRotate
import tachiyomi.i18n.MR

enum class ReaderOrientation(
    val flag: Int,
    val stringRes: StringResource,
    val icon: ImageVector,
    val flagValue: Int,
) {
    DEFAULT(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.label_default,
        MaterialSymbols.Rounded.MobileRotate,
        0x00000000,
    ),
    FREE(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.rotation_free,
        MaterialSymbols.Rounded.MobileRotate,
        0x00000008,
    ),
    PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        MR.strings.rotation_portrait,
        MaterialSymbols.Rounded.Mobile,
        0x00000010,
    ),
    LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        MR.strings.rotation_landscape,
        MaterialSymbols.Rounded.MobileLandscape,
        0x00000018,
    ),
    LOCKED_PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        MR.strings.rotation_force_portrait,
        MaterialSymbols.Rounded.MobileLockPortrait,
        0x00000020,
    ),
    LOCKED_LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        MR.strings.rotation_force_landscape,
        MaterialSymbols.Rounded.MobileLockLandscape,
        0x00000028,
    ),
    REVERSE_PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        MR.strings.rotation_reverse_portrait,
        MaterialSymbols.Rounded.Mobile,
        0x00000030,
    ),
    ;

    companion object {
        const val MASK = 0x00000038

        fun fromPreference(preference: Int?): ReaderOrientation = entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
