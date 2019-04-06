package exh.metadata.sql.mappers

import android.content.ContentValues
import android.database.Cursor
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.tables.SearchMetadataTable.COL_EXTRA
import exh.metadata.sql.tables.SearchMetadataTable.COL_EXTRA_VERSION
import exh.metadata.sql.tables.SearchMetadataTable.COL_INDEXED_EXTRA
import exh.metadata.sql.tables.SearchMetadataTable.COL_MANGA_ID
import exh.metadata.sql.tables.SearchMetadataTable.COL_UPLOADER
import exh.metadata.sql.tables.SearchMetadataTable.TABLE

class SearchMetadataTypeMapping : SQLiteTypeMapping<SearchMetadata>(
        SearchMetadataPutResolver(),
        SearchMetadataGetResolver(),
        SearchMetadataDeleteResolver()
)

class SearchMetadataPutResolver : DefaultPutResolver<SearchMetadata>() {

    override fun mapToInsertQuery(obj: SearchMetadata) = InsertQuery.builder()
            .table(TABLE)
            .build()

    override fun mapToUpdateQuery(obj: SearchMetadata) = UpdateQuery.builder()
            .table(TABLE)
            .where("$COL_MANGA_ID = ?")
            .whereArgs(obj.mangaId)
            .build()

    override fun mapToContentValues(obj: SearchMetadata) = ContentValues(5).apply {
        put(COL_MANGA_ID, obj.mangaId)
        put(COL_UPLOADER, obj.uploader)
        put(COL_EXTRA, obj.extra)
        put(COL_INDEXED_EXTRA, obj.indexedExtra)
        put(COL_EXTRA_VERSION, obj.extraVersion)
    }
}

class SearchMetadataGetResolver : DefaultGetResolver<SearchMetadata>() {

    override fun mapFromCursor(cursor: Cursor): SearchMetadata = SearchMetadata(
            mangaId = cursor.getLong(cursor.getColumnIndex(COL_MANGA_ID)),
            uploader = cursor.getString(cursor.getColumnIndex(COL_UPLOADER)),
            extra = cursor.getString(cursor.getColumnIndex(COL_EXTRA)),
            indexedExtra = cursor.getString(cursor.getColumnIndex(COL_INDEXED_EXTRA)),
            extraVersion = cursor.getInt(cursor.getColumnIndex(COL_EXTRA_VERSION))
    )
}

class SearchMetadataDeleteResolver : DefaultDeleteResolver<SearchMetadata>() {

    override fun mapToDeleteQuery(obj: SearchMetadata) = DeleteQuery.builder()
            .table(TABLE)
            .where("$COL_MANGA_ID = ?")
            .whereArgs(obj.mangaId)
            .build()
}
