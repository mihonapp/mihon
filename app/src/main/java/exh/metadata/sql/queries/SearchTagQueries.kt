package exh.metadata.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.inTransaction
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.tables.SearchTagTable

interface SearchTagQueries : DbProvider {
    fun getSearchTagsForManga(mangaId: Long) = db.get()
            .listOfObjects(SearchTag::class.java)
            .withQuery(Query.builder()
                    .table(SearchTagTable.TABLE)
                    .where("${SearchTagTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build())
            .prepare()

    fun deleteSearchTagsForManga(mangaId: Long) = db.delete()
            .byQuery(DeleteQuery.builder()
                    .table(SearchTagTable.TABLE)
                    .where("${SearchTagTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build())
            .prepare()

    fun insertSearchTag(searchTag: SearchTag) = db.put().`object`(searchTag).prepare()

    fun insertSearchTags(searchTags: List<SearchTag>) = db.put().objects(searchTags).prepare()

    fun deleteSearchTag(searchTag: SearchTag) = db.delete().`object`(searchTag).prepare()

    fun deleteAllSearchTags() = db.delete().byQuery(DeleteQuery.builder()
            .table(SearchTagTable.TABLE)
            .build())
            .prepare()

    fun setSearchTagsForManga(mangaId: Long, tags: List<SearchTag>) {
        db.inTransaction {
            deleteSearchTagsForManga(mangaId).executeAsBlocking()
            tags.chunked(100) { chunk ->
                insertSearchTags(chunk).executeAsBlocking()
            }
        }
    }
}