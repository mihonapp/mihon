package eu.kanade.mangafeed.data.database;

import android.content.Context;

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
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.CategorySQLiteTypeMapping;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.ChapterSQLiteTypeMapping;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaCategory;
import eu.kanade.mangafeed.data.database.models.MangaCategorySQLiteTypeMapping;
import eu.kanade.mangafeed.data.database.models.MangaSQLiteTypeMapping;
import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.database.models.MangaSyncSQLiteTypeMapping;
import eu.kanade.mangafeed.data.database.resolvers.LibraryMangaGetResolver;
import eu.kanade.mangafeed.data.database.tables.CategoryTable;
import eu.kanade.mangafeed.data.database.tables.ChapterTable;
import eu.kanade.mangafeed.data.database.tables.MangaCategoryTable;
import eu.kanade.mangafeed.data.database.tables.MangaSyncTable;
import eu.kanade.mangafeed.data.database.tables.MangaTable;
import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.util.ChapterRecognition;
import eu.kanade.mangafeed.util.PostResult;
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

    public PreparedGetListOfObjects<Chapter> getChapters(long manga_id, boolean sortAToZ, boolean onlyUnread) {
        Query.CompleteBuilder query = Query.builder()
                .table(ChapterTable.TABLE)

                .orderBy(ChapterTable.COLUMN_CHAPTER_NUMBER + (sortAToZ ? " ASC" : " DESC"));

        if (onlyUnread) {
            query = query.where(ChapterTable.COLUMN_MANGA_ID + "=? AND " + ChapterTable.COLUMN_READ + "=?")
                    .whereArgs(manga_id, 0);
        } else {
            query = query.where(ChapterTable.COLUMN_MANGA_ID + "=?")
                    .whereArgs(manga_id);
        }

        return db.get()
                .listOfObjects(Chapter.class)
                .withQuery(query.build())
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
    public Observable<PostResult> insertOrRemoveChapters(Manga manga, List<Chapter> chapters) {
        for (Chapter chapter : chapters) {
            chapter.manga_id = manga.id;
        }

        Observable<List<Chapter>> chapterList = Observable.create(subscriber -> {
            subscriber.onNext(getChapters(manga).executeAsBlocking());
            subscriber.onCompleted();
        });

        Observable<Integer> newChaptersObs = chapterList
                .flatMap(dbChapters -> Observable.from(chapters)
                        .filter(c -> !dbChapters.contains(c))
                        .map(c -> {
                            ChapterRecognition.parseChapterNumber(c, manga);
                            return c;
                        })
                        .toList()
                        .flatMap(newChapters -> insertChapters(newChapters).createObservable())
                        .map(PutResults::numberOfInserts));

        Observable<Integer> deletedChaptersObs = chapterList
                .flatMap(dbChapters -> Observable.from(dbChapters)
                        .filter(c -> !chapters.contains(c))
                        .toList()
                        .flatMap(deletedChapters -> deleteChapters(deletedChapters).createObservable())
                        .map(d -> d.results().size()));

        return Observable.zip(newChaptersObs, deletedChaptersObs,
                (insertions, deletions) -> new PostResult(0, insertions, deletions)
        );
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
