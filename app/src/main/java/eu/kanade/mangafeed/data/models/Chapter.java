package eu.kanade.mangafeed.data.models;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.tables.ChaptersTable;

@StorIOSQLiteType(table = ChaptersTable.TABLE)
public class Chapter {

    @Nullable
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_ID, key = true)
    public Long id;

    @NonNull
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_MANGA_ID)
    public Long manga_id;

    @NonNull
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_URL)
    public String url;

    @NonNull
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_NAME)
    public String name;

    @NonNull
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_READ)
    public int read;

    @NonNull
    @StorIOSQLiteColumn(name = ChaptersTable.COLUMN_DATE_FETCH)
    public long date_fetch;

    @NonNull
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
