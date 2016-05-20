package eu.kanade.tachiyomi.data.database.resolvers

import android.content.ContentValues
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResolver
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.tables.ChapterTable

class ChapterSourceOrderPutResolver : PutResolver<Chapter>() {

    override fun performPut(db: StorIOSQLite, chapter: Chapter) = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(chapter)
        val contentValues = mapToContentValues(chapter)

        val numberOfRowsUpdated = db.internal().update(updateQuery, contentValues)
        PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
    }

    fun mapToUpdateQuery(chapter: Chapter) = UpdateQuery.builder()
            .table(ChapterTable.TABLE)
            .where("${ChapterTable.COLUMN_URL} = ? AND ${ChapterTable.COLUMN_MANGA_ID} = ?")
            .whereArgs(chapter.url, chapter.manga_id)
            .build()

    fun mapToContentValues(chapter: Chapter) = ContentValues(1).apply {
        put(ChapterTable.COLUMN_SOURCE_ORDER, chapter.source_order)
    }

}