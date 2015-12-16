package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import java.io.Serializable;

import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.data.database.tables.MangaSyncTable;

@StorIOSQLiteType(table = MangaSyncTable.TABLE)
public class MangaSync implements Serializable {

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_MANGA_ID)
    public long manga_id;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_SYNC_ID)
    public int sync_id;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_REMOTE_ID)
    public int remote_id;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_TITLE)
    public String title;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_LAST_CHAPTER_READ)
    public int last_chapter_read;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_SCORE)
    public float score;

    @StorIOSQLiteColumn(name = MangaSyncTable.COLUMN_STATUS)
    public int status;

    public static MangaSync create(MangaSyncService service) {
        MangaSync mangasync = new MangaSync();
        mangasync.sync_id = service.getId();
        return mangasync;
    }
}
