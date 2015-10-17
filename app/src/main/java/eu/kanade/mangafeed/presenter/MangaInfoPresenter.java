package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.ui.fragment.MangaInfoFragment;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MangaInfoPresenter extends BasePresenter2<MangaInfoFragment> {

    @Inject DatabaseHelper db;

    private Subscription mangaInfoSubscription;

    @Override
    protected void onTakeView(MangaInfoFragment view) {
        super.onTakeView(view);

        getMangaInfo(view);
    }

    private void getMangaInfo(MangaInfoFragment view) {
        if (mangaInfoSubscription != null)
            remove(mangaInfoSubscription);

        mangaInfoSubscription = db.getManga(view.getMangaId())
                .observeOn(AndroidSchedulers.mainThread())
                .take(1)
                .flatMap(Observable::from)
                .subscribe(view::setMangaInfo);

        add(mangaInfoSubscription);
    }
}
