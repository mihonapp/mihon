package eu.kanade.tachiyomi.event

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga

class LibraryMangasEvent(val mangas: Map<Int, List<Manga>>) {

    fun getMangasForCategory(category: Category): List<Manga>? {
        return mangas[category.id]
    }
}
