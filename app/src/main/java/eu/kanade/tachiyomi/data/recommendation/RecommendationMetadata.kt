package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import java.net.URI
import java.text.Normalizer
import java.util.Locale

internal object RecommendationMetadata {
    private val creatorSeparator = Regex("""\s*(?:,|;|\r?\n|\u3001|\uFF0C|\uFF1B)\s*""")
    private val tagSeparator = Regex("""\s*(?:,|;|\r?\n|\t|\u3001|\uFF0C|\uFF1B|\|)\s*""")
    private val punctuationOrWhitespace = Regex("""[\p{P}\p{S}\s]+""")
    private val whitespace = Regex("""\s+""")
    private val explicitGroupLine = Regex(
        """^\s*(?:circle|group|\u793E\u56E2|\u793E\u5718|\u30B5\u30FC\u30AF\u30EB)\s*[:\uFF1A]\s*(.+?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val trackingQueryNames = setOf("gclid", "fbclid")

    fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(punctuationOrWhitespace, " ")
            .trim()
            .replace(whitespace, " ")
    }

    fun extractCreators(manga: SManga, groups: Collection<String> = emptyList()): Set<CreatorIdentity> {
        val creators = linkedMapOf<String, MutableCreator>()
        addCreators(creators, manga.author, CreatorRole.AUTHOR)
        addCreators(creators, manga.artist, CreatorRole.ARTIST)
        (groups + extractExplicitGroups(manga.description)).forEach {
            addCreators(creators, it, CreatorRole.GROUP)
        }
        return creators.values.mapTo(linkedSetOf()) { creator ->
            CreatorIdentity(creator.displayName, creator.normalizedName, creator.roles)
        }
    }

    fun extractTags(manga: SManga): Set<String> = extractTagIdentities(manga).mapTo(linkedSetOf()) {
        it.normalizedName
    }

    fun extractTagIdentities(manga: SManga): List<TagIdentity> {
        val seen = hashSetOf<String>()
        return tagSeparator.split(manga.genre.orEmpty())
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { displayName ->
                val normalized = normalize(displayName)
                normalized.takeIf { it.isNotEmpty() && seen.add(it) }
                    ?.let { TagIdentity(displayName, it) }
            }
            .toList()
    }

    fun extractExplicitGroups(description: String?): List<String> {
        return description.orEmpty().lineSequence()
            .mapNotNull { line -> explicitGroupLine.matchEntire(line)?.groupValues?.get(1) }
            .filter(String::isNotBlank)
            .toList()
    }

    fun identity(
        sourceId: Long,
        manga: SManga,
        series: Collection<String> = emptyList(),
    ): RecommendationIdentity {
        val url = normalizeUrl(manga.url)
        val creators = extractCreators(manga).mapTo(linkedSetOf(), CreatorIdentity::normalizedName)
        val exactTitle = normalize(manga.title)
        return RecommendationIdentity(
            sourceId = sourceId,
            canonicalUrl = url.canonical,
            urlHost = url.host,
            urlPathAndQuery = url.pathAndQuery,
            exactTitle = exactTitle,
            // The generic implementation deliberately avoids stripping volume, language, or
            // edition markers. Any broader identity needs independent series or cover evidence.
            baseTitle = exactTitle,
            creators = creators,
            cover = manga.thumbnail_url?.takeIf(String::isNotBlank)?.let(::normalizeUrl)?.canonical,
            series = series.mapTo(linkedSetOf(), ::normalize).filterTo(linkedSetOf(), String::isNotEmpty),
        )
    }

    fun card(
        sourceId: Long,
        manga: SManga,
        favorite: Boolean = false,
        localId: Long? = null,
        series: Collection<String> = emptyList(),
    ): RecommendationCard {
        return RecommendationCard(
            manga = manga,
            sourceId = sourceId,
            identity = identity(sourceId, manga, series),
            creators = extractCreators(manga),
            tags = extractTags(manga),
            favorite = favorite,
            localId = localId,
        )
    }

    fun sameWork(left: RecommendationIdentity, right: RecommendationIdentity): Boolean {
        if (left.sourceId != right.sourceId) return false
        val compatibleHosts = left.urlHost == null || right.urlHost == null || left.urlHost == right.urlHost
        if (
            compatibleHosts &&
            left.urlPathAndQuery.isNotBlank() &&
            left.urlPathAndQuery == right.urlPathAndQuery
        ) {
            return true
        }
        val sharedCreators = left.creators intersect right.creators
        if (left.exactTitle.isNotBlank() && left.exactTitle == right.exactTitle && sharedCreators.isNotEmpty()) {
            return true
        }
        if (left.baseTitle.isBlank() || left.baseTitle != right.baseTitle || sharedCreators.isEmpty()) return false
        val sameSeries = left.series.isNotEmpty() && (left.series intersect right.series).isNotEmpty()
        val sameCover = left.cover != null && left.cover == right.cover
        return sameSeries || sameCover
    }

    fun creatorsOverlap(left: Set<CreatorIdentity>, right: Set<CreatorIdentity>): Boolean {
        if (left.isEmpty() || right.isEmpty()) return false
        val names = left.mapTo(hashSetOf(), CreatorIdentity::normalizedName)
        return right.any { it.normalizedName in names }
    }

    /**
     * Applies an exact tag value to generic source filters without guessing filter semantics.
     * Text and sort filters are intentionally left untouched.
     */
    fun applyExactTagFilter(filters: FilterList, rawTag: String): Boolean {
        val target = normalize(rawTag)
        if (target.isEmpty()) return false
        return filters.any { applyExactTagFilter(it, target) }
    }

    private fun applyExactTagFilter(filter: Filter<*>, target: String): Boolean {
        return when (filter) {
            is Filter.CheckBox -> {
                if (normalize(filter.name) == target) {
                    filter.state = true
                    true
                } else {
                    false
                }
            }
            is Filter.TriState -> {
                if (normalize(filter.name) == target) {
                    filter.state = Filter.TriState.STATE_INCLUDE
                    true
                } else {
                    false
                }
            }
            is Filter.Select<*> -> {
                val index = filter.values.indexOfFirst { normalize(it.toString()) == target }
                if (index >= 0) {
                    filter.state = index
                    true
                } else {
                    false
                }
            }
            is Filter.Group<*> -> filter.state.filterIsInstance<Filter<*>>()
                .any { applyExactTagFilter(it, target) }
            else -> false
        }
    }

    private fun addCreators(
        result: MutableMap<String, MutableCreator>,
        rawNames: String?,
        role: CreatorRole,
    ) {
        creatorSeparator.split(rawNames.orEmpty()).forEach { rawName ->
            val displayName = rawName.trim()
            val normalizedName = normalize(displayName)
            if (normalizedName.isEmpty()) return@forEach
            val creator = result.getOrPut(normalizedName) {
                MutableCreator(displayName, normalizedName)
            }
            creator.roles += role
        }
    }

    private fun normalizeUrl(rawUrl: String): NormalizedUrl {
        val trimmed = Normalizer.normalize(rawUrl.trim(), Normalizer.Form.NFKC)
        if (trimmed.isEmpty()) return NormalizedUrl("", null, "")
        val protocolRelative = trimmed.startsWith("//")
        val parsed = runCatching { URI(if (protocolRelative) "https:$trimmed" else trimmed) }.getOrNull()
        if (parsed == null) {
            val fallback = trimmed.substringBefore('#')
            return NormalizedUrl(fallback, null, fallback)
        }
        val host = parsed.host?.lowercase(Locale.ROOT)
        val path = parsed.rawPath.orEmpty().ifBlank { "/" }
        val query = parsed.rawQuery
            ?.split('&')
            ?.filter(String::isNotBlank)
            ?.filterNot { part ->
                val name = part.substringBefore('=').lowercase(Locale.ROOT)
                name.startsWith("utm_") || name in trackingQueryNames
            }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        val pathAndQuery = path + query.takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()
        val canonical = host?.let { "//$it$pathAndQuery" } ?: pathAndQuery
        return NormalizedUrl(canonical, host, pathAndQuery)
    }

    private data class MutableCreator(
        val displayName: String,
        val normalizedName: String,
        val roles: MutableSet<CreatorRole> = linkedSetOf(),
    )

    private data class NormalizedUrl(
        val canonical: String,
        val host: String?,
        val pathAndQuery: String,
    )
}
