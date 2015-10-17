package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.fragment.MangaInfoFragment;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaInfoPresenter extends BasePresenter<MangaInfoFragment> {

    @Inject DatabaseHelper db;

    private Manga manga;
    private Subscription mangaInfoSubscription;

    @Override
    protected void onTakeView(MangaInfoFragment view) {
        super.onTakeView(view);
        registerForStickyEvents();
    }

    @Override
    protected void onDropView() {
        unregisterForEvents();
        super.onDropView();
    }

    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        getMangaInfo();
    }

    private void getMangaInfo() {
        if (mangaInfoSubscription != null)
            return;

        add(mangaInfoSubscription = db.getManga(manga.id)
                .subscribeOn(Schedulers.io())
                .take(1)
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverLatestCache())
                .subscribe(split(MangaInfoFragment::setMangaInfo)));
    }
}
