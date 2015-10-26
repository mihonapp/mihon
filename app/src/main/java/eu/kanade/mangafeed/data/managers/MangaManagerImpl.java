package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.tables.ChaptersTable;
import eu.kanade.mangafeed.data.tables.MangasTable;
import rx.Observable;

public class MangaManagerImpl extends BaseManager implements MangaManager {

    public MangaManagerImpl(StorIOSQLite db) {
        super(db);
    }

    private final String favoriteMangasWithUnreadQuery = String.format(
            "SELECT %1$s.*, COUNT(C.%4$s) AS %5$s FROM %1$s LEFT JOIN " +
            "(SELECT %4$s FROM %2$s WHERE %6$s = 0) AS C ON %3$s = C.%4$s " +
            "WHERE %7$s = 1 GROUP BY %3$s",
            MangasTable.TABLE,
            ChaptersTable.TABLE,
            MangasTable.TABLE + "." + MangasTable.COLUMN_ID,
            ChaptersTable.COLUMN_MANGA_ID,
            MangasTable.COLUMN_UNREAD,
            ChaptersTable.COLUMN_READ,
            MangasTable.COLUMN_FAVORITE
    );

    public Observable<List<Manga>> getMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<List<Manga>> getMangasWithUnread() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(RawQuery.builder()
                        .query(favoriteMangasWithUnreadQuery)
                        .observesTables(MangasTable.TABLE, ChaptersTable.TABLE)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<List<Manga>> getManga(String url) {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .where(MangasTable.COLUMN_URL + "=?")
                        .whereArgs(url)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<List<Manga>> getManga(long id) {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .where(MangasTable.COLUMN_ID + "=?")
                        .whereArgs(id)
                        .build())
                .prepare()
                .createObservable();
    }

    @Override
    public Manga getMangaBlock(String url) {
        List<Manga> result = db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .where(MangasTable.COLUMN_URL + "=?")
                        .whereArgs(url)
                        .build())
                .prepare()
                .executeAsBlocking();

        if (result.isEmpty())
            return null;

        return result.get(0);
    }

    public Observable<PutResult> insertManga(Manga manga) {
        return db.put()
                .object(manga)
                .prepare()
                .createObservable();
    }

    public Observable<PutResults<Manga>> insertMangas(List<Manga> mangas) {
        return db.put()
                .objects(mangas)
                .prepare()
                .createObservable();
    }

    public PutResult insertMangaBlock(Manga manga) {
        return db.put()
                .object(manga)
                .prepare()
                .executeAsBlocking();
    }

    public Observable<DeleteResult> deleteManga(Manga manga) {
        return db.delete()
                .object(manga)
                .prepare()
                .createObservable();
    }

    public Observable<DeleteResults<Manga>> deleteMangas(List<Manga> mangas) {
        return db.delete()
                .objects(mangas)
                .prepare()
                .createObservable();
    }
}
