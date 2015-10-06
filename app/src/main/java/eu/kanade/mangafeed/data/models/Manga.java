package eu.kanade.mangafeed.data.models;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.tables.MangasTable;

@StorIOSQLiteType(table = MangasTable.TABLE)
public class Manga {

    @Nullable
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ID, key = true)
    public Long id;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_SOURCE)
    public int source;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_URL)
    public String url;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ARTIST)
    public String artist;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_AUTHOR)
    public String author;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_DESCRIPTION)
    public String description;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_GENRE)
    public String genre;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_TITLE)
    public String title;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_STATUS)
    public String status;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_THUMBNAIL_URL)
    public String thumbnail_url;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_RANK)
    public int rank;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_LAST_UPDATE)
    public long last_update;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_INITIALIZED)
    public boolean initialized;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_VIEWER)
    public int viewer;

    @NonNull
    @StorIOSQLiteColumn(name = MangasTable.COLUMN_CHAPTER_ORDER)
    public int chapter_order;

    @NonNull
    public int unread = 0;

    public Manga() {}

    public Manga(String title) {
        this.title = title;
    }

    public Manga(String title, String author, String artist, String url,
                 String description, String genre, String status, int rank,
                 String thumbnail_url) {
        this.title = title;
        this.author = author;
        this.artist = artist;
        this.url = url;
        this.description = description;
        this.genre = genre;
        this.status = status;
        this.rank = rank;
        this.thumbnail_url = thumbnail_url;
    }

    public static Manga newManga(String title, String author, String artist, String url,
                                 String description, String genre, String status, int rank,
                                 String thumbnail_url) {
        return new Manga(title, author, artist, url, description, genre, status, rank, thumbnail_url);
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
