package eu.kanade.tachiyomi.ui.readinglist

import eu.kanade.domain.base.BasePreferences
import java.util.Locale

internal fun availableReadingListSourceLanguages(
    groups: List<ReadingListSourceGroup>,
): List<String> {
    return groups
        .asSequence()
        .filter(ReadingListSourceGroup::installed)
        .flatMap { group -> group.sources.asSequence() }
        .map { source -> source.language.normalizedLanguageCode() }
        .filter { language -> language.isNotEmpty() && language != LANGUAGE_ALL }
        .distinct()
        .sorted()
        .toList()
}

internal fun filterReadingListSourceGroups(
    groups: List<ReadingListSourceGroup>,
    preferredLanguage: String,
    query: String,
): List<ReadingListSourceGroup> {
    val normalizedLanguage = preferredLanguage.normalizedLanguageCode()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)

    return groups.mapNotNull { group ->
        val languageMatches = group.sources.filter { source ->
            source.matchesLanguage(normalizedLanguage)
        }
        if (languageMatches.isEmpty()) return@mapNotNull null

        val groupMatchesSearch = normalizedQuery.isEmpty() ||
            group.extensionName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            group.packageName.orEmpty().lowercase(Locale.ROOT).contains(normalizedQuery)
        val visibleSources = if (groupMatchesSearch) {
            languageMatches
        } else {
            languageMatches.filter { source ->
                source.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    source.language.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
        if (visibleSources.isEmpty()) return@mapNotNull null

        group.copy(sources = visibleSources)
    }
}

internal fun ReadingListSourceOption.matchesLanguage(preferredLanguage: String): Boolean {
    if (!installed) return true

    val normalizedPreferred = preferredLanguage.normalizedLanguageCode()
    if (normalizedPreferred == BasePreferences.READING_LIST_ALL_LANGUAGES) return true

    val sourceLanguage = language.normalizedLanguageCode()
    return sourceLanguage == normalizedPreferred || sourceLanguage == LANGUAGE_ALL
}

private fun String.normalizedLanguageCode(): String {
    return trim().lowercase(Locale.ROOT)
}

private const val LANGUAGE_ALL = "all"
