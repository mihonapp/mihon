package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.tables.ChaptersTable;
import eu.kanade.mangafeed.data.tables.MangasTable;
import rx.Observable;

public class MangaManager extends BaseManager {

    public MangaManager(StorIOSQLite db) {
        super(db);
    }

    private final String mangasWithUnreadQuery = String.format(
            "SELECT %1$s.*, COUNT(C.%4$s) AS %5$s FROM %1$s LEFT JOIN " +
            "(SELECT %4$s FROM %2$s WHERE %6$s = 0) AS C ON %3$s = C.%4$s " +
            "GROUP BY %3$s",
            MangasTable.TABLE,
            ChaptersTable.TABLE,
            MangasTable.TABLE + "." + MangasTable.COLUMN_ID,
            ChaptersTable.COLUMN_MANGA_ID,
            MangasTable.COLUMN_UNREAD,
            ChaptersTable.COLUMN_READ
    );

    public Observable<List<Manga>> get() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<List<Manga>> getWithUnread() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(RawQuery.builder()
                        .query(mangasWithUnreadQuery)
                        .observesTables(MangasTable.TABLE, ChaptersTable.TABLE)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<PutResult> insert(Manga manga) {
        return db.put()
                .object(manga)
                .prepare()
                .createObservable();
    }

    public void createDummyManga() {
        insert(createDummyManga("One Piece")).subscribe();
        insert(createDummyManga("Übel Blatt")).subscribe();
        insert(createDummyManga("Berserk")).subscribe();
        insert(createDummyManga("Horimiya")).subscribe();
    }

    private Manga createDummyManga(String title) {
        Manga m = new Manga();
        m.title = title;
        m.url="http://example.com";
        m.artist="Eiichiro Oda";
        m.author="Eiichiro Oda";
        m.description="...";
        m.genre="Action, Drama";
        m.status="Ongoing";
        m.thumbnail_url="http://example.com/pic.png";
        return m;
    }

    public Observable<DeleteResult> delete(Manga manga) {
        return db.delete()
                .object(manga)
                .prepare()
                .createObservable();
    }

    public Observable<DeleteResults<Manga>> delete(List<Manga> mangas) {
        return db.delete()
                .objects(mangas)
                .prepare()
                .createObservable();
    }

}
