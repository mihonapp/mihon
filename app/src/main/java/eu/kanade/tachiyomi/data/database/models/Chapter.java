package eu.kanade.tachiyomi.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import java.io.Serializable;
import java.util.List;

import eu.kanade.tachiyomi.data.database.tables.ChapterTable;
import eu.kanade.tachiyomi.data.download.model.Download;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.util.UrlUtil;

@StorIOSQLiteType(table = ChapterTable.TABLE)
public class Chapter implements Serializable {

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = ChapterTable.COLUMN_MANGA_ID)
    public Long manga_id;

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

    private transient List<Page> pages;

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

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public boolean isDownloaded() {
        return status == Download.DOWNLOADED;
    }
}
