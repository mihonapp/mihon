package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class SetMigrateSorting(
    private val preferences: PreferencesHelper,
) {

    fun await(mode: Mode, direction: Direction) {
        preferences.migrationSortingMode().set(mode)
        preferences.migrationSortingDirection().set(direction)
    }

    enum class Mode {
        ALPHABETICAL,
        TOTAL,
        ;
    }

    enum class Direction {
        ASCENDING,
        DESCENDING,
        ;
    }
}
