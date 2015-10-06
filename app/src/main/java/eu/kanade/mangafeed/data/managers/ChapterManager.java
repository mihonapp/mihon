package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;
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

    private PreparedGetListOfObjects<Chapter> prepareGet(Manga manga) {
        return db.get()
                .listOfObjects(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChaptersTable.TABLE)
                        .where(ChaptersTable.COLUMN_MANGA_ID + "=?")
                        .whereArgs(manga.id)
                        .build())
                .prepare();
    }

    public Observable<List<Chapter>> get(Manga manga) {
        return prepareGet(manga).createObservable();
    }

    public Observable<PutResult> insert(Chapter chapter) {
        return db.put()
                .object(chapter)
                .prepare()
                .createObservable();
    }

    public Observable<PutResults<Chapter>> insert(List<Chapter> chapters) {
        return db.put()
                .objects(chapters)
                .prepare()
                .createObservable();
    }

    // Add new chapters or delete if the source deletes them
    public Observable insertOrRemove(Manga manga, List<Chapter> chapters) {
        // I don't know a better approach
        return Observable.create(subscriber -> {
            List<Chapter> dbGet = prepareGet(manga).executeAsBlocking();

            Observable.just(dbGet)
                    .doOnNext(dbChapters -> {
                        Observable.from(chapters)
                                .filter(c -> !dbChapters.contains(c))
                                .toList()
                                .subscribe(newChapters -> {
                                    if (newChapters.size() > 0)
                                        insert(newChapters).subscribe();
                                });
                    })
                    .flatMap(Observable::from)
                    .filter(c -> !chapters.contains(c))
                    .toList()
                    .subscribe(removedChapters -> {
                        if (removedChapters.size() > 0)
                            delete(removedChapters).subscribe();
                        subscriber.onCompleted();
                    });

        });

    }

    public void createDummyChapters() {
        Chapter c;

        for (int i = 1; i < 100; i++) {
            c = new Chapter();
            c.manga_id = 1L;
            c.name = "Chapter " + i;
            c.url = "http://example.com/1";
            insert(c).subscribe();
        }

    }

    public Observable<DeleteResults<Chapter>> delete(List<Chapter> chapters) {
        return db.delete()
                .objects(chapters)
                .prepare()
                .createObservable();
    }

    public Observable<DeleteResult> delete(Chapter chapter) {
        return db.delete()
                .object(chapter)
                .prepare()
                .createObservable();
    }
}
