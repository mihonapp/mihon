package mihon.feature.common.utils

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.i18n.MR

fun Int.groupTypeStringRes(hasCategories: Boolean = true): StringResource {
    return when (this) {
        LibraryGroup.BY_STATUS -> MR.strings.status
        LibraryGroup.BY_SOURCE -> MR.strings.label_sources
        LibraryGroup.BY_TRACK_STATUS -> MR.strings.tracking_status
        LibraryGroup.BY_LANGUAGE -> MR.strings.ext_info_language
        LibraryGroup.UNGROUPED -> MR.strings.ungrouped
        else -> if (hasCategories) MR.strings.categories else MR.strings.ungrouped
    }
}

fun Int.groupTypeDrawableRes(): Int {
    return when (this) {
        LibraryGroup.BY_STATUS -> R.drawable.ic_progress_clock_24dp
        LibraryGroup.BY_TRACK_STATUS -> R.drawable.ic_sync_24dp
        LibraryGroup.BY_SOURCE -> R.drawable.ic_browse_filled_24dp
        LibraryGroup.BY_LANGUAGE -> R.drawable.ic_translate_24dp
        LibraryGroup.UNGROUPED -> R.drawable.ic_ungroup_24dp
        else -> R.drawable.ic_label_24dp
    }
}
