package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.chaptersync.BaseChapterSync;
import eu.kanade.mangafeed.data.database.tables.ChapterSyncTable;

@StorIOSQLiteType(table = ChapterSyncTable.TABLE)
public class ChapterSync {

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_ID, key = true)
    public long id;

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_MANGA_ID)
    public long manga_id;

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_SYNC_ID)
    public long sync_id;

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_REMOTE_ID)
    public long remote_id;

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_TITLE)
    public String title;

    @StorIOSQLiteColumn(name = ChapterSyncTable.COLUMN_LAST_CHAPTER_READ)
    public int last_chapter_read;

    public static ChapterSync create(BaseChapterSync sync) {
        ChapterSync chapter = new ChapterSync();
        chapter.sync_id = sync.getId();
        return chapter;
    }
}
