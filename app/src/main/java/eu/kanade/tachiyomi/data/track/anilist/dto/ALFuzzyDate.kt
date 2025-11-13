package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ALFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?,
) {
    fun toEpochMilli(): Long = try {
        LocalDate.of(year!!, month!!, day!!)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        0L
    }
}
