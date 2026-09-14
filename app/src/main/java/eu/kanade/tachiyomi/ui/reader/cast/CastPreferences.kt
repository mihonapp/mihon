package eu.kanade.tachiyomi.ui.reader.cast

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class CastPreferences(
    preferenceStore: PreferenceStore,
) {

    // region Presentation on the cast target

    val orientation: Preference<CastOrientation> = preferenceStore.getEnum(
        "cast_orientation",
        CastOrientation.LANDSCAPE,
    )

    val scaleMode: Preference<CastScaleMode> = preferenceStore.getEnum("cast_scale_mode", CastScaleMode.FIT_SCREEN)

    /** Zoom applied on top of [scaleMode] for paged layouts, in percent. */
    val zoomPercent: Preference<Int> = preferenceStore.getInt("cast_zoom_percent", 100)

    /** Width of the strip for continuous layouts, in percent of the (rotated) screen width. */
    val stripWidthPercent: Preference<Int> = preferenceStore.getInt("cast_strip_width_percent", 50)

    val background: Preference<CastBackground> = preferenceStore.getEnum("cast_background", CastBackground.BLACK)

    // endregion

    // region Web receiver

    val webPort: Preference<Int> = preferenceStore.getInt("cast_web_port", DEFAULT_WEB_PORT)

    val webImageQuality: Preference<CastImageQuality> = preferenceStore.getEnum(
        "cast_web_image_quality",
        CastImageQuality.OPTIMIZED,
    )

    // endregion

    // region Auto-scroll

    /** Continuous layouts: scroll speed in dp per second. */
    val autoScrollSpeed: Preference<Int> = preferenceStore.getInt("cast_auto_scroll_speed", 60)

    /** Continuous layouts: interval between scroll steps in milliseconds (16 = every frame). */
    val autoScrollStepMs: Preference<Int> = preferenceStore.getInt("cast_auto_scroll_step_ms", 16)

    /** Paged layouts: seconds a page stays on screen before advancing. */
    val autoScrollPageIntervalSec: Preference<Int> = preferenceStore.getInt("cast_auto_scroll_page_interval", 10)

    // endregion

    companion object {
        const val DEFAULT_WEB_PORT = 8765

        const val ZOOM_MIN = 25
        const val ZOOM_MAX = 400
        const val STRIP_WIDTH_MIN = 15
        const val STRIP_WIDTH_MAX = 100

        const val AUTO_SCROLL_SPEED_MIN = 5
        const val AUTO_SCROLL_SPEED_MAX = 500
        const val AUTO_SCROLL_PAGE_INTERVAL_MIN = 1
        const val AUTO_SCROLL_PAGE_INTERVAL_MAX = 120

        /** Selectable scroll step intervals in milliseconds; the first one means "every frame". */
        val AutoScrollSteps = listOf(16, 50, 100, 250, 500, 1000)
    }
}
