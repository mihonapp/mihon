package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaDetailPresenter extends BasePresenter<MangaDetailActivity> {

    @Inject DatabaseHelper db;

    private Subscription mangaSubscription;

    @Override
    protected void onTakeView(MangaDetailActivity view) {
        super.onTakeView(view);

        if (mangaSubscription == null)
            initializeManga(view);
    }

    private void initializeManga(MangaDetailActivity view) {
        mangaSubscription = db.getManga(view.getMangaId())
                .subscribeOn(Schedulers.io())
                .take(1)
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(view::onMangaNext);

        add(mangaSubscription);
    }

}
