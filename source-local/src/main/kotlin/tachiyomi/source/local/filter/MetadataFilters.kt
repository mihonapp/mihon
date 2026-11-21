package tachiyomi.source.local.filter

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Free-text filter matched case-insensitively against the author parsed from a local manga's
 * ComicInfo.xml (or legacy details.json).
 */
class AuthorFilter(context: Context) : Filter.Text(context.stringResource(MR.strings.author))

/**
 * Free-text filter matched case-insensitively against the artist parsed from a local manga's
 * ComicInfo.xml (or legacy details.json).
 */
class ArtistFilter(context: Context) : Filter.Text(context.stringResource(MR.strings.artist))

/**
 * Free-text filter matched against the genres parsed from a local manga's ComicInfo.xml (or
 * legacy details.json). Accepts a comma-separated list of terms; a manga must match every term
 * (logical AND) to be included.
 */
class GenreFilter(context: Context) : Filter.Text(context.stringResource(MR.strings.genre))

/**
 * Restricts results to local manga whose parsed publishing status matches the selection.
 */
class StatusFilter(context: Context) : Filter.Select<String>(
    context.stringResource(MR.strings.status),
    arrayOf(
        context.stringResource(MR.strings.all),
        context.stringResource(MR.strings.unknown),
        context.stringResource(MR.strings.ongoing),
        context.stringResource(MR.strings.completed),
        context.stringResource(MR.strings.licensed),
        context.stringResource(MR.strings.publishing_finished),
        context.stringResource(MR.strings.cancelled),
        context.stringResource(MR.strings.on_hiatus),
    ),
) {
    companion object {
        /** Sentinel meaning "no status restriction" (the "All" option). */
        const val ANY = Int.MIN_VALUE

        // Index in the dropdown above -> SManga status constant.
        private val STATUSES = intArrayOf(
            ANY,
            SManga.UNKNOWN,
            SManga.ONGOING,
            SManga.COMPLETED,
            SManga.LICENSED,
            SManga.PUBLISHING_FINISHED,
            SManga.CANCELLED,
            SManga.ON_HIATUS,
        )

        fun statusFor(selectedIndex: Int): Int = STATUSES.getOrElse(selectedIndex) { ANY }
    }
}
