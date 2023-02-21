package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.R
import kotlin.time.Duration

fun Duration.toDurationString(context: Context, fallback: String): String {
    return toComponents { days, hours, minutes, seconds, _ ->
        buildList(4) {
            if (days != 0L) add(context.getString(R.string.day_short, days))
            if (hours != 0) add(context.getString(R.string.hour_short, hours))
            if (minutes != 0 && (days == 0L || hours == 0)) add(context.getString(R.string.minute_short, minutes))
            if (seconds != 0 && days == 0L && hours == 0) add(context.getString(R.string.seconds_short, seconds))
        }.joinToString(" ").ifBlank { fallback }
    }
}
