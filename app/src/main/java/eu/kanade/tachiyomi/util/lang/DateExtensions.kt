package eu.kanade.tachiyomi.util.lang

import java.text.DateFormat
import java.util.Date

fun Date.toTimestampString(dateFormatter: DateFormat): String {
    val date = dateFormatter.format(this)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(this)
    return "$date $time"
}
