package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;
import com.pushtorefresh.storio.sqlite.queries.Query;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.tables.ChaptersTable;
import rx.Observable;

public class ChapterManagerImpl extends BaseManager implements ChapterManager {

    public ChapterManagerImpl(StorIOSQLite db) {
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

    @Override
    public Observable<List<Chapter>> getChapters(Manga manga) {
        return prepareGet(manga).createObservable();
    }

    @Override
    public Observable<PutResult> insertChapter(Chapter chapter) {
        return db.put()
                .object(chapter)
                .prepare()
                .createObservable();
    }

    @Override
    public Observable<PutResults<Chapter>> insertChapters(List<Chapter> chapters) {
        return db.put()
                .objects(chapters)
                .prepare()
                .createObservable();
    }

    // Add new chapters or delete if the source deletes them
    @Override
    public Observable insertOrRemoveChapters(Manga manga, List<Chapter> chapters) {
        // I don't know a better approach
        // TODO Fix this method
        return Observable.create(subscriber -> {
            List<Chapter> dbChapters = prepareGet(manga).executeAsBlocking();

            Observable<List<Chapter>> newChaptersObs =
                    Observable.from(chapters)
                            .filter(c -> !dbChapters.contains(c))
                            .toList();

            Observable<List<Chapter>> deletedChaptersObs =
                    Observable.from(dbChapters)
                            .filter(c -> !chapters.contains(c))
                            .toList();

            Observable.zip(newChaptersObs, deletedChaptersObs,
                    (newChapters, deletedChapters) -> {
                        insertChapters(newChapters).subscribe();
                        deleteChapters(deletedChapters).subscribe();
                        subscriber.onCompleted();
                        return null;
                    }).subscribe();
        });
    }

    @Override
    public Observable<DeleteResult> deleteChapter(Chapter chapter) {
        return db.delete()
                .object(chapter)
                .prepare()
                .createObservable();
    }

    @Override
    public Observable<DeleteResults<Chapter>> deleteChapters(List<Chapter> chapters) {
        return db.delete()
                .objects(chapters)
                .prepare()
                .createObservable();
    }

}
