package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite;
import com.pushtorefresh.storio.sqlite.queries.Query;

import java.util.List;

import eu.kanade.mangafeed.data.entities.Manga;
import eu.kanade.mangafeed.data.tables.MangasTable;
import rx.Observable;

/**
 * Created by len on 23/09/2015.
 */
public class DatabaseHelper {

    private StorIOSQLite db;

    public DatabaseHelper(Context context) {
        db = DefaultStorIOSQLite.builder()
                .sqliteOpenHelper(new DbOpenHelper(context))
                .build();
    }

    public StorIOSQLite getStorIODb() {
        return db;
    }

    public Observable<List<Manga>> getMangas() {
        return db.get()
                .listOfObjects(Manga.class)
                .withQuery(Query.builder()
                        .table(MangasTable.TABLE)
                        .build())
                .prepare()
                .createObservable();

    }

}
