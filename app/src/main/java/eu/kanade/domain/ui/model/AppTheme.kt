package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.system.isDevFlavor
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.i18n.MR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),

    // TODO: re-enable for preview
    NORD(MR.strings.theme_nord.takeIf { isDevFlavor || isPreviewBuildType }),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
