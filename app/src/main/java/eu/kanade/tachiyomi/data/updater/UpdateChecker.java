package eu.kanade.tachiyomi.data.updater;


import android.content.Context;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.rest.GithubService;
import eu.kanade.tachiyomi.data.rest.Release;
import eu.kanade.tachiyomi.data.rest.ServiceFactory;
import eu.kanade.tachiyomi.util.ToastUtil;
import rx.Observable;


public class UpdateChecker {
    private final Context context;

    public UpdateChecker(Context context) {
        this.context = context;
    }

    /**
     * Returns observable containing release information
     *
     */
    public Observable<Release> checkForApplicationUpdate() {
        ToastUtil.showShort(context, context.getString(R.string.update_check_look_for_updates));
        //Create Github service to retrieve Github data
        GithubService service = ServiceFactory.createRetrofitService(GithubService.class, GithubService.SERVICE_ENDPOINT);
        return service.getLatestVersion();
    }
}