package eu.kanade.tachiyomi.ui.recent;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.event.ReaderEvent;
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class RecentChaptersPresenter extends BasePresenter<RecentChaptersFragment> {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;

    private static final int GET_RECENT_CHAPTERS = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_RECENT_CHAPTERS,
                this::getRecentChaptersObservable,
                RecentChaptersFragment::onNextMangaChapters);

        if (savedState == null)
            start(GET_RECENT_CHAPTERS);
    }

    private Observable<List<Object>> getRecentChaptersObservable() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MONTH, -1);

        return db.getRecentChapters(cal.getTime()).asRxObservable()
                // group chapters by the date they were fetched on a ordered map
                .flatMap(recents -> Observable.from(recents)
                        .toMultimap(
                                recent -> getMapKey(recent.chapter.date_fetch),
                                recent -> recent,
                                () -> new TreeMap<>((d1, d2) -> d2.compareTo(d1))))
                // add every day and all its chapters to a single list
                .map(recents -> {
                    List<Object> items = new ArrayList<>();
                    for (Map.Entry<Date, Collection<MangaChapter>> recent : recents.entrySet()) {
                        items.add(recent.getKey());
                        items.addAll(recent.getValue());
                    }
                    return items;
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Date getMapKey(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(date));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public void onOpenChapter(MangaChapter item) {
        Source source = sourceManager.get(item.manga.source);
        EventBus.getDefault().postSticky(new ReaderEvent(source, item.manga, item.chapter));
    }
}
