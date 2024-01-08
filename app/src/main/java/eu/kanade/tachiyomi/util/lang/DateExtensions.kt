package eu.kanade.tachiyomi.util.lang

import android.content.Context
import tachiyomi.core.i18n.pluralStringResource
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date

fun Date.toDateTimestampString(dateFormatter: DateFormat): String {
    val date = dateFormatter.format(this)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
    return "$date $time"
}

fun Date.toTimestampString(): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
}

fun Long.convertEpochMillisZone(
    from: ZoneId,
    to: ZoneId,
): Long {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), from)
        .atZone(to)
        .toInstant()
        .toEpochMilli()
}

/**
 * Get date as time key
 *
 * @param date desired date
 * @return date as time key
 */
fun Long.toDateKey(): Date {
    val instant = Instant.ofEpochMilli(this)
    return Date.from(instant.truncatedTo(ChronoUnit.DAYS))
}

fun Date.toRelativeString(
    context: Context,
    relative: Boolean = true,
    dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this)
    }
    val now = Date()
    val difference = now.timeWithOffset.floorNearest(MILLISECONDS_IN_DAY) -
        this.timeWithOffset.floorNearest(MILLISECONDS_IN_DAY)
    val days = difference.floorDiv(MILLISECONDS_IN_DAY).toInt()
    return when {
        difference < 0 -> dateFormat.format(this)
        difference < MILLISECONDS_IN_DAY -> context.stringResource(MR.strings.relative_time_today)
        difference < MILLISECONDS_IN_DAY.times(7) -> context.pluralStringResource(
            MR.plurals.relative_time,
            days,
            days,
        )
        else -> dateFormat.format(this)
    }
}

private const val MILLISECONDS_IN_DAY = 86_400_000L

private val Date.timeWithOffset: Long
    get() {
        return Calendar.getInstance().run {
            time = this@timeWithOffset
            val dstOffset = get(Calendar.DST_OFFSET)
            this@timeWithOffset.time + timeZone.rawOffset + dstOffset
        }
    }

private fun Long.floorNearest(to: Long): Long {
    return this.floorDiv(to) * to
}
