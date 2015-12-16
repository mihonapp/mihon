package eu.kanade.mangafeed.data.database;

import android.content.Context;

import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.PreparedDeleteCollectionOfObjects;
import com.pushtorefresh.storio.sqlite.operations.delete.PreparedDeleteObject;
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects;
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutCollectionOfObjects;
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutObject;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.ChapterStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.database.models.ChapterStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.models.ChapterStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.database.models.MangaSyncStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.database.models.MangaSyncStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.models.MangaSyncStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.database.resolvers.MangaWithUnreadGetResolver;
import eu.kanade.mangafeed.data.database.tables.MangaSyncTable;
import eu.kanade.mangafeed.data.database.tables.ChapterTable;
import eu.kanade.mangafeed.data.database.tables.MangaTable;
import eu.kanade.mangafeed.util.ChapterRecognition;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;

public class DatabaseHelper {

    private StorIOSQLite db;

    public DatabaseHelper(Context context) {

        db = DefaultStorIOSQLite.builder()
                .sqliteOpenHelper(new DbOpenHelper(context))
                .addTypeMapping(Manga.class, SQLiteTypeMapping.<Manga>builder()
                        .putResolver(new MangaStorIOSQLitePutResolver())
                        .getResolver(new MangaStorIOSQLiteGetResolver())
                        .deleteResolver(new MangaStorIOSQLiteDeleteResolver())
                        .build())
                .addTypeMapping(Chapter.class, SQLiteTypeMapping.<Chapter>builder()
                        .putResolver(new ChapterStorIOSQLitePutResolver())
                        .getResolver(new ChapterStorIOSQLiteGetResolver())
                        .deleteResolver(new ChapterStorIOSQLiteDeleteResolver())
                        .build())
                .addTypeMapping(MangaSync.class, SQLiteTypeMapping.<MangaSync>builder()
                        .putResolver(new MangaSyncStorIOSQLitePutResolver())
                        .getResolver(new MangaSyncStorIOSQLiteGetResolver())
                        .deleteResolver(new MangaSyncStorIOSQLiteDeleteResolver())
                        .build())
                .build();
    }

    // Mangas related queries

    private final String favoriteMangasWithUnreadQuery = String.format(
            "SELECT %1$s.*, COUNT(C.%4$s) AS %5$s FROM %1$s LEFT JOIN " +
                    "(SELECT %4$s FROM %2$s WHERE %6$s = 0) AS C ON %3$s = C.%4$s " +
                    "WHERE %7$s = 1 GROUP BY %3$s ORDER BY %1$s.%8$s",
            MangaTable.TABLE,
            ChapterTable.TABLE,
            MangaTable.TABLE + "." + MangaTable.COLUMN_ID,
            ChapterTable.COLUMN_MANGA_ID,
            MangaTable.COLUMN_UNREAD,
            ChapterTable.COLUMN_READ,
            MangaTable.COLUMN_FAVORITE,
            MangaTable.COLUMN_TITLE
    );

    public PreparedGetListOfObjects<Manga> getMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getFavoriteMangasWithUnread() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(RawQuery.builder()
                        .query(favoriteMangasWithUnreadQuery)
                        .observesTables(MangaTable.TABLE, ChapterTable.TABLE)
                        .build())
                .withGetResolver(MangaWithUnreadGetResolver.instance)
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getFavoriteMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_FAVORITE + "=?")
                        .whereArgs(1)
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getManga(String url, int sourceId) {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangaTable.TABLE)
                        .where(MangaTable.COLUMN_URL + "=? AND " + MangaTable.COLUMN_SOURCE + "=?")
                        .whereArgs(url, sourceId)
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<Manga> getManga(long id) {
        return db.get()
                .listOfObjects(Manga.class)
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

    public PreparedGetListOfObjects<Chapter> getNextChapter(Chapter chapter) {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        double chapterNumber = chapter.chapter_number + 0.00001;

        return db.get()
                .listOfObjects(Chapter.class)
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

    public PreparedGetListOfObjects<Chapter> getPreviousChapter(Chapter chapter) {
        // Add a delta to the chapter number, because binary decimal representation
        // can retrieve the same chapter again
        double chapterNumber = chapter.chapter_number - 0.00001;

        return db.get()
                .listOfObjects(Chapter.class)
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

    public PreparedGetListOfObjects<Chapter> getNextUnreadChapter(Manga manga) {
        return db.get()
                .listOfObjects(Chapter.class)
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

    public PreparedGetListOfObjects<MangaSync> getMangaSync(Manga manga, MangaSyncService sync) {
        return db.get()
                .listOfObjects(MangaSync.class)
                .withQuery(Query.builder()
                        .table(MangaSyncTable.TABLE)
                        .where(MangaSyncTable.COLUMN_MANGA_ID + "=? AND " +
                                MangaSyncTable.COLUMN_SYNC_ID + "=?")
                        .whereArgs(manga.id, sync.getId())
                        .build())
                .prepare();
    }

    public PreparedGetListOfObjects<MangaSync> getMangaSync(Manga manga) {
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
}
