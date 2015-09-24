package eu.kanade.mangafeed.data.entities;

/**
 * Created by len on 23/09/2015.
 */

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.tables.MangasTable;

@StorIOSQLiteType(table = MangasTable.TABLE)
public class Manga {

    @Nullable
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ID, key = true)
    Long id;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_SOURCE)
    int source;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_URL)
    String url;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ARTIST)
    String artist;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_AUTHOR)
    String author;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_DESCRIPTION)
    String description;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_GENRE)
    String genre;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_TITLE)
    String title;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_STATUS)
    String status;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_THUMBNAIL_URL)
    String thumbnail_url;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_RANK)
    int rank;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_LAST_UPDATE)
    long last_update;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_INITIALIZED)
    boolean initialized;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_VIEWER)
    int viewer;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_CHAPTER_ORDER)
    int chapter_order;

    Manga() {}

    @Nullable
    public Long id() {
        return id;
    }

    @NonNull
    public int source() {
        return source;
    }

    @NonNull
    public String url() {
        return url;
    }

    @NonNull
    public String artist() {
        return artist;
    }

    @NonNull
    public String author() {
        return author;
    }

    @NonNull
    public String description() {
        return description;
    }

    @NonNull
    public String genre() {
        return genre;
    }

    @NonNull
    public String title() {
        return title;
    }

    @NonNull
    public String status() {
        return status;
    }

    @NonNull
    public String thumbnail_url() {
        return thumbnail_url;
    }

    @NonNull
    public int rank() {
        return rank;
    }

    @NonNull
    public long last_update() {
        return last_update;
    }

    @NonNull
    public boolean nitialized() {
        return initialized;
    }

    @NonNull
    public int viewer() {
        return viewer;
    }

    @NonNull
    public int chapter_order() {
        return chapter_order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Manga manga = (Manga) o;

        if (source != manga.source) return false;
        if (rank != manga.rank) return false;
        if (last_update != manga.last_update) return false;
        if (initialized != manga.initialized) return false;
        if (viewer != manga.viewer) return false;
        if (chapter_order != manga.chapter_order) return false;
        if (id != null ? !id.equals(manga.id) : manga.id != null) return false;
        if (!url.equals(manga.url)) return false;
        if (!artist.equals(manga.artist)) return false;
        if (!author.equals(manga.author)) return false;
        if (!description.equals(manga.description)) return false;
        if (!genre.equals(manga.genre)) return false;
        if (!title.equals(manga.title)) return false;
        if (!status.equals(manga.status)) return false;
        return thumbnail_url.equals(manga.thumbnail_url);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + source;
        result = 31 * result + url.hashCode();
        result = 31 * result + artist.hashCode();
        result = 31 * result + author.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + genre.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + thumbnail_url.hashCode();
        result = 31 * result + rank;
        result = 31 * result + (int) (last_update ^ (last_update >>> 32));
        result = 31 * result + (initialized ? 1 : 0);
        result = 31 * result + viewer;
        result = 31 * result + chapter_order;
        return result;
    }
}
