package eu.kanade.tachiyomi.data.database.models

import eu.kanade.domain.category.model.Category as DomainCategory
import eu.kanade.domain.manga.model.Manga as DomainManga

class MangaCategory {

    var id: Long? = null

    var manga_id: Long = 0

    var category_id: Int = 0

    companion object {

        fun create(manga: DomainManga, category: DomainCategory): MangaCategory {
            val mc = MangaCategory()
            mc.manga_id = manga.id
            mc.category_id = category.id.toInt()
            return mc
        }

        fun create(manga: Manga, category: Category): MangaCategory {
            val mc = MangaCategory()
            mc.manga_id = manga.id!!
            mc.category_id = category.id!!
            return mc
        }
    }
}
