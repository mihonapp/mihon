package eu.kanade.mangafeed.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import java.io.Serializable;

import eu.kanade.mangafeed.data.database.tables.CategoryTable;

@StorIOSQLiteType(table = CategoryTable.TABLE)
public class Category implements Serializable {

    @StorIOSQLiteColumn(name = CategoryTable.COLUMN_ID, key = true)
    public Integer id;

    @StorIOSQLiteColumn(name = CategoryTable.COLUMN_NAME)
    public String name;

    @StorIOSQLiteColumn(name = CategoryTable.COLUMN_ORDER)
    public int order;

    @StorIOSQLiteColumn(name = CategoryTable.COLUMN_FLAGS)
    public int flags;

    public Category() {}

    public static Category create(String name) {
        Category c = new Category();
        c.name = name;
        return c;
    }

    public static Category createDefault() {
        Category c = create("Default");
        c.id = 0;
        return c;
    }
}
