package eu.kanade.tachiyomi.widget

import tachiyomi.domain.manga.model.TriStateFilter

// TODO: replace this with TriStateFilter entirely
enum class TriState(val value: Int) {
    DISABLED(0),
    ENABLED_IS(1),
    ENABLED_NOT(2),
    ;

    fun next(): TriState {
        return when (this) {
            DISABLED -> ENABLED_IS
            ENABLED_IS -> ENABLED_NOT
            ENABLED_NOT -> DISABLED
        }
    }

    companion object {
        fun valueOf(value: Int): TriState {
            return TriState.values().first { it.value == value }
        }
    }
}

fun Int.toTriStateFilter(): TriStateFilter {
    return when (this) {
        TriState.DISABLED.value -> TriStateFilter.DISABLED
        TriState.ENABLED_IS.value -> TriStateFilter.ENABLED_IS
        TriState.ENABLED_NOT.value -> TriStateFilter.ENABLED_NOT
        else -> throw IllegalStateException("Unknown TriStateGroup state: $this")
    }
}
