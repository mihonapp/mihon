package eu.kanade.mangafeed.data.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.tables.ChaptersTable;

@StorIOSQLiteType(table = ChaptersTable.TABLE)
public class Chapter {

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_MANGA_ID)
    public Long manga_id;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_URL)
    public String url;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_NAME)
    public String name;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_READ)
    public boolean read;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_LAST_PAGE_READ)
    public int last_page_read;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_DATE_FETCH)
    public long date_fetch;

    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_DATE_UPLOAD)
    public long date_upload;


    public Chapter() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Chapter chapter = (Chapter) o;

        return url.equals(chapter.url);

    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    public static Chapter newChapter() {
        Chapter c = new Chapter();
        return c;
    }
}
