package eu.kanade.tachiyomi.data.database.models;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import java.io.Serializable;

import eu.kanade.tachiyomi.data.database.tables.CategoryTable;

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

    public String getNameLower() {
        return name.toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Category category = (Category) o;

        return name.equals(category.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
