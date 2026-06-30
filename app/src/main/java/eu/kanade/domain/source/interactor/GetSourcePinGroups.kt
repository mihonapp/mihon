package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.source.model.Source

class GetSourcePinGroups(
    private val preferences: SourcePreferences,
) {

    fun execute(source: Source): Pair<List<String>, List<Boolean>> {
        val allPinGroups = preferences.groupPinnedSources.get()
            .map { it.substringBeforeLast("|") }
            .distinct()

        val sourcePinnedGroups = preferences.groupPinnedSources.get()
            .filter { it.endsWith("|${source.id}") }
            .map { it.substringBeforeLast("|") }

        return allPinGroups to allPinGroups.map { it in sourcePinnedGroups }
    }
}
