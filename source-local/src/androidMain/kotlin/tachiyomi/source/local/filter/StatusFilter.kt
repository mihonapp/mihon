package tachiyomi.source.local.filter

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class StatusFilter(context: Context) : Filter.Select<String>(
    context.stringResource(MR.strings.status),
    arrayOf(
        context.stringResource(MR.strings.local_filter_status_any),
        context.stringResource(MR.strings.unknown),
        context.stringResource(MR.strings.ongoing),
        context.stringResource(MR.strings.completed),
        context.stringResource(MR.strings.licensed),
        context.stringResource(MR.strings.publishing_finished),
        context.stringResource(MR.strings.cancelled),
        context.stringResource(MR.strings.on_hiatus),
    ),
) {
    val selectedStatus: Int?
        get() = STATUS_VALUES.getOrNull(state)

    companion object {
        private val STATUS_VALUES = listOf(
            null,
            SManga.UNKNOWN,
            SManga.ONGOING,
            SManga.COMPLETED,
            SManga.LICENSED,
            SManga.PUBLISHING_FINISHED,
            SManga.CANCELLED,
            SManga.ON_HIATUS,
        )
    }
}
