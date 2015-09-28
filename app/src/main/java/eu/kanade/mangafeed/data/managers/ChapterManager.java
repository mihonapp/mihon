package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.queries.Query;

import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.tables.ChaptersTable;
import rx.Observable;

public class ChapterManager extends BaseManager {

    public ChapterManager(StorIOSQLite db) {
        super(db);
    }

    public Observable<List<Chapter>> get(Manga manga) {
        return db.get()
                .listOfObjects(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChaptersTable.TABLE)
                        .where(ChaptersTable.COLUMN_MANGA_ID + "=?")
                        .whereArgs(manga.id)
                        .build())
                .prepare()
                .createObservable();
    }

    public Observable<PutResult> insert(Chapter chapter) {
        return db.put()
                .object(chapter)
                .prepare()
                .createObservable();
    }

    public void createDummyChapters() {
        Chapter c;

        for (int i = 1; i < 100; i++) {
            c = new Chapter();
            c.manga_id = 1;
            c.name = "Chapter " + i;
            c.url = "http://example.com/1";
            insert(c).subscribe();
        }

    }
}
