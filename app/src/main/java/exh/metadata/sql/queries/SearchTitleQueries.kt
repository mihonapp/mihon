package exh.metadata.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.inTransaction
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTitle
import exh.metadata.sql.tables.SearchTitleTable

interface SearchTitleQueries : DbProvider {
    fun getSearchTitlesForManga(mangaId: Long) = db.get()
            .listOfObjects(SearchTitle::class.java)
            .withQuery(Query.builder()
                    .table(SearchTitleTable.TABLE)
                    .where("${SearchTitleTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build())
            .prepare()

    fun deleteSearchTitlesForManga(mangaId: Long) = db.delete()
            .byQuery(DeleteQuery.builder()
                    .table(SearchTitleTable.TABLE)
                    .where("${SearchTitleTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build())
            .prepare()

    fun insertSearchTitle(searchTitle: SearchTitle) = db.put().`object`(searchTitle).prepare()

    fun insertSearchTitles(searchTitles: List<SearchTitle>) = db.put().objects(searchTitles).prepare()

    fun deleteSearchTitle(searchTitle: SearchTitle) = db.delete().`object`(searchTitle).prepare()

    fun deleteAllSearchTitle() = db.delete().byQuery(DeleteQuery.builder()
            .table(SearchTitleTable.TABLE)
            .build())
            .prepare()

    fun setSearchTitlesForManga(mangaId: Long, titles: List<SearchTitle>) {
        db.inTransaction {
            deleteSearchTitlesForManga(mangaId).executeAsBlocking()
            titles.chunked(100) { chunk ->
                insertSearchTitles(chunk).executeAsBlocking()
            }
        }
    }
}