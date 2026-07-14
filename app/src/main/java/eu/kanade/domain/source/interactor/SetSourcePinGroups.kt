package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.Source

class SetSourcePinGroups(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source, groups: Set<String>) {
        val newEntries = groups.map { "$it|${source.id}" }.toSet()

        val prevEntries = preferences.groupPinnedSources.get().filter {
            it.substringAfterLast("|") == source.id.toString()
        }.toSet()

        preferences.groupPinnedSources.getAndSet { entries ->
            entries.minus(prevEntries).plus(newEntries)
        }
    }
}
