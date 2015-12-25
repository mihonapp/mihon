package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import eu.kanade.mangafeed.data.database.tables.MangaCategoryTable;

@StorIOSQLiteType(table = MangaCategoryTable.TABLE)
public class MangaCategory {

    @StorIOSQLiteColumn(name = MangaCategoryTable.COLUMN_ID, key = true)
    public Long id;

    @StorIOSQLiteColumn(name = MangaCategoryTable.COLUMN_MANGA_ID)
    public long manga_id;

    @StorIOSQLiteColumn(name = MangaCategoryTable.COLUMN_CATEGORY_ID)
    public int category_id;

    public MangaCategory() {}

    public static MangaCategory create(Manga manga, Category category) {
        MangaCategory mc = new MangaCategory();
        mc.manga_id = manga.id;
        mc.category_id = category.id;
        return mc;
    }

}
