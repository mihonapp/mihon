package mihon.desktop.navigation

sealed interface DesktopRoute

enum class DesktopDestination(val label: String, val shortLabel: String) : DesktopRoute {
    Library("Library", "L"),
    Updates("Updates", "U"),
    History("History", "H"),
    Browse("Browse", "B"),
    Downloads("Downloads", "D"),
    Settings("Settings", "S"),
    About("About", "A"),

    ;

    /** A transient route which is never persisted as the user's shell destination. */
    data class Reader(val chapterId: Long) : DesktopRoute {
        init {
            require(chapterId > 0L) { "chapterId must be positive" }
        }
    }
}
