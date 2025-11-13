package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences

class SetMigrateSorting(
    private val preferences: SourcePreferences,
) {

    fun await(mode: Mode, direction: Direction) {
        preferences.migrationSortingMode().set(mode)
        preferences.migrationSortingDirection().set(direction)
    }

    enum class Mode {
        ALPHABETICAL,
        TOTAL,
    }

    enum class Direction {
        ASCENDING,
        DESCENDING,
    }
}
