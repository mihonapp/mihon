package eu.kanade.tachiyomi.data.database.queries

import android.util.Pair
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.inTransaction
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.database.resolvers.ChapterProgressPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaChapterGetResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.util.ChapterRecognition
import rx.Observable
import java.util.*

interface ChapterQueries : DbProvider {

    fun getChapters(manga: Manga) = db.get()
            .listOfObjects(Chapter::class.java)
            .withQuery(Query.builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COLUMN_MANGA_ID} = ?")
                    .whereArgs(manga.id)
                    .build())
            .prepare()

    fun getRecentChapters(date: Date) = db.get()
            .listOfObjects(MangaChapter::class.java)
            .withQuery(RawQuery.builder()
                    .query(getRecentsQuery())
                    .args(date.time)
                    .observesTables(ChapterTable.TABLE)
                    .build())
            .withGetResolver(MangaChapterGetResolver.INSTANCE)
            .prepare()

    fun getNextChapter(chapter: Chapter): PreparedGetObject<Chapter> {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        val chapterNumber = chapter.chapter_number + 0.00001

        return db.get()
                .`object`(Chapter::class.java)
                .withQuery(Query.builder()
                        .table(ChapterTable.TABLE)
                        .where("${ChapterTable.COLUMN_MANGA_ID} = ? AND " +
                                "${ChapterTable.COLUMN_CHAPTER_NUMBER} > ? AND " +
                                "${ChapterTable.COLUMN_CHAPTER_NUMBER} <= ?")
                        .whereArgs(chapter.manga_id, chapterNumber, chapterNumber + 1)
                        .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER)
                        .limit(1)
                        .build())
                .prepare()
    }

    fun getPreviousChapter(chapter: Chapter): PreparedGetObject<Chapter> {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        val chapterNumber = chapter.chapter_number - 0.00001

        return db.get()
                .`object`(Chapter::class.java)
                .withQuery(Query.builder().table(ChapterTable.TABLE)
                        .where("${ChapterTable.COLUMN_MANGA_ID} = ? AND " +
                                "${ChapterTable.COLUMN_CHAPTER_NUMBER} < ? AND " +
                                "${ChapterTable.COLUMN_CHAPTER_NUMBER} >= ?")
                        .whereArgs(chapter.manga_id, chapterNumber, chapterNumber - 1)
                        .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER + " DESC")
                        .limit(1)
                        .build())
                .prepare()
    }

    fun getNextUnreadChapter(manga: Manga) = db.get()
            .`object`(Chapter::class.java)
            .withQuery(Query.builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COLUMN_MANGA_ID} = ? AND " +
                            "${ChapterTable.COLUMN_READ} = ? AND " +
                            "${ChapterTable.COLUMN_CHAPTER_NUMBER} >= ?")
                    .whereArgs(manga.id, 0, 0)
                    .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER)
                    .limit(1)
                    .build())
            .prepare()

    fun insertChapter(chapter: Chapter) = db.put().`object`(chapter).prepare()

    fun insertChapters(chapters: List<Chapter>) = db.put().objects(chapters).prepare()

    // TODO this logic shouldn't be here
    // Add new chapters or delete if the source deletes them
    open fun insertOrRemoveChapters(manga: Manga, sourceChapters: List<Chapter>, source: Source): Observable<Pair<Int, Int>> {
        val dbChapters = getChapters(manga).executeAsBlocking()

        val newChapters = Observable.from(sourceChapters)
                .filter { it !in dbChapters }
                .doOnNext { c ->
                    c.manga_id = manga.id
                    source.parseChapterNumber(c)
                    ChapterRecognition.parseChapterNumber(c, manga)
                }.toList()

        val deletedChapters = Observable.from(dbChapters)
                .filter { it !in sourceChapters }
                .toList()

        return Observable.zip(newChapters, deletedChapters) { toAdd, toDelete ->
            var added = 0
            var deleted = 0
            var readded = 0

            db.inTransaction {
                val deletedReadChapterNumbers = TreeSet<Float>()
                if (!toDelete.isEmpty()) {
                    for (c in toDelete) {
                        if (c.read) {
                            deletedReadChapterNumbers.add(c.chapter_number)
                        }
                    }
                    deleted = deleteChapters(toDelete).executeAsBlocking().results().size
                }

                if (!toAdd.isEmpty()) {
                    // Set the date fetch for new items in reverse order to allow another sorting method.
                    // Sources MUST return the chapters from most to less recent, which is common.
                    var now = Date().time

                    for (i in toAdd.indices.reversed()) {
                        val c = toAdd[i]
                        c.date_fetch = now++
                        // Try to mark already read chapters as read when the source deletes them
                        if (c.chapter_number != -1f && c.chapter_number in deletedReadChapterNumbers) {
                            c.read = true
                            readded++
                        }
                    }
                    added = insertChapters(toAdd).executeAsBlocking().numberOfInserts()
                }
            }
            Pair.create(added - readded, deleted - readded)
        }
    }

    fun deleteChapter(chapter: Chapter) = db.delete().`object`(chapter).prepare()

    fun deleteChapters(chapters: List<Chapter>) = db.delete().objects(chapters).prepare()

    fun updateChapterProgress(chapter: Chapter) = db.put()
            .`object`(chapter)
            .withPutResolver(ChapterProgressPutResolver.instance)
            .prepare()

}