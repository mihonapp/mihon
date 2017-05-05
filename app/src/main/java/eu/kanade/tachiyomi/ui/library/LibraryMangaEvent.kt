package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga

class LibraryMangaEvent(val mangas: Map<Int, List<Manga>>) {

    fun getMangaForCategory(category: Category): List<Manga>? {
        return mangas[category.id]
    }
}
