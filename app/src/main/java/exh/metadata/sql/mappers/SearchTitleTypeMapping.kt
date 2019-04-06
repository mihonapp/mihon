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
import exh.metadata.sql.models.SearchTitle
import exh.metadata.sql.tables.SearchTitleTable.COL_ID
import exh.metadata.sql.tables.SearchTitleTable.COL_MANGA_ID
import exh.metadata.sql.tables.SearchTitleTable.COL_TITLE
import exh.metadata.sql.tables.SearchTitleTable.COL_TYPE
import exh.metadata.sql.tables.SearchTitleTable.TABLE

class SearchTitleTypeMapping : SQLiteTypeMapping<SearchTitle>(
        SearchTitlePutResolver(),
        SearchTitleGetResolver(),
        SearchTitleDeleteResolver()
)

class SearchTitlePutResolver : DefaultPutResolver<SearchTitle>() {

    override fun mapToInsertQuery(obj: SearchTitle) = InsertQuery.builder()
            .table(TABLE)
            .build()

    override fun mapToUpdateQuery(obj: SearchTitle) = UpdateQuery.builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()

    override fun mapToContentValues(obj: SearchTitle) = ContentValues(4).apply {
        put(COL_ID, obj.id)
        put(COL_MANGA_ID, obj.mangaId)
        put(COL_TITLE, obj.title)
        put(COL_TYPE, obj.type)
    }
}

class SearchTitleGetResolver : DefaultGetResolver<SearchTitle>() {

    override fun mapFromCursor(cursor: Cursor): SearchTitle = SearchTitle(
            id = cursor.getLong(cursor.getColumnIndex(COL_ID)),
            mangaId = cursor.getLong(cursor.getColumnIndex(COL_MANGA_ID)),
            title = cursor.getString(cursor.getColumnIndex(COL_TITLE)),
            type = cursor.getInt(cursor.getColumnIndex(COL_TYPE))
    )
}

class SearchTitleDeleteResolver : DefaultDeleteResolver<SearchTitle>() {

    override fun mapToDeleteQuery(obj: SearchTitle) = DeleteQuery.builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()
}
