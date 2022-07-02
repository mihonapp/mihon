package eu.kanade.tachiyomi.ui.library

import eu.kanade.domain.category.model.Category

class LibraryMangaEvent(val mangas: LibraryMap) {

    fun getMangaForCategory(category: Category): List<LibraryItem>? {
        return mangas[category.id]
    }
}
