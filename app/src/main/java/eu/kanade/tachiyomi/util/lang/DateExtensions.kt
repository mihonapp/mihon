package eu.kanade.tachiyomi.util.lang

import android.content.Context
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant

fun LocalDateTime.toDateTimestampString(dateTimeFormatter: DateTimeFormatter): String {
    val javaLocalDateTime = this.toJavaLocalDateTime()
    val date = dateTimeFormatter.format(javaLocalDateTime)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(javaLocalDateTime)
    return "$date $time"
}

fun Date.toTimestampString(): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
}

fun Long.convertEpochMillisZone(
    from: TimeZone,
    to: TimeZone,
): Long {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(from)
        .toInstant(to)
        .toEpochMilliseconds()
}

fun Long.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
}

fun Long.toJavaLocalDate(): java.time.LocalDate {
    return this.toLocalDate().toJavaLocalDate()
}

fun LocalDate.toRelativeString(
    context: Context,
    relative: Boolean = true,
    dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this.toJavaLocalDate())
    }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val difference = (today - this).days
    return when {
        difference < -7 -> dateFormat.format(this.toJavaLocalDate())
        difference < 0 -> context.pluralStringResource(
            MR.plurals.upcoming_relative_time,
            difference.absoluteValue,
            difference.absoluteValue,
        )
        difference < 1 -> context.stringResource(MR.strings.relative_time_today)
        difference < 7 -> context.pluralStringResource(
            MR.plurals.relative_time,
            difference,
            difference,
        )
        else -> dateFormat.format(this.toJavaLocalDate())
    }
}
