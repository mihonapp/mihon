package eu.kanade.domain.track.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class AutoTrackState(val titleRes: StringResource) {
    ASK(MR.strings.default_category_summary),
    ALWAYS(MR.strings.lock_always),
    NEVER(MR.strings.lock_never),
}
