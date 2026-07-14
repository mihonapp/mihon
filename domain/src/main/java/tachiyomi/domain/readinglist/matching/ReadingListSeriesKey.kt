package tachiyomi.domain.readinglist.matching

import tachiyomi.domain.readinglist.normalization.TitleNormalizer
import java.util.Locale

object ReadingListSeriesKey {

    fun from(
        seriesTitle: String,
        volume: String? = null,
        year: String? = null,
    ): String {
        require(seriesTitle.isNotBlank()) {
            "Reading-list series title cannot be blank"
        }

        val normalized = TitleNormalizer.normalize(seriesTitle)
        val titleKey = normalized.articlelessBase
            .ifBlank { normalized.base }
            .ifBlank { seriesTitle.trim().lowercase(Locale.ROOT) }
        val yearKey = year.toMetadataKey() ?: normalized.year?.toString()
        val volumeKey = volume.toMetadataKey() ?: normalized.volume?.toString()

        return buildList {
            add(titleKey)
            yearKey?.let { value -> add("year=$value") }
            volumeKey?.let { value -> add("volume=$value") }
        }.joinToString(separator = "|")
    }

    private fun String?.toMetadataKey(): String? {
        val value = this
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        return value.toLongOrNull()?.toString()
            ?: value
                .lowercase(Locale.ROOT)
                .replace(WHITESPACE, " ")
                .trim()
    }

    private val WHITESPACE = Regex("""\s+""")
}
