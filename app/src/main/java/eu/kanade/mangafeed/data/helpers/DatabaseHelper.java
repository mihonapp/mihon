package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite;

import eu.kanade.mangafeed.data.managers.ChapterManager;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.models.ChapterStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLiteDeleteResolver;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLitePutResolver;
import eu.kanade.mangafeed.data.managers.MangaManager;
import eu.kanade.mangafeed.data.tables.ChaptersTable;

public class DatabaseHelper {

    private StorIOSQLite db;
    public MangaManager manga;
    public ChapterManager chapter;

    public DatabaseHelper(Context context) {
        db = DefaultStorIOSQLite.builder()
                .sqliteOpenHelper(new DbOpenHelper(context))
                .addTypeMapping(Manga.class, SQLiteTypeMapping.<Manga>builder()
                        .putResolver(new MangaStorIOSQLitePutResolver())
                        .getResolver(new MangaStorIOSQLiteGetResolver())
                        .deleteResolver(new MangaStorIOSQLiteDeleteResolver())
                        .build())
                .addTypeMapping(Chapter.class, SQLiteTypeMapping.<Chapter>builder()
                        .putResolver(new ChapterStorIOSQLitePutResolver())
                        .getResolver(new ChapterStorIOSQLiteGetResolver())
                        .deleteResolver(new ChapterStorIOSQLiteDeleteResolver())
                        .build())
                .build();

        manga = new MangaManager(db);
        chapter = new ChapterManager(db);
    }

}
