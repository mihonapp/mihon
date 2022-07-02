package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.tables.CategoryTable

interface CategoryQueries : DbProvider {

    fun getCategories() = db.get()
        .listOfObjects(Category::class.java)
        .withQuery(
            Query.builder()
                .table(CategoryTable.TABLE)
                .orderBy(CategoryTable.COL_ORDER)
                .build(),
        )
        .prepare()

    fun getCategoriesForManga(mangaId: Long) = db.get()
        .listOfObjects(Category::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getCategoriesForMangaQuery())
                .args(mangaId)
                .build(),
        )
        .prepare()
}
