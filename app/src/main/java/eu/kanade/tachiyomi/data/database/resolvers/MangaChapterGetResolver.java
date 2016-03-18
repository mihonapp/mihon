package eu.kanade.tachiyomi.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.ChapterStorIOSQLiteGetResolver;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.database.models.MangaStorIOSQLiteGetResolver;

public class MangaChapterGetResolver extends DefaultGetResolver<MangaChapter> {

    public static final MangaChapterGetResolver INSTANCE = new MangaChapterGetResolver();

    @NonNull
    private final MangaStorIOSQLiteGetResolver mangaGetResolver;

    @NonNull
    private final ChapterStorIOSQLiteGetResolver chapterGetResolver;

    public MangaChapterGetResolver() {
        this.mangaGetResolver = new MangaStorIOSQLiteGetResolver();
        this.chapterGetResolver = new ChapterStorIOSQLiteGetResolver();
    }

    @NonNull
    @Override
    public MangaChapter mapFromCursor(@NonNull Cursor cursor) {
        final Manga manga = mangaGetResolver.mapFromCursor(cursor);
        final Chapter chapter = chapterGetResolver.mapFromCursor(cursor);
        manga.id = chapter.manga_id;

        return new MangaChapter(manga, chapter);
    }
}
