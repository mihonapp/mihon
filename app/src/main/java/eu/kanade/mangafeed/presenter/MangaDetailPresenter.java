package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaDetailPresenter extends BasePresenter<MangaDetailActivity> {

    @Inject DatabaseHelper db;

    private Manga manga;
    private Subscription mangaSubscription;

    @Override
    protected void onTakeView(MangaDetailActivity view) {
        super.onTakeView(view);
        if (manga != null)
            view.setManga(manga);

        getManga(view);
    }

    private void getManga(MangaDetailActivity view) {
        if (mangaSubscription != null)
            return;

        add(mangaSubscription = db.getManga(view.getMangaId())
                .subscribeOn(Schedulers.io())
                .take(1)
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(manga -> {
                    this.manga = manga;
                    view.setManga(manga);
                    EventBus.getDefault().postSticky(manga);
                }));
    }

}
