package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.database.models.Manga

sealed class LibrarySelectionEvent {

    class Selected(val manga: Manga) : LibrarySelectionEvent()
    class Unselected(val manga: Manga) : LibrarySelectionEvent()
    class Cleared() : LibrarySelectionEvent()
}