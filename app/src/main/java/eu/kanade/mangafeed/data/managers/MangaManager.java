package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.queries.Query;

import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.tables.MangasTable;
import rx.Observable;

public class MangaManager extends BaseManager {

    public MangaManager(StorIOSQLite db) {
        super(db);
    }

    public Observable<List<Manga>> get() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
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
        Manga m = new Manga();
        m.url="http://example.com";
        m.artist="Eiichiro Oda";
        m.author="Eiichiro Oda";
        m.description="...";
        m.genre="Action, Drama";
        m.status="Ongoing";
        m.thumbnail_url="http://example.com/pic.png";
        m.title="One Piece";
        insert(m).subscribe();
    }

}
