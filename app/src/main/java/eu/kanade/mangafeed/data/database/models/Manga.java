package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import java.io.Serializable;

import eu.kanade.mangafeed.data.database.tables.MangaTable;
import eu.kanade.mangafeed.util.UrlUtil;

@StorIOSQLiteType(table = MangaTable.TABLE)
public class Manga implements Serializable {

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_SOURCE)
    public int source;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_URL)
    public String url;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_ARTIST)
    public String artist;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_AUTHOR)
    public String author;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_DESCRIPTION)
    public String description;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_GENRE)
    public String genre;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_TITLE)
    public String title;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_STATUS)
    public int status;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_THUMBNAIL_URL)
    public String thumbnail_url;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_FAVORITE)
    public boolean favorite;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_LAST_UPDATE)
    public long last_update;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_INITIALIZED)
    public boolean initialized;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_VIEWER)
    public int viewer;

    @StorIOSQLiteColumn(name = MangaTable.COLUMN_CHAPTER_FLAGS)
    public int chapter_flags;

    public int unread;

    public int category;

    public Manga() {}

    public void setUrl(String url) {
        this.url = UrlUtil.getPath(url);
    }

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

        if (network.thumbnail_url != null)
            local.thumbnail_url = network.thumbnail_url;

        local.status = network.status;

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
