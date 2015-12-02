package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.database.tables.ChapterTable;
import eu.kanade.mangafeed.util.UrlUtil;

@StorIOSQLiteType(table = ChapterTable.TABLE)
public class Chapter {

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_ID, key = true)
    public long id;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_MANGA_ID)
    public long manga_id;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_URL)
    public String url;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_NAME)
    public String name;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_READ)
    public boolean read;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_LAST_PAGE_READ)
    public int last_page_read;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_DATE_FETCH)
    public long date_fetch;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_DATE_UPLOAD)
    public long date_upload;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_CHAPTER_NUMBER)
    public float chapter_number;

    public int status;


    public Chapter() {}

    public void setUrl(String url) {
        this.url = UrlUtil.getPath(url);
    }

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

    public static Chapter create() {
        Chapter chapter = new Chapter();
        chapter.chapter_number = -1;
        return chapter;
    }

}
