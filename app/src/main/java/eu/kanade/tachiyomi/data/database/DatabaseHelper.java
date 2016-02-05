package eu.kanade.tachiyomi.data.database;

import android.content.Context;
import android.util.Pair;

import com.pushtorefresh.storio.Queries;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.PreparedDeleteByQuery;
import com.pushtorefresh.storio.sqlite.operations.delete.PreparedDeleteCollectionOfObjects;
import com.pushtorefresh.storio.sqlite.operations.delete.PreparedDeleteObject;
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects;
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject;
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutCollectionOfObjects;
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutObject;
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.data.database.models.CategorySQLiteTypeMapping;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.ChapterSQLiteTypeMapping;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaCategory;
import eu.kanade.tachiyomi.data.database.models.MangaCategorySQLiteTypeMapping;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.database.models.MangaSQLiteTypeMapping;
import eu.kanade.tachiyomi.data.database.models.MangaSync;
import eu.kanade.tachiyomi.data.database.models.MangaSyncSQLiteTypeMapping;
import eu.kanade.tachiyomi.data.database.resolvers.LibraryMangaGetResolver;
import eu.kanade.tachiyomi.data.database.resolvers.MangaChapterGetResolver;
import eu.kanade.tachiyomi.data.database.tables.CategoryTable;
import eu.kanade.tachiyomi.data.database.tables.ChapterTable;
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable;
import eu.kanade.tachiyomi.data.database.tables.MangaSyncTable;
import eu.kanade.tachiyomi.data.database.tables.MangaTable;
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService;
import eu.kanade.tachiyomi.util.ChapterRecognition;
import rx.Observable;

public class DatabaseHelper {

    private StorIOSQLite db;

    public DatabaseHelper(Context context) {

        db = DefaultStorIOSQLite.builder()
                .sqliteOpenHelper(new DbOpenHelper(context))
                .addTypeMapping(Manga.class, new MangaSQLiteTypeMapping())
                .addTypeMapping(Chapter.class, new ChapterSQLiteTypeMapping())
                .addTypeMapping(MangaSync.class, new MangaSyncSQLiteTypeMapping())
                .addTypeMapping(Category.class, new CategorySQLiteTypeMapping())
                .addTypeMapping(MangaCategory.class, new MangaCategorySQLiteTypeMapping())
                .build();
    }

    // Mangas related queries

