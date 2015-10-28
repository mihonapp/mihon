package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;

import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;

public interface ChapterManager {

    Observable<List<Chapter>> getChapters(Manga manga);

    Observable<List<Chapter>> getChapters(long manga_id);

    Observable<PutResult> insertChapter(Chapter chapter);

    Observable<PutResults<Chapter>> insertChapters(List<Chapter> chapters);

    PutResult insertChapterBlock(Chapter chapter);

    Observable<PostResult> insertOrRemoveChapters(Manga manga, List<Chapter> chapters);

    Observable<DeleteResult> deleteChapter(Chapter chapter);

    Observable<DeleteResults<Chapter>> deleteChapters(List<Chapter> chapters);

}
