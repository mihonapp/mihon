package mihon.desktop.navigation

sealed interface DesktopRoute

enum class DesktopDestination(val label: String, val shortLabel: String) : DesktopRoute {
    Library("Library", "L"),
    Updates("Updates", "U"),
    History("History", "H"),
    Browse("Browse", "B"),
    Downloads("Downloads", "D"),
    Stats("Stats", "St"),
    Settings("Settings", "S"),
    About("About", "A"),

    ;

    /** A transient route which is never persisted as the user's shell destination. */
    data class Reader(val chapterId: Long) : DesktopRoute {
        init {
            require(chapterId > 0L) { "chapterId must be positive" }
        }
    }

    /** Details opened outside the library, including books that have not been favorited. */
    data class MangaDetails(val mangaId: Long) : DesktopRoute {
        init {
            require(mangaId > 0L) { "mangaId must be positive" }
        }
    }

    /**
     * A transient route for the Upcoming calendar. It is opened from Updates and is intentionally
     * not part of [entries] so it can never be persisted as the shell destination.
     */
    data object Upcoming : DesktopRoute
}
