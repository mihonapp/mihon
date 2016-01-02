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

    public static final int UNKNOWN = 0;
    public static final int ONGOING = 1;
    public static final int COMPLETED = 2;
    public static final int LICENSED = 3;

    public Manga() {}

    public static Manga create(String pathUrl) {
        Manga m = new Manga();
        m.url = pathUrl;
        return m;
    }

    public void setUrl(String url) {
        this.url = UrlUtil.getPath(url);
    }

    public void copyFrom(Manga other) {
        if (other.title != null)
            title = other.title;

        if (other.author != null)
            author = other.author;

        if (other.artist != null)
            artist = other.artist;

        if (other.url != null)
            url = other.url;

        if (other.description != null)
            description = other.description;

        if (other.genre != null)
            genre = other.genre;

        if (other.thumbnail_url != null)
            thumbnail_url = other.thumbnail_url;

        status = other.status;

        initialized = true;
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
