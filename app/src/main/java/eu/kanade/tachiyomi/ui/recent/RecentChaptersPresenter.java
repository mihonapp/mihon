package eu.kanade.tachiyomi.ui.recent;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.download.DownloadManager;
import eu.kanade.tachiyomi.data.download.model.Download;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.event.DownloadChaptersEvent;
import eu.kanade.tachiyomi.event.ReaderEvent;
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class RecentChaptersPresenter extends BasePresenter<RecentChaptersFragment> {

    @Inject DatabaseHelper db;
    @Inject DownloadManager downloadManager;
    @Inject SourceManager sourceManager;

    private List<MangaChapter> mangaChapters;

    private static final int GET_RECENT_CHAPTERS = 1;
    private static final int CHAPTER_STATUS_CHANGES = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_RECENT_CHAPTERS,
                this::getRecentChaptersObservable,
                (recentChaptersFragment, chapters) -> {
                    recentChaptersFragment.onNextMangaChapters(chapters);
                    updateMangaInformation(convertToMangaChaptersList(chapters));
                });

        startableLatestCache(CHAPTER_STATUS_CHANGES,
                this::getChapterStatusObs,
                RecentChaptersFragment::onChapterStatusChange,
                (view, error) -> Timber.e(error.getCause(), error.getMessage()));

        if (savedState == null) {
            start(GET_RECENT_CHAPTERS);
        }
    }


    private void updateMangaInformation(List<MangaChapter> mangaChapters) {
        this.mangaChapters = mangaChapters;

        for (MangaChapter mangaChapter : mangaChapters)
            setChapterStatus(mangaChapter);

        start(CHAPTER_STATUS_CHANGES);
    }

    private List<MangaChapter> convertToMangaChaptersList(List<Object> chapters) {
        List<MangaChapter> tempMangaChapterList = new ArrayList<>();
        for (Object object : chapters) {
            if (object instanceof MangaChapter) {
                tempMangaChapterList.add((MangaChapter) object);
            }
        }
        return tempMangaChapterList;
    }

    private Observable<Download> getChapterStatusObs() {
        return downloadManager.getQueue().getStatusObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .filter(download -> chapterIdEquals(download.chapter.id))
                .doOnNext(this::updateChapterStatus);
    }

    private boolean chapterIdEquals(Long chaptersId) {
        for (MangaChapter mangaChapter : mangaChapters) {
            if (chaptersId.equals(mangaChapter.chapter.id)) {
                return true;
            }
        }
        return false;
    }

    public void updateChapterStatus(Download download) {
        for (Object item : mangaChapters) {
            if (item instanceof MangaChapter) {
                if (download.chapter.id.equals(((MangaChapter) item).chapter.id)) {
                    ((MangaChapter) item).chapter.status = download.getStatus();
                    break;
                }
            }
        }
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

    private void setChapterStatus(MangaChapter mangaChapter) {
        for (Download download : downloadManager.getQueue()) {
            if (mangaChapter.chapter.id.equals(download.chapter.id)) {
                mangaChapter.chapter.status = download.getStatus();
                return;
            }
        }

        Source source = sourceManager.get(mangaChapter.manga.source);
        if (downloadManager.isChapterDownloaded(source, mangaChapter.manga, mangaChapter.chapter)) {
            mangaChapter.chapter.status = Download.DOWNLOADED;
        } else {
            mangaChapter.chapter.status = Download.NOT_DOWNLOADED;
        }
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

    public void downloadChapter(Observable<Chapter> selectedChapter, Manga manga) {
        add(selectedChapter
                .toList()
                .subscribe(chapters -> {
                    EventBus.getDefault().postSticky(new DownloadChaptersEvent(manga, chapters));
                }));
    }

    public void deleteChapter(Chapter chapter, Manga manga) {
        Source source = sourceManager.get(manga.source);
        downloadManager.deleteChapter(source, manga, chapter);
    }

    public void deleteChapters(Observable<Chapter> selectedChapters) {
        add(selectedChapters
                .subscribe(chapter -> {
                    downloadManager.getQueue().remove(chapter);
                }, error -> {
                    Timber.e(error.getMessage());
                }));
    }

    public void markChaptersRead(Observable<Chapter> selectedChapters, boolean read) {
        add(selectedChapters
                .subscribeOn(Schedulers.io())
                .map(chapter -> {
                    chapter.read = read;
                    if (!read) chapter.last_page_read = 0;
                    return chapter;
                })
                .toList()
                .flatMap(chapters -> db.insertChapters(chapters).asRxObservable())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }
}
