package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import android.content.Context
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR

enum class GlobalSearchStatus(
    val labelRes: StringResource,
    val mangaStatus: Int,
    vararg val keywords: String,
) {
    ONGOING(MR.strings.ongoing, SManga.ONGOING, "ongoing", "publishing", "releasing"),
    COMPLETED(MR.strings.completed, SManga.COMPLETED, "completed", "complete", "finished"),
    HIATUS(MR.strings.on_hiatus, SManga.ON_HIATUS, "hiatus", "on hiatus", "paused"),
    CANCELLED(MR.strings.cancelled, SManga.CANCELLED, "cancelled", "canceled", "discontinued", "dropped", "axed"),
    ;

    fun allKeywords(localizedLabel: String? = null): List<String> =
        if (localizedLabel.isNullOrBlank()) keywords.toList() else keywords.toList() + localizedLabel

    fun matches(text: String, localizedLabel: String? = null): Boolean =
        allKeywords(localizedLabel).any { text.trim().equals(it, ignoreCase = true) }

    fun containedIn(text: String, localizedLabel: String? = null): Boolean =
        allKeywords(localizedLabel).any { text.contains(it, ignoreCase = true) }

    companion object {
        fun fromString(value: String, context: Context? = null): GlobalSearchStatus? {
            val normalized = value.replace('_', ' ').trim()
            return entries.find { status ->
                val localized = context?.stringResource(status.labelRes)
                status.matches(normalized, localized)
            }
        }
    }
}

data class GlobalSearchFilters(
    val statuses: Set<GlobalSearchStatus> = emptySet(),
    val includedTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val sortKeyword: String? = null,
) {
    val isEmpty: Boolean
        get() = statuses.isEmpty() && includedTags.isEmpty() && excludedTags.isEmpty() && sortKeyword == null
}

data class ParsedGlobalQuery(
    val cleanQuery: String,
    val filters: GlobalSearchFilters,
    val invalidTokens: List<String> = emptyList(),
)

private val tokenRegex = Regex("""(?<!\S)(-?(?:status|tag|genre|sort)):("[^"]+"|[^\s]+)""", RegexOption.IGNORE_CASE)

/**
 * Parses tokens like:
 *   solo leveling status:completed tag:action -tag:harem tag:"slice of life" sort:popular
 */
fun parseGlobalSearchQuery(raw: String, context: Context? = null): ParsedGlobalQuery {
    val statuses = linkedSetOf<GlobalSearchStatus>()
    val includedTags = linkedSetOf<String>()
    val excludedTags = linkedSetOf<String>()
    var sortKeyword: String? = null
    val invalidTokens = mutableListOf<String>()
    val rangesToRemove = mutableListOf<IntRange>()

    for (match in tokenRegex.findAll(raw)) {
        val rawKey = match.groupValues[1].lowercase()
        val rawVal = match.groupValues[2].removeSurrounding("\"").replace('_', ' ').trim()
        val isNegated = rawKey.startsWith("-")
        val key = rawKey.removePrefix("-")

        when (key) {
            "status" -> {
                val parsed = GlobalSearchStatus.fromString(rawVal, context)
                if (parsed != null) {
                    statuses += parsed
                    rangesToRemove += match.range
                } else {
                    invalidTokens += match.value
                }
            }
            "tag", "genre" -> {
                if (rawVal.isNotEmpty()) {
                    if (isNegated) excludedTags += rawVal else includedTags += rawVal
                    rangesToRemove += match.range
                }
            }
            "sort" -> {
                if (rawVal.isNotEmpty()) {
                    sortKeyword = rawVal
                    rangesToRemove += match.range
                }
            }
        }
    }

    var cleaned = raw
    for (range in rangesToRemove.asReversed()) {
        cleaned = cleaned.removeRange(range)
    }
    cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

    return ParsedGlobalQuery(
        cleanQuery = cleaned,
        filters = GlobalSearchFilters(
            statuses = statuses,
            includedTags = includedTags,
            excludedTags = excludedTags,
            sortKeyword = sortKeyword,
        ),
        invalidTokens = invalidTokens,
    )
}

/** Tracks which requested filters the extension natively supported vs. which need client-side fallback. */
data class FilterApplyReport(
    val statusApplied: Boolean,
    val appliedTags: Set<String>,
    val sortApplied: Boolean,
)

fun FilterList.applyGlobalFilters(
    filters: GlobalSearchFilters,
    context: Context? = null,
): FilterApplyReport {
    val statusApplied = if (filters.statuses.isEmpty()) {
        true
    } else {
        filters.statuses.any { applySingleStatus(it, context) }
    }

    val appliedTags = mutableSetOf<String>()
    for (tag in filters.includedTags) {
        if (applyTagFilter(tag, include = true)) appliedTags += tag.lowercase()
    }
    for (tag in filters.excludedTags) {
        if (applyTagFilter(tag, include = false)) appliedTags += tag.lowercase()
    }

    val sortApplied = filters.sortKeyword?.let { applySortFilter(it) } ?: true

    return FilterApplyReport(
        statusApplied = statusApplied,
        appliedTags = appliedTags,
        sortApplied = sortApplied,
    )
}

