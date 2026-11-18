package tachiyomi.domain.manga.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

data class SourceStats(
    val rating: Double?,
    val ratingCount: Long?,
)

private val ratingKeys = listOf("mihon.rating", "rating", "score")
private val ratingCountKeys = listOf(
    "mihon.rating_count",
    "rating_count",
    "ratingCount",
    "votes",
    "follows",
    "users",
)

private val labeledRating = Regex(
    "(?iu)(?:\u0440\u0435\u0439\u0442\u0438\u043d\u0433|\u043e\u0446\u0435\u043d\u043a\u0430|rating|score)" +
        "\\s*[:\uFF1A]?\\s*(\\d+(?:[.,]\\d+)?)" +
        "(?:\\s*(%|/\\s*\\d+(?:[.,]\\d+)?|\u0438\u0437\\s+\\d+(?:[.,]\\d+)?))?" +
        "(?:\\s*\\(\\s*([\\d\\s.,]+)\\s*(k|\u043a|\u0442\u044b\u0441\\.?)?\\s*\\))?",
)
private val fractionRating = Regex(
    "(\\d+(?:[.,]\\d+)?)\\s*/\\s*(5|100)(?!\\d)",
)
private val starRating = Regex(
    "[\u2605\u2606\u2726-\u2730\u2B50]+\\s*(\\d+(?:[.,]\\d+)?)",
)
private val labeledCount = Regex(
    "(?iu)(\\d[\\d\\s.,]*)\\s*(k|\u043a|\u0442\u044b\u0441\\.?)?\\s*" +
        "(?:\u043e\u0446\u0435\u043d\u043e\u043a|\u043e\u0446\u0435\u043d\u043a\u0438|" +
        "\u0433\u043e\u043b\u043e\u0441(?:\u043e\u0432|\u0430)?|votes?|ratings?|users?|" +
        "\u0447\u0438\u0442\u0430\u0442\u0435\u043b\u0435\u0439)",
)
private val countBeforeNumber = Regex(
    "(?iu)(?:\u0433\u043e\u043b\u043e\u0441\u043e\u0432|\u0433\u043e\u043b\u043e\u0441\u0430|" +
        "\u0433\u043e\u043b\u043e\u0441|\u043e\u0446\u0435\u043d\u043e\u043a|\u043e\u0446\u0435\u043d\u043a\u0438|" +
        "\u0438\u0437\u0431\u0440\u0430\u043d\u043d(?:\u043e\u0435|\u043e\u043c|\u044b\u0445)|" +
        "votes?|ratings?)" +
        "\\s*[:\uFF1A]?\\s*([\\d\\s.,]+)\\s*(k|\u043a|\u0442\u044b\u0441\\.?)?",
)

fun Manga.sourceStats(): SourceStats {
    val fromText = description?.take(1200).orEmpty().parseSourceStats()
    return SourceStats(
        rating = memo.doubleValue(ratingKeys) ?: fromText.rating,
        ratingCount = memo.longValue(ratingCountKeys) ?: fromText.ratingCount,
    )
}

private fun String.parseSourceStats(): SourceStats {
    if (isEmpty()) return SourceStats(rating = null, ratingCount = null)

    val header = take(180)
    val labeled = labeledRating.find(this)
    val scale = labeled?.groupValues?.get(2).orEmpty()
    val labeledValue = labeled?.groupValues?.get(1)?.toTenPoint(scale)
    val stars = starRating.find(header)?.groupValues?.get(1)?.toRating()?.roundToTenPoint()
    val fraction = fractionRating.find(header)?.let { match ->
        match.groupValues[1].toTenPoint("/${match.groupValues[2]}")
    }
    val rating = when {
        labeledValue != null && scale.isNotBlank() -> labeledValue
        stars != null -> stars
        labeledValue != null -> labeledValue
        else -> fraction
    }
    val count = countBeforeNumber.find(this)?.let { it.groupValues[1].toCount(it.groupValues[2]) }
        ?: labeled?.groupValues?.get(3)?.toCount(labeled.groupValues[4])
        ?: labeledCount.find(this)?.let { it.groupValues[1].toCount(it.groupValues[2]) }

    return SourceStats(rating = rating, ratingCount = count)
}

private fun JsonObject.doubleValue(keys: List<String>): Double? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.doubleOrNull
            ?: (this[key] as? JsonPrimitive)?.content?.toRating()
    }
}

private fun JsonObject.longValue(keys: List<String>): Long? {
    return keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        primitive.longOrNull ?: primitive.content.toCount(suffix = "")
    }
}

private fun String.toRating(): Double? {
    return replace(',', '.').trim().toDoubleOrNull()
}

private fun String.toTenPoint(scale: String): Double? {
    val value = toRating() ?: return null
    val trimmed = scale.trim()
    val scaled = when {
        trimmed.startsWith("%") -> value / 10.0
        else -> {
            val denominator = Regex("\\d+(?:[.,]\\d+)?").find(trimmed)?.value?.toRating()
            when (denominator) {
                5.0 -> value / 5.0 * 10.0
                100.0 -> value / 100.0 * 10.0
                else -> value
            }
        }
    }
    return scaled.roundToTenPoint()
}

private fun Double.roundToTenPoint(): Double {
    return kotlin.math.round(this * 10.0) / 10.0
}

private fun String.toCount(suffix: String): Long? {
    val digits = filter { it.isDigit() }.toLongOrNull() ?: return null
    val multiplier = when (suffix.lowercase().trim('.')) {
        "k", "\u043a", "\u0442\u044b\u0441" -> 1_000L
        else -> 1L
    }
    return digits * multiplier
}
