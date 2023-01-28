package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.getAndSet
import tachiyomi.domain.source.model.Source

class ToggleSourcePin(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source) {
        val isPinned = source.id.toString() in preferences.pinnedSources().get()
        preferences.pinnedSources().getAndSet { pinned ->
            if (isPinned) pinned.minus("${source.id}") else pinned.plus("${source.id}")
        }
    }
}
