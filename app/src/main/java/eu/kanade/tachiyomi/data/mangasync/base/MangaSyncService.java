package eu.kanade.tachiyomi.data.mangasync.base;

import com.squareup.okhttp.Response;

import eu.kanade.tachiyomi.data.database.models.MangaSync;
import rx.Observable;

public abstract class MangaSyncService {

    // Name of the manga sync service to display
    public abstract String getName();

    // Id of the sync service (must be declared and obtained from MangaSyncManager to avoid conflicts)
    public abstract int getId();

    public abstract Observable<Boolean> login(String username, String password);

    public abstract boolean isLogged();

    public abstract Observable<Response> update(MangaSync manga);

    public abstract Observable<Response> add(MangaSync manga);

    public abstract Observable<Response> bind(MangaSync manga);

    public abstract String getStatus(int status);

}
