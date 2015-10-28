package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;

import java.util.List;

import eu.kanade.mangafeed.data.managers.ChapterManager;
import eu.kanade.mangafeed.data.managers.ChapterManagerImpl;
import eu.kanade.mangafeed.data.managers.MangaManager;
import eu.kanade.mangafeed.data.managers.MangaManagerImpl;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.resolvers.MangaWithUnreadGetResolver;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;

public class DatabaseHelper implements MangaManager, ChapterManager {

    private StorIOSQLite mDb;
    private MangaManagerImpl mMangaManager;
    private ChapterManagerImpl mChapterManager;

    public DatabaseHelper(Context context) {

        mDb = DefaultStorIOSQLite.builder()
                .sqliteOpenHelper(new DbOpenHelper(context))
                .addTypeMapping(Manga.class, SQLiteTypeMapping.<Manga>builder()
                        .putResolver(new MangaStorIOSQLitePutResolver())
                        .getResolver(new MangaWithUnreadGetResolver())
                        .deleteResolver(new MangaStorIOSQLiteDeleteResolver())
                        .build())
                .addTypeMapping(Chapter.class, SQLiteTypeMapping.<Chapter>builder()
                        .putResolver(new ChapterStorIOSQLitePutResolver())
                        .getResolver(new ChapterStorIOSQLiteGetResolver())
                        .deleteResolver(new ChapterStorIOSQLiteDeleteResolver())
                        .build())
                .build();

        mMangaManager = new MangaManagerImpl(mDb);
        mChapterManager = new ChapterManagerImpl(mDb);
    }

    @Override
    public Observable<List<Chapter>> getChapters(Manga manga) {
        return mChapterManager.getChapters(manga);
    }

    @Override
    public Observable<List<Chapter>> getChapters(long manga_id) {
        return mChapterManager.getChapters(manga_id);
    }

    @Override
    public Observable<PutResult> insertChapter(Chapter chapter) {
        return mChapterManager.insertChapter(chapter);
    }

    @Override
    public Observable<PutResults<Chapter>> insertChapters(List<Chapter> chapters) {
        return mChapterManager.insertChapters(chapters);
    }

    @Override
    public PutResult insertChapterBlock(Chapter chapter) {
        return mChapterManager.insertChapterBlock(chapter);
    }

    @Override
    public Observable<PostResult> insertOrRemoveChapters(Manga manga, List<Chapter> chapters) {
        return mChapterManager.insertOrRemoveChapters(manga, chapters);
    }

    @Override
    public Observable<DeleteResult> deleteChapter(Chapter chapter) {
        return mChapterManager.deleteChapter(chapter);
    }

    @Override
    public Observable<DeleteResults<Chapter>> deleteChapters(List<Chapter> chapters) {
        return mChapterManager.deleteChapters(chapters);
    }

    @Override
    public Observable<List<Manga>> getMangas() {
        return mMangaManager.getMangas();
    }

    @Override
    public Observable<List<Manga>> getMangasWithUnread() {
        return mMangaManager.getMangasWithUnread();
    }

    @Override
    public Observable<List<Manga>> getManga(String url) {
        return mMangaManager.getManga(url);
    }

    @Override
    public Observable<List<Manga>> getManga(long id) {
        return mMangaManager.getManga(id);
    }

    @Override
    public Manga getMangaBlock(String url) {
        return mMangaManager.getMangaBlock(url);
    }

    @Override
    public Observable<PutResult> insertManga(Manga manga) {
        return mMangaManager.insertManga(manga);
    }

    @Override
    public Observable<PutResults<Manga>> insertMangas(List<Manga> mangas) {
        return mMangaManager.insertMangas(mangas);
    }

    @Override
    public PutResult insertMangaBlock(Manga manga) {
        return mMangaManager.insertMangaBlock(manga);
    }

    @Override
    public Observable<DeleteResult> deleteManga(Manga manga) {
        return mMangaManager.deleteManga(manga);
    }

    @Override
    public Observable<DeleteResults<Manga>> deleteMangas(List<Manga> mangas) {
        return mMangaManager.deleteMangas(mangas);
    }
}
