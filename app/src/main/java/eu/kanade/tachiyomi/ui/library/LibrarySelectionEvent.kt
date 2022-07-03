package eu.kanade.tachiyomi.ui.library

import eu.kanade.domain.manga.model.Manga

sealed class LibrarySelectionEvent {
    class Selected(val manga: Manga) : LibrarySelectionEvent()
    class Unselected(val manga: Manga) : LibrarySelectionEvent()
    object Cleared : LibrarySelectionEvent()
}
