package eu.kanade.tachiyomi.ui.reader.setting

import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewerContinuous
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import mihon.app.di.appGraph
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ReaderLeftToRight
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStrip
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStripGaps
import mihon.icons.custommaterialsymbols.rounded.ReaderRightToLeft
import mihon.icons.custommaterialsymbols.rounded.ReaderVertical
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MobileGear
import tachiyomi.i18n.MR

enum class ReadingMode(
    val stringRes: StringResource,
    val icon: ImageVector,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(MR.strings.label_default, MaterialSymbols.Rounded.MobileGear, 0x00000000),
    LEFT_TO_RIGHT(
        MR.strings.left_to_right_viewer,
        CustomMaterialSymbols.Rounded.ReaderLeftToRight,
        0x00000001,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    RIGHT_TO_LEFT(
        MR.strings.right_to_left_viewer,
        CustomMaterialSymbols.Rounded.ReaderRightToLeft,
        0x00000002,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    VERTICAL(
        MR.strings.vertical_viewer,
        CustomMaterialSymbols.Rounded.ReaderVertical,
        0x00000003,
        Direction.Vertical,
        ViewerType.Pager,
    ),
    WEBTOON(
        MR.strings.webtoon_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStrip,
        0x00000004,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    CONTINUOUS_VERTICAL(
        MR.strings.vertical_plus_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStripGaps,
        0x00000005,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    ;

    companion object {
        const val MASK = 0x00000007

        fun fromPreference(preference: Int?): ReadingMode = entries.find { it.flagValue == preference } ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }

        fun toViewer(preference: Int?, activity: ReaderActivity): Viewer {
            if (activity.appGraph.basePreferences.highQualityRenderer.get()) {
                return when (fromPreference(preference)) {
                    LEFT_TO_RIGHT -> WebGpuViewer(activity, isReversed = false, isVertical = false)
                    RIGHT_TO_LEFT -> WebGpuViewer(activity, isReversed = true, isVertical = false)
                    VERTICAL -> WebGpuViewer(activity, isReversed = false, isVertical = true)
                    WEBTOON -> WebGpuViewerContinuous(activity, useGap = false)
                    CONTINUOUS_VERTICAL -> WebGpuViewerContinuous(activity, useGap = true)
                    DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
                }
            }
            return when (fromPreference(preference)) {
                LEFT_TO_RIGHT -> L2RPagerViewer(activity)
                RIGHT_TO_LEFT -> R2LPagerViewer(activity)
                VERTICAL -> VerticalPagerViewer(activity)
                WEBTOON -> WebtoonViewer(activity)
                CONTINUOUS_VERTICAL -> WebtoonViewer(activity, isContinuous = false)
                DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
            }
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
