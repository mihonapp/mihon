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
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.tables.SearchTagTable.COL_ID
import exh.metadata.sql.tables.SearchTagTable.COL_MANGA_ID
import exh.metadata.sql.tables.SearchTagTable.COL_NAME
import exh.metadata.sql.tables.SearchTagTable.COL_NAMESPACE
import exh.metadata.sql.tables.SearchTagTable.COL_TYPE
import exh.metadata.sql.tables.SearchTagTable.TABLE

class SearchTagTypeMapping : SQLiteTypeMapping<SearchTag>(
        SearchTagPutResolver(),
        SearchTagGetResolver(),
        SearchTagDeleteResolver()
)

class SearchTagPutResolver : DefaultPutResolver<SearchTag>() {

    override fun mapToInsertQuery(obj: SearchTag) = InsertQuery.builder()
            .table(TABLE)
            .build()

    override fun mapToUpdateQuery(obj: SearchTag) = UpdateQuery.builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()

    override fun mapToContentValues(obj: SearchTag) = ContentValues(5).apply {
        put(COL_ID, obj.id)
        put(COL_MANGA_ID, obj.mangaId)
        put(COL_NAMESPACE, obj.namespace)
        put(COL_NAME, obj.name)
        put(COL_TYPE, obj.type)
    }
}

class SearchTagGetResolver : DefaultGetResolver<SearchTag>() {

    override fun mapFromCursor(cursor: Cursor): SearchTag = SearchTag(
            id = cursor.getLong(cursor.getColumnIndex(COL_ID)),
            mangaId = cursor.getLong(cursor.getColumnIndex(COL_MANGA_ID)),
            namespace = cursor.getString(cursor.getColumnIndex(COL_NAMESPACE)),
            name = cursor.getString(cursor.getColumnIndex(COL_NAME)),
            type = cursor.getInt(cursor.getColumnIndex(COL_TYPE))
    )
}

class SearchTagDeleteResolver : DefaultDeleteResolver<SearchTag>() {

    override fun mapToDeleteQuery(obj: SearchTag) = DeleteQuery.builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()
}
