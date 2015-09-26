package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;

public abstract class BaseManager {

    protected StorIOSQLite db;

    public BaseManager(StorIOSQLite db) {
        this.db = db;
    }
}
