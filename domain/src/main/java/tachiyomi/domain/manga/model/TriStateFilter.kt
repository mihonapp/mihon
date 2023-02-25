package tachiyomi.domain.manga.model

enum class TriStateFilter {
    DISABLED, // Disable filter
    ENABLED_IS, // Enabled with "is" filter
    ENABLED_NOT, // Enabled with "not" filter
    ;

    fun next(): TriStateFilter {
        return when (this) {
            DISABLED -> ENABLED_IS
            ENABLED_IS -> ENABLED_NOT
            ENABLED_NOT -> DISABLED
        }
    }
}

inline fun applyFilter(filter: TriStateFilter, predicate: () -> Boolean): Boolean = when (filter) {
    TriStateFilter.DISABLED -> true
    TriStateFilter.ENABLED_IS -> predicate()
    TriStateFilter.ENABLED_NOT -> !predicate()
}
