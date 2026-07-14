package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class DeleteSourcePinGroup(
    private val preferences: SourcePreferences,
) {

    fun await(group: String) {
        preferences.groupPinnedSources.getAndSet { entries ->
            entries.filterNot { it.substringBeforeLast("|") == group }
                .toSet()
        }
    }
}
