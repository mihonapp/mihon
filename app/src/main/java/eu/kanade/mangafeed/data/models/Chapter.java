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
    public int manga_id;

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


    public Chapter() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Chapter chapter = (Chapter) o;

        if (manga_id != chapter.manga_id) return false;
        if (read != chapter.read) return false;
        if (date_fetch != chapter.date_fetch) return false;
        if (id != null ? !id.equals(chapter.id) : chapter.id != null) return false;
        if (!url.equals(chapter.url)) return false;
        return name.equals(chapter.name);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + manga_id;
        result = 31 * result + url.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + read;
        result = 31 * result + (int) (date_fetch ^ (date_fetch >>> 32));
        return result;
    }
}
