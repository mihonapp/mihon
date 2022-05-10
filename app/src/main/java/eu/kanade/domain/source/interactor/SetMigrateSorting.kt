package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class SetMigrateSorting(
    private val preferences: PreferencesHelper,
) {

    fun await(mode: Mode, isAscending: Boolean) {
        val direction = if (isAscending) Direction.ASCENDING else Direction.DESCENDING
        preferences.migrationSortingDirection().set(direction)
        preferences.migrationSortingMode().set(mode)
    }

    enum class Mode {
        ALPHABETICAL,
        TOTAL;
    }

    enum class Direction {
        ASCENDING,
        DESCENDING;
    }
}
