package eu.kanade.tachiyomi.util.lang

import android.content.Context
import eu.kanade.tachiyomi.R
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

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
    val cal = Calendar.getInstance()
    cal.time = Date(this)
    cal[Calendar.HOUR_OF_DAY] = 0
    cal[Calendar.MINUTE] = 0
    cal[Calendar.SECOND] = 0
    cal[Calendar.MILLISECOND] = 0
    return cal.time
}

/**
 * Convert epoch long to Calendar instance
 *
 * @return Calendar instance at supplied epoch time. Null if epoch was 0.
 */
fun Long.toCalendar(): Calendar? {
    if (this == 0L) {
        return null
    }
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    return cal
}

/**
 * Convert local time millisecond value to Calendar instance in UTC
 *
 * @return UTC Calendar instance at supplied time. Null if time is 0.
 */
fun Long.toUtcCalendar(): Calendar? {
    if (this == 0L) {
        return null
    }
    val rawCalendar = Calendar.getInstance().apply {
        timeInMillis = this@toUtcCalendar
    }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            rawCalendar.get(Calendar.YEAR),
            rawCalendar.get(Calendar.MONTH),
            rawCalendar.get(Calendar.DAY_OF_MONTH),
            rawCalendar.get(Calendar.HOUR_OF_DAY),
            rawCalendar.get(Calendar.MINUTE),
            rawCalendar.get(Calendar.SECOND),
        )
    }
}

/**
 * Convert UTC time millisecond to Calendar instance in local time zone
 *
 * @return local Calendar instance at supplied UTC time. Null if time is 0.
 */
fun Long.toLocalCalendar(): Calendar? {
    if (this == 0L) {
        return null
    }
    val rawCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = this@toLocalCalendar
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            rawCalendar.get(Calendar.YEAR),
            rawCalendar.get(Calendar.MONTH),
            rawCalendar.get(Calendar.DAY_OF_MONTH),
            rawCalendar.get(Calendar.HOUR_OF_DAY),
            rawCalendar.get(Calendar.MINUTE),
            rawCalendar.get(Calendar.SECOND),
        )
    }
}

private const val MILLISECONDS_IN_DAY = 86_400_000L

fun Date.toRelativeString(
    context: Context,
    dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT),
): String {
    val now = Date()
    val difference = now.timeWithOffset.floorNearest(MILLISECONDS_IN_DAY) - this.timeWithOffset.floorNearest(MILLISECONDS_IN_DAY)
    val days = difference.floorDiv(MILLISECONDS_IN_DAY).toInt()
    return when {
        difference < 0 -> dateFormat.format(this)
        difference < MILLISECONDS_IN_DAY -> context.getString(R.string.relative_time_today)
        difference < MILLISECONDS_IN_DAY.times(7) -> context.resources.getQuantityString(
            R.plurals.relative_time,
            days,
            days,
        )
        else -> dateFormat.format(this)
    }
}

private val Date.timeWithOffset: Long
    get() {
        return Calendar.getInstance().run {
            time = this@timeWithOffset
            val dstOffset = get(Calendar.DST_OFFSET)
            this@timeWithOffset.time + timeZone.rawOffset + dstOffset
        }
    }

fun Long.floorNearest(to: Long): Long {
    return this.floorDiv(to) * to
}
