package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.fragment.MangaChaptersFragment;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaChaptersPresenter extends BasePresenter<MangaChaptersFragment> {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;

    private Subscription chaptersSubscription;
    private Subscription onlineChaptersSubscription;
    private boolean doingRequest = false;

    @Override
    protected void onTakeView(MangaChaptersFragment view) {
        super.onTakeView(view);

        getChapters(view.getMangaId());
    }

    public void refreshChapters(Manga manga) {
        if (manga != null && !doingRequest)
            getChaptersFromSource(manga);
    }

    private void getChapters(long manga_id) {
        if (chaptersSubscription != null)
            remove(chaptersSubscription);

        chaptersSubscription = db.getChapters(manga_id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverLatestCache())
                .subscribe(this.split(MangaChaptersFragment::onNextChapters));

        add(chaptersSubscription);
    }

    public void getChaptersFromSource(Manga manga) {
        if (onlineChaptersSubscription != null)
            remove(onlineChaptersSubscription);

        Source source = sourceManager.get(manga.source);
        doingRequest = true;

        onlineChaptersSubscription = source.pullChaptersFromNetwork(manga.url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverLatestCache())
                .subscribe(this.split((view, chapters) -> {
                    doingRequest = false;
                }), throwable -> {
                    doingRequest = false;
                });

        add(onlineChaptersSubscription);
    }
}