    public PreparedGetListOfObjects<Manga> getMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getLibraryMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(RawQuery.builder()
                        .query(LibraryMangaGetResolver.QUERY)
                        .observesTables(MangaTable.TABLE, ChapterTable.TABLE, MangaCategoryTable.TABLE)
                        .build())
                .withGetResolver(LibraryMangaGetResolver.INSTANCE)
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getFavoriteMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_FAVORITE + "=?")
                        .whereArgs(1)
                        .orderBy(MangaTable.COLUMN_TITLE)
                        .build())
                .prepare();
    }

    public PreparedGetObject<Manga> getManga(String url, int sourceId) {
        return db.get()
                .object(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_URL + "=? AND " + MangaTable.COLUMN_SOURCE + "=?")
                        .whereArgs(url, sourceId)
                        .build())
                .prepare();
    }

    public PreparedGetObject<Manga> getManga(long id) {
        return db.get()
                .object(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_ID + "=?")
                        .whereArgs(id)
                        .build())
                .prepare();
    }

    public PreparedPutObject<Manga> insertManga(Manga manga) {
        return db.put()
                .object(manga)
                .prepare();
    }

    public PreparedPutCollectionOfObjects<Manga> insertMangas(List<Manga> mangas) {
        return db.put()
                .objects(mangas)
                .prepare();
    }

    public PreparedDeleteObject<Manga> deleteManga(Manga manga) {
        return db.delete()
                .object(manga)
                .prepare();
    }

    public PreparedDeleteCollectionOfObjects<Manga> deleteMangas(List<Manga> mangas) {
        return db.delete()
                .objects(mangas)
                .prepare();
    }

    public PreparedDeleteByQuery deleteMangasNotInLibrary() {
        return db.delete()
                .byQuery(DeleteQuery.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_FAVORITE + "=?")
                        .whereArgs(0)
                        .build())
                .prepare();
    }


    // Chapters related queries

    public PreparedGetListOfObjects<Chapter> getChapters(Manga manga) {
        return db.get()
                .listOfObjects(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChapterTable.TABLE)
                        .where(ChapterTable.COLUMN_MANGA_ID + "=?")
                        .whereArgs(manga.id)
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<MangaChapter> getRecentChapters(Date date) {
        return db.get()
                .listOfObjects(MangaChapter.class)
                .withQuery(RawQuery.builder()
                        .query(MangaChapterGetResolver.getRecentChaptersQuery(date))
                        .observesTables(ChapterTable.TABLE)
                        .build())
                .withGetResolver(MangaChapterGetResolver.INSTANCE)
                .prepare();
    }

    public PreparedGetObject<Chapter> getNextChapter(Chapter chapter) {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        double chapterNumber = chapter.chapter_number + 0.00001;

        return db.get()
                .object(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChapterTable.TABLE)
                        .where(ChapterTable.COLUMN_MANGA_ID + "=? AND " +
                                ChapterTable.COLUMN_CHAPTER_NUMBER + ">? AND " +
                                ChapterTable.COLUMN_CHAPTER_NUMBER + "<=?")
                        .whereArgs(chapter.manga_id, chapterNumber, chapterNumber + 1)
                        .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER)
                        .limit(1)
                        .build())
                .prepare();
    }

    public PreparedGetObject<Chapter> getPreviousChapter(Chapter chapter) {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        double chapterNumber = chapter.chapter_number - 0.00001;

        return db.get()
                .object(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChapterTable.TABLE)
                        .where(ChapterTable.COLUMN_MANGA_ID + "=? AND " +
                                ChapterTable.COLUMN_CHAPTER_NUMBER + "<? AND " +
                                ChapterTable.COLUMN_CHAPTER_NUMBER + ">=?")
                        .whereArgs(chapter.manga_id, chapterNumber, chapterNumber - 1)
                        .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER + " DESC")
                        .limit(1)
                        .build())
                .prepare();
    }

    public PreparedGetObject<Chapter> getNextUnreadChapter(Manga manga) {
        return db.get()
                .object(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChapterTable.TABLE)
                        .where(ChapterTable.COLUMN_MANGA_ID + "=? AND " +
                                ChapterTable.COLUMN_READ + "=? AND " +
                                ChapterTable.COLUMN_CHAPTER_NUMBER + ">=?")
                        .whereArgs(manga.id, 0, 0)
                        .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER)
                        .limit(1)
                        .build())
                .prepare();
    }

    public PreparedPutObject<Chapter> insertChapter(Chapter chapter) {
        return db.put()
                .object(chapter)
                .prepare();
    }

    public PreparedPutCollectionOfObjects<Chapter> insertChapters(List<Chapter> chapters) {
        return db.put()
                .objects(chapters)
                .prepare();
    }

    // Add new chapters or delete if the source deletes them
    public Observable<Pair<Integer, Integer>> insertOrRemoveChapters(Manga manga, List<Chapter> sourceChapters) {
        List<Chapter> dbChapters = getChapters(manga).executeAsBlocking();

        Observable<List<Chapter>> newChapters = Observable.from(sourceChapters)
                .filter(c -> !dbChapters.contains(c))
                .doOnNext(c -> {
                    c.manga_id = manga.id;
                    ChapterRecognition.parseChapterNumber(c, manga);
                })
                .toList();

        Observable<List<Chapter>> deletedChapters = Observable.from(dbChapters)
                .filter(c -> !sourceChapters.contains(c))
                .toList();

        return Observable.zip(newChapters, deletedChapters, (toAdd, toDelete) -> {
            int added = 0;
            int deleted = 0;
            db.internal().beginTransaction();
            try {
                TreeSet<Float> deletedReadChapterNumbers = new TreeSet<>();
                if (!toDelete.isEmpty()) {
                    for (Chapter c : toDelete) {
                        if (c.read) {
                            deletedReadChapterNumbers.add(c.chapter_number);
                        }
                    }
                    deleted = deleteChapters(toDelete).executeAsBlocking().results().size();
                }

                if (!toAdd.isEmpty()) {
                    // Set the date fetch for new items in reverse order to allow another sorting method.
                    // Sources MUST return the chapters from most to less recent, which is common.
                    long now = new Date().getTime();

                    for (int i = toAdd.size() - 1; i >= 0; i--) {
                        Chapter c = toAdd.get(i);
                        c.date_fetch = now++;
                        // Try to mark already read chapters as read when the source deletes them
                        if (c.chapter_number != -1 && deletedReadChapterNumbers.contains(c.chapter_number)) {
                            c.read = true;
                        }
                    }
                    added = insertChapters(toAdd).executeAsBlocking().numberOfInserts();
                }

                db.internal().setTransactionSuccessful();
            } finally {
                db.internal().endTransaction();
            }
            return Pair.create(added, deleted);
        });
    }

    public PreparedDeleteObject<Chapter> deleteChapter(Chapter chapter) {
        return db.delete()
                .object(chapter)
                .prepare();
    }

    public PreparedDeleteCollectionOfObjects<Chapter> deleteChapters(List<Chapter> chapters) {
        return db.delete()
                .objects(chapters)
                .prepare();
    }

    // Manga sync related queries

    public PreparedGetObject<MangaSync> getMangaSync(Manga manga, MangaSyncService sync) {
        return db.get()
                .object(MangaSync.class)
                .withQuery(Query.builder()
                        .table(MangaSyncTable.TABLE)
                        .where(MangaSyncTable.COLUMN_MANGA_ID + "=? AND " +
                                MangaSyncTable.COLUMN_SYNC_ID + "=?")
                        .whereArgs(manga.id, sync.getId())
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<MangaSync> getMangasSync(Manga manga) {
        return db.get()
                .listOfObjects(MangaSync.class)
                .withQuery(Query.builder()
                        .table(MangaSyncTable.TABLE)
                        .where(MangaSyncTable.COLUMN_MANGA_ID + "=?")
                        .whereArgs(manga.id)
                        .build())
                .prepare();
    }

    public PreparedPutObject<MangaSync> insertMangaSync(MangaSync manga) {
        return db.put()
                .object(manga)
                .prepare();
    }

    public PreparedDeleteObject<MangaSync> deleteMangaSync(MangaSync manga) {
        return db.delete()
                .object(manga)
                .prepare();
    }

    // Categories related queries

    public PreparedGetListOfObjects<Category> getCategories() {
        return db.get()
                .listOfObjects(Category.class)
                .withQuery(Query.builder()
                        .table(CategoryTable.TABLE)
                        .orderBy(CategoryTable.COLUMN_ORDER)
                        .build())
                .prepare();
    }

    public PreparedPutObject<Category> insertCategory(Category category) {
        return db.put()
                .object(category)
                .prepare();
    }

    public PreparedPutCollectionOfObjects<Category> insertCategories(List<Category> categories) {
        return db.put()
                .objects(categories)
                .prepare();
    }

    public PreparedDeleteObject<Category> deleteCategory(Category category) {
        return db.delete()
                .object(category)
                .prepare();
    }

    public PreparedDeleteCollectionOfObjects<Category> deleteCategories(List<Category> categories) {
        return db.delete()
                .objects(categories)
                .prepare();
    }

    public PreparedPutObject<MangaCategory> insertMangaCategory(MangaCategory mangaCategory) {
        return db.put()
                .object(mangaCategory)
                .prepare();
    }

    public PreparedPutCollectionOfObjects<MangaCategory> insertMangasCategories(List<MangaCategory> mangasCategories) {
        return db.put()
                .objects(mangasCategories)
                .prepare();
    }

    public PreparedDeleteByQuery deleteOldMangasCategories(List<Manga> mangas) {
        List<Long> mangaIds = Observable.from(mangas)
                .map(manga -> manga.id)
                .toList().toBlocking().single();

        return db.delete()
                .byQuery(DeleteQuery.builder()
                        .table(MangaCategoryTable.TABLE)
                        .where(MangaCategoryTable.COLUMN_MANGA_ID + " IN ("
                                + Queries.placeholders(mangas.size()) + ")")
                        .whereArgs(mangaIds.toArray())
                        .build())
                .prepare();
    }

    public void setMangaCategories(List<MangaCategory> mangasCategories, List<Manga> mangas) {
        db.internal().beginTransaction();
        try {
            deleteOldMangasCategories(mangas).executeAsBlocking();
            insertMangasCategories(mangasCategories).executeAsBlocking();
            db.internal().setTransactionSuccessful();
        } finally {
            db.internal().endTransaction();
        }
    }

}
