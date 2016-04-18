package eu.kanade.tachiyomi.data.database

import android.content.Context
import android.util.Pair
import com.pushtorefresh.storio.Queries
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.models.*
import eu.kanade.tachiyomi.data.database.resolvers.LibraryMangaGetResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaChapterGetResolver
import eu.kanade.tachiyomi.data.database.tables.*
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.util.ChapterRecognition
import rx.Observable
import java.util.*

open class DatabaseHelper(context: Context) {

    val db = DefaultStorIOSQLite.builder()
            .sqliteOpenHelper(DbOpenHelper(context))
            .addTypeMapping(Manga::class.java, MangaSQLiteTypeMapping())
            .addTypeMapping(Chapter::class.java, ChapterSQLiteTypeMapping())
            .addTypeMapping(MangaSync::class.java, MangaSyncSQLiteTypeMapping())
            .addTypeMapping(Category::class.java, CategorySQLiteTypeMapping())
            .addTypeMapping(MangaCategory::class.java, MangaCategorySQLiteTypeMapping())
            .build()

    inline fun inTransaction(func: DatabaseHelper.() -> Unit) {
        db.internal().beginTransaction()
        try {
            func()
            db.internal().setTransactionSuccessful()
        } finally {
            db.internal().endTransaction()
        }
    }

    // Mangas related queries

    fun getMangas() = db.get()
            .listOfObjects(Manga::class.java)
            .withQuery(Query.builder()
                    .table(MangaTable.TABLE)
                    .build())
            .prepare()

    fun getLibraryMangas() = db.get()
            .listOfObjects(Manga::class.java)
            .withQuery(RawQuery.builder()
                    .query(libraryQuery)
                    .observesTables(MangaTable.TABLE, ChapterTable.TABLE, MangaCategoryTable.TABLE)
                    .build())
            .withGetResolver(LibraryMangaGetResolver.INSTANCE)
            .prepare()

    open fun getFavoriteMangas() = db.get()
            .listOfObjects(Manga::class.java)
            .withQuery(Query.builder()
                    .table(MangaTable.TABLE)
                    .where("${MangaTable.COLUMN_FAVORITE} = ?")
                    .whereArgs(1)
                    .orderBy(MangaTable.COLUMN_TITLE)
                    .build())
            .prepare()

    fun getManga(url: String, sourceId: Int) = db.get()
            .`object`(Manga::class.java)
            .withQuery(Query.builder()
                    .table(MangaTable.TABLE)
                    .where("${MangaTable.COLUMN_URL} = ? AND ${MangaTable.COLUMN_SOURCE} = ?")
                    .whereArgs(url, sourceId)
                    .build())
            .prepare()

    fun getManga(id: Long) = db.get()
            .`object`(Manga::class.java)
            .withQuery(Query.builder()
                    .table(MangaTable.TABLE)
                    .where("${MangaTable.COLUMN_ID} = ?")
                    .whereArgs(id)
                    .build())
            .prepare()

    fun insertManga(manga: Manga) = db.put().`object`(manga).prepare()

    fun insertMangas(mangas: List<Manga>) = db.put().objects(mangas).prepare()

    fun deleteManga(manga: Manga) = db.delete().`object`(manga).prepare()

    fun deleteMangas(mangas: List<Manga>) = db.delete().objects(mangas).prepare()

    fun deleteMangasNotInLibrary() = db.delete()
            .byQuery(DeleteQuery.builder()
                    .table(MangaTable.TABLE)
                    .where("${MangaTable.COLUMN_FAVORITE} = ?")
                    .whereArgs(0)
                    .build())
            .prepare()


    // Chapters related queries

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
                    .query(getRecentsQuery(date))
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

            inTransaction {
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

    // Manga sync related queries

    fun getMangaSync(manga: Manga, sync: MangaSyncService) = db.get()
            .`object`(MangaSync::class.java)
            .withQuery(Query.builder()
                    .table(MangaSyncTable.TABLE)
                    .where("${MangaSyncTable.COLUMN_MANGA_ID} = ? AND " +
                            "${MangaSyncTable.COLUMN_SYNC_ID} = ?")
                    .whereArgs(manga.id, sync.id)
                    .build())
            .prepare()

    fun getMangasSync(manga: Manga) = db.get()
            .listOfObjects(MangaSync::class.java)
            .withQuery(Query.builder()
                    .table(MangaSyncTable.TABLE)
                    .where("${MangaSyncTable.COLUMN_MANGA_ID} = ?")
                    .whereArgs(manga.id)
                    .build())
            .prepare()

    fun insertMangaSync(manga: MangaSync) = db.put().`object`(manga).prepare()

    fun insertMangasSync(mangas: List<MangaSync>) = db.put().objects(mangas).prepare()

    fun deleteMangaSync(manga: MangaSync) = db.delete().`object`(manga).prepare()

    fun deleteMangaSyncForManga(manga: Manga) = db.delete()
            .byQuery(DeleteQuery.builder()
                    .table(MangaSyncTable.TABLE)
                    .where("${MangaSyncTable.COLUMN_MANGA_ID} = ?")
                    .whereArgs(manga.id)
                    .build())
            .prepare()

    // Categories related queries

    fun getCategories() = db.get()
            .listOfObjects(Category::class.java)
            .withQuery(Query.builder()
                    .table(CategoryTable.TABLE)
                    .orderBy(CategoryTable.COLUMN_ORDER)
                    .build())
            .prepare()

    fun getCategoriesForManga(manga: Manga) = db.get()
            .listOfObjects(Category::class.java)
            .withQuery(RawQuery.builder()
                    .query(getCategoriesForMangaQuery(manga))
                    .build())
            .prepare()

    fun insertCategory(category: Category) = db.put().`object`(category).prepare()

    fun insertCategories(categories: List<Category>) = db.put().objects(categories).prepare()

    fun deleteCategory(category: Category) = db.delete().`object`(category).prepare()

    fun deleteCategories(categories: List<Category>) = db.delete().objects(categories).prepare()

    fun insertMangaCategory(mangaCategory: MangaCategory) = db.put().`object`(mangaCategory).prepare()

    fun insertMangasCategories(mangasCategories: List<MangaCategory>) = db.put().objects(mangasCategories).prepare()

    fun deleteOldMangasCategories(mangas: List<Manga>) = db.delete()
            .byQuery(DeleteQuery.builder()
                    .table(MangaCategoryTable.TABLE)
                    .where("${MangaCategoryTable.COLUMN_MANGA_ID} IN (${Queries.placeholders(mangas.size)})")
                    .whereArgs(*mangas.map { it.id }.toTypedArray())
                    .build())
            .prepare()

    fun setMangaCategories(mangasCategories: List<MangaCategory>, mangas: List<Manga>) {
        inTransaction {
            deleteOldMangasCategories(mangas).executeAsBlocking()
            insertMangasCategories(mangasCategories).executeAsBlocking()
        }
    }

}
