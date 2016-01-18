package eu.kanade.tachiyomi.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.ChapterStorIOSQLiteGetResolver;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.tachiyomi.data.database.tables.ChapterTable;
import eu.kanade.tachiyomi.data.database.tables.MangaTable;

public class MangaChapterGetResolver extends DefaultGetResolver<MangaChapter> {

    public static final MangaChapterGetResolver INSTANCE = new MangaChapterGetResolver();

    public static final String QUERY = String.format(
            "SELECT * FROM %1$s JOIN %2$s on %1$s.%3$s = %2$s.%4$s",
            MangaTable.TABLE,
            ChapterTable.TABLE,
            MangaTable.COLUMN_ID,
            ChapterTable.COLUMN_MANGA_ID);

    public static final String RECENT_CHAPTERS_QUERY = String.format(
            QUERY + " WHERE %1$s = 1 ORDER BY %2$s DESC LIMIT 100",
            MangaTable.COLUMN_FAVORITE,
            ChapterTable.COLUMN_DATE_UPLOAD);

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

        return new MangaChapter(manga, chapter);
    }
}
