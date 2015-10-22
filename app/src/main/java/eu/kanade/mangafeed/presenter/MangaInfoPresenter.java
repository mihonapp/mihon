package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.events.ChapterCountEvent;
import eu.kanade.mangafeed.ui.fragment.MangaInfoFragment;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.Observable;

public class MangaInfoPresenter extends BasePresenter<MangaInfoFragment> {

    @Inject DatabaseHelper db;

    private Manga manga;
    private int count = -1;

    private static final int GET_MANGA = 1;
    private static final int GET_CHAPTER_COUNT = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_MANGA,
                () -> Observable.just(manga),
                MangaInfoFragment::setMangaInfo);

        restartableLatestCache(GET_CHAPTER_COUNT,
                () -> Observable.just(count),
                MangaInfoFragment::setChapterCount);
    }

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

    @EventBusHook
    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        start(GET_MANGA);
    }

    @EventBusHook
    public void onEventMainThread(ChapterCountEvent event) {
        if (count != event.getCount()) {
            count = event.getCount();
            start(GET_CHAPTER_COUNT);
        }
    }

}
