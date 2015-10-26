package eu.kanade.mangafeed.data.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.tables.MangasTable;

@StorIOSQLiteType(table = MangasTable.TABLE)
public class Manga {

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_SOURCE)
    public int source;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_URL)
    public String url;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_ARTIST)
    public String artist;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_AUTHOR)
    public String author;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_DESCRIPTION)
    public String description;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_GENRE)
    public String genre;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_TITLE)
    public String title;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_STATUS)
    public String status;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_THUMBNAIL_URL)
    public String thumbnail_url;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_FAVORITE)
    public boolean favorite;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_LAST_UPDATE)
    public long last_update;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_INITIALIZED)
    public boolean initialized;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_VIEWER)
    public int viewer;

    @StorIOSQLiteColumn(name = MangasTable.COLUMN_CHAPTER_ORDER)
    public int chapter_order;

    public int unread;

    public Manga() {}

    public static void copyFromNetwork(Manga local, Manga network) {
        if (network.title != null)
            local.title = network.title;

        if (network.author != null)
            local.author = network.author;

        if (network.artist != null)
            local.artist = network.artist;

        if (network.url != null)
            local.url = network.url;

        if (network.description != null)
            local.description = network.description;

        if (network.genre != null)
            local.genre = network.genre;

        if (network.status != null)
            local.status = network.status;

        if (network.thumbnail_url != null)
            local.thumbnail_url = network.thumbnail_url;

        local.initialized = true;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Manga manga = (Manga) o;

        return url.equals(manga.url);

    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }
}
