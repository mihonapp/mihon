package exh.metadata.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.tables.SearchMetadataTable

interface SearchMetadataQueries : DbProvider {

    fun getSearchMetadataForManga(mangaId: Long) = db.get()
            .`object`(SearchMetadata::class.java)
            .withQuery(Query.builder()
                    .table(SearchMetadataTable.TABLE)
                    .where("${SearchMetadataTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build())
            .prepare()

    fun getSearchMetadata() = db.get()
            .listOfObjects(SearchMetadata::class.java)
            .withQuery(Query.builder()
                    .table(SearchMetadataTable.TABLE)
                    .build())
            .prepare()

    fun getSearchMetadataByIndexedExtra(extra: String) = db.get()
            .listOfObjects(SearchMetadata::class.java)
            .withQuery(Query.builder()
                    .table(SearchMetadataTable.TABLE)
                    .where("${SearchMetadataTable.COL_INDEXED_EXTRA} = ?")
                    .whereArgs(extra)
                    .build())
            .prepare()

    fun insertSearchMetadata(metadata: SearchMetadata) = db.put().`object`(metadata).prepare()

    fun deleteSearchMetadata(metadata: SearchMetadata) = db.delete().`object`(metadata).prepare()

    fun deleteAllSearchMetadata() = db.delete().byQuery(DeleteQuery.builder()
            .table(SearchMetadataTable.TABLE)
            .build())
            .prepare()
}