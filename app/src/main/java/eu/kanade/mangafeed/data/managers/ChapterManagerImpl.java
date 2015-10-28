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
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;

public class ChapterManagerImpl extends BaseManager implements ChapterManager {

    public ChapterManagerImpl(StorIOSQLite db) {
        super(db);
    }

    private PreparedGetListOfObjects<Chapter> prepareGetChapters(Manga manga) {
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
        return prepareGetChapters(manga).createObservable();
    }

    @Override
    public Observable<List<Chapter>> getChapters(long manga_id) {
        return db.get()
                .listOfObjects(Chapter.class)
                .withQuery(Query.builder()
                        .table(ChaptersTable.TABLE)
                        .where(ChaptersTable.COLUMN_MANGA_ID + "=?")
                        .whereArgs(manga_id)
                        .build())
                .prepare()
                .createObservable();
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

    @Override
    public PutResult insertChapterBlock(Chapter chapter) {
        return db.put()
                .object(chapter)
                .prepare()
                .executeAsBlocking();
    }

    // Add new chapters or delete if the source deletes them
    @Override
    public Observable<PostResult> insertOrRemoveChapters(Manga manga, List<Chapter> chapters) {
        for (Chapter chapter : chapters) {
            chapter.manga_id = manga.id;
        }

        Observable<List<Chapter>> chapterList = Observable.create(subscriber -> {
            subscriber.onNext(prepareGetChapters(manga).executeAsBlocking());
            subscriber.onCompleted();
        });

        Observable<Integer> newChaptersObs = chapterList
                .flatMap(dbChapters -> Observable.from(chapters)
                        .filter(c -> !dbChapters.contains(c))
                        .toList()
                        .flatMap(this::insertChapters)
                        .map(PutResults::numberOfInserts));

        Observable<Integer> deletedChaptersObs = chapterList
                .flatMap(dbChapters -> Observable.from(dbChapters)
                        .filter(c -> !chapters.contains(c))
                        .toList()
                        .flatMap(this::deleteChapters)
                        .map( d -> d.results().size() ));

        return Observable.zip(newChaptersObs, deletedChaptersObs,
                (insertions, deletions) -> new PostResult(0, insertions, deletions)
        );
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