private fun FilterList.applySingleStatus(status: GlobalSearchStatus, context: Context?): Boolean {
    val localized = context?.stringResource(status.labelRes)
    fun isNonPublicationName(name: String): Boolean =
        name.contains("scanlation", ignoreCase = true) ||
            name.contains("translation", ignoreCase = true) ||
            name.contains("upload", ignoreCase = true)

    fun isStatusName(name: String): Boolean =
        name.contains("status", ignoreCase = true) && !isNonPublicationName(name)

    fun applyTo(filter: Filter<*>, insideStatusGroup: Boolean): Boolean {
        if (isNonPublicationName(filter.name)) return false
        return when (filter) {
            is Filter.Group<*> -> {
                val groupIsStatus = insideStatusGroup || isStatusName(filter.name)
                filter.state.filterIsInstance<Filter<*>>().any { applyTo(it, groupIsStatus) }
            }
            is Filter.Select<*> -> {
                val options = filter.values.filterIsInstance<String>()
                val knownOptions = GlobalSearchStatus.entries.count { s ->
                    val loc = context?.stringResource(s.labelRes)
                    options.any { s.matches(it, loc) }
                }
                if (!insideStatusGroup && !isStatusName(filter.name) && knownOptions < 2) return false
                val index = options.indexOfFirst { status.matches(it, localized) }
                if (index == -1) return false
                filter.state = index
                true
            }
            is Filter.CheckBox -> {
                val namedAfterStatus = GlobalSearchStatus.entries.any {
                    it.matches(filter.name, context?.stringResource(it.labelRes))
                }
                val wanted = (insideStatusGroup && status.containedIn(filter.name, localized)) ||
                    status.matches(filter.name, localized)
                if ((insideStatusGroup || namedAfterStatus) && wanted) {
                    filter.state = true
                    true
                } else {
                    false
                }
            }
            is Filter.TriState -> {
                val namedAfterStatus = GlobalSearchStatus.entries.any {
                    it.matches(filter.name, context?.stringResource(it.labelRes))
                }
                val wanted = (insideStatusGroup && status.containedIn(filter.name, localized)) ||
                    status.matches(filter.name, localized)
                if ((insideStatusGroup || namedAfterStatus) && wanted) {
                    filter.state = Filter.TriState.STATE_INCLUDE
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    return any { applyTo(it, insideStatusGroup = false) }
}

private fun FilterList.applyTagFilter(tag: String, include: Boolean): Boolean {
    fun visit(filter: Filter<*>): Boolean = when (filter) {
        is Filter.Group<*> -> filter.state.filterIsInstance<Filter<*>>().any { visit(it) }
        is Filter.TriState -> {
            if (filter.name.trim().equals(tag, ignoreCase = true)) {
                filter.state = if (include) Filter.TriState.STATE_INCLUDE else Filter.TriState.STATE_EXCLUDE
                true
            } else {
                false
            }
        }
        is Filter.CheckBox -> {
            if (include && filter.name.trim().equals(tag, ignoreCase = true)) {
                filter.state = true
                true
            } else {
                false
            }
        }
        is Filter.Select<*> -> {
            if (!include) return false
            val options = filter.values.filterIsInstance<String>()
            val idx = options.indexOfFirst { it.trim().equals(tag, ignoreCase = true) }
            if (idx != -1) {
                filter.state = idx
                true
            } else {
                false
            }
        }
        else -> false
    }
    return any { visit(it) }
}

private fun FilterList.applySortFilter(keyword: String): Boolean {
    for (filter in this) {
        when (filter) {
            is Filter.Sort -> {
                val idx = filter.values.indexOfFirst { it.contains(keyword, ignoreCase = true) }
                if (idx != -1) {
                    filter.state = Filter.Sort.Selection(index = idx, ascending = false)
                    return true
                }
            }
            is Filter.Select<*> -> {
                if (filter.name.contains("sort", ignoreCase = true) ||
                    filter.name.contains("order", ignoreCase = true)
                ) {
                    val options = filter.values.filterIsInstance<String>()
                    val idx = options.indexOfFirst { it.contains(keyword, ignoreCase = true) }
                    if (idx != -1) {
                        filter.state = idx
                        return true
                    }
                }
            }
            else -> {}
        }
    }
    return false
}

/**
 * Client-side fallback when an extension lacks a native filter for status or tags.
 * Only filters out manga when the extension actually populated metadata on the search result;
 * if metadata is missing (e.g. status == SManga.UNKNOWN or genre == null), keeps the item
 * so valid results aren't falsely hidden.
 */
fun List<Manga>.applyClientSideFallback(
    filters: GlobalSearchFilters,
    report: FilterApplyReport,
): List<Manga> {
    if (filters.isEmpty) return this

    val targetStatuses = filters.statuses.map { it.mangaStatus }.toSet()
    val unappliedIncludes = filters.includedTags.filter { it.lowercase() !in report.appliedTags }
    val unappliedExcludes = filters.excludedTags.filter { it.lowercase() !in report.appliedTags }

    return filter { manga ->
        val statusOk = if (!report.statusApplied && targetStatuses.isNotEmpty()) {
            manga.status == SManga.UNKNOWN.toLong() || manga.status.toInt() in targetStatuses
        } else {
            true
        }

        val genres = manga.genre.orEmpty().map { it.trim().lowercase() }
        val includeOk = if (unappliedIncludes.isNotEmpty() && genres.isNotEmpty()) {
            unappliedIncludes.all { inc -> genres.any { it == inc.lowercase() } }
        } else {
            true
        }
        val excludeOk = if (unappliedExcludes.isNotEmpty() && genres.isNotEmpty()) {
            unappliedExcludes.none { exc -> genres.any { it == exc.lowercase() } }
        } else {
            true
        }

        statusOk && includeOk && excludeOk
    }
}
