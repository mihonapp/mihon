package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.LibraryMangaGetResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaCoverLastModifiedPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaFavoritePutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaFlagsPutResolver
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable

interface MangaQueries : DbProvider {

    fun getLibraryMangas() = db.get()
        .listOfObjects(LibraryManga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(libraryQuery)
                .observesTables(MangaTable.TABLE, ChapterTable.TABLE, MangaCategoryTable.TABLE, CategoryTable.TABLE)
                .build(),
        )
        .withGetResolver(LibraryMangaGetResolver.INSTANCE)
        .prepare()

    fun getFavoriteMangas(sortByTitle: Boolean = true): PreparedGetListOfObjects<Manga> {
        var queryBuilder = Query.builder()
            .table(MangaTable.TABLE)
            .where("${MangaTable.COL_FAVORITE} = ?")
            .whereArgs(1)

        if (sortByTitle) {
            queryBuilder = queryBuilder.orderBy(MangaTable.COL_TITLE)
        }

        return db.get()
            .listOfObjects(Manga::class.java)
            .withQuery(queryBuilder.build())
            .prepare()
    }

    fun getManga(url: String, sourceId: Long) = db.get()
        .`object`(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .where("${MangaTable.COL_URL} = ? AND ${MangaTable.COL_SOURCE} = ?")
                .whereArgs(url, sourceId)
                .build(),
        )
        .prepare()

    fun getManga(id: Long) = db.get()
        .`object`(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .where("${MangaTable.COL_ID} = ?")
                .whereArgs(id)
                .build(),
        )
        .prepare()

    fun insertManga(manga: Manga) = db.put().`object`(manga).prepare()

    fun insertMangas(mangas: List<Manga>) = db.put().objects(mangas).prepare()

    fun updateChapterFlags(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaFlagsPutResolver(MangaTable.COL_CHAPTER_FLAGS, Manga::chapter_flags))
        .prepare()

    fun updateChapterFlags(manga: List<Manga>) = db.put()
        .objects(manga)
        .withPutResolver(MangaFlagsPutResolver(MangaTable.COL_CHAPTER_FLAGS, Manga::chapter_flags))
        .prepare()

    fun updateViewerFlags(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaFlagsPutResolver(MangaTable.COL_VIEWER, Manga::viewer_flags))
        .prepare()

    fun updateMangaFavorite(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaFavoritePutResolver())
        .prepare()

    fun updateMangaCoverLastModified(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaCoverLastModifiedPutResolver())
        .prepare()

    fun getLastReadManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getLastReadMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build(),
        )
        .prepare()

    fun getLatestChapterManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getLatestChapterMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build(),
        )
        .prepare()

    fun getChapterFetchDateManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getChapterFetchDateMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build(),
        )
        .prepare()
}
