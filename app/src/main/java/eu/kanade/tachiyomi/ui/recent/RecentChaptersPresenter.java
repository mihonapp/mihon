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

/**
 * Presenter of RecentChaptersFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
public class RecentChaptersPresenter extends BasePresenter<RecentChaptersFragment> {

    /**
     * The id of the restartable.
     */
    private static final int GET_RECENT_CHAPTERS = 1;

    /**
     * The id of the restartable.
     */
    private static final int CHAPTER_STATUS_CHANGES = 2;

    /**
     * Used to connect to database
     */
    @Inject DatabaseHelper db;

    /**
     * Used to get information from download manager
     */
    @Inject DownloadManager downloadManager;

    /**
     * Used to get source from source id
     */
    @Inject SourceManager sourceManager;

    /**
     * List containing chapter and manga information
     */
    private List<MangaChapter> mangaChapters;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Used to get recent chapters
        restartableLatestCache(GET_RECENT_CHAPTERS,
                this::getRecentChaptersObservable,
                (recentChaptersFragment, chapters) -> {
                    // Update adapter to show recent manga's
                    recentChaptersFragment.onNextMangaChapters(chapters);
                    // Update download status
                    updateChapterStatus(convertToMangaChaptersList(chapters));
                });

        // Used to update download status
        startableLatestCache(CHAPTER_STATUS_CHANGES,
                this::getChapterStatusObs,
                RecentChaptersFragment::onChapterStatusChange,
                (view, error) -> Timber.e(error.getCause(), error.getMessage()));

        if (savedState == null) {
            // Start fetching recent chapters
            start(GET_RECENT_CHAPTERS);
        }
    }

    /**
     * Returns a list only containing MangaChapter objects.
     *
     * @param input the list that will be converted.
     * @return list containing MangaChapters objects.
     */
    private List<MangaChapter> convertToMangaChaptersList(List<Object> input) {
        // Create temp list
        List<MangaChapter> tempMangaChapterList = new ArrayList<>();

        // Only add MangaChapter objects
        //noinspection Convert2streamapi
        for (Object object : input) {
            if (object instanceof MangaChapter) {
                tempMangaChapterList.add((MangaChapter) object);
            }
        }

        // Return temp list
        return tempMangaChapterList;
    }

    /**
     * Update status of chapters
     *
     * @param mangaChapters list containing recent chapters
     */
    private void updateChapterStatus(List<MangaChapter> mangaChapters) {
        // Set global list of chapters.
        this.mangaChapters = mangaChapters;

        // Update status.
        //noinspection Convert2streamapi
        for (MangaChapter mangaChapter : mangaChapters)
            setChapterStatus(mangaChapter);

        // Start onChapterStatusChange restartable.
        start(CHAPTER_STATUS_CHANGES);
    }

    /**
     * Returns observable containing chapter status.
     *
     * @return download object containing download progress.
     */
    private Observable<Download> getChapterStatusObs() {
        return downloadManager.getQueue().getStatusObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .filter(download -> chapterIdEquals(download.chapter.id))
                .doOnNext(this::updateChapterStatus);
    }

    /**
     * Function to check if chapter is in recent list
     * @param chaptersId id of chapter
     * @return exist in recent list
     */
    private boolean chapterIdEquals(Long chaptersId) {
        for (MangaChapter mangaChapter : mangaChapters) {
            if (chaptersId.equals(mangaChapter.chapter.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update status of chapters.
     *
     * @param download download object containing progress.
     */
    private void updateChapterStatus(Download download) {
        // Loop through list
        for (MangaChapter item : mangaChapters) {
            if (download.chapter.id.equals(item.chapter.id)) {
                item.chapter.status = download.getStatus();
                    break;
            }
        }
    }

    /**
     * Get observable containing recent chapters and date
     * @return observable containing recent chapters and date
     */
    private Observable<List<Object>> getRecentChaptersObservable() {
        // Set date for recent chapters
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MONTH, -1);

        // Get recent chapters from database.
        return db.getRecentChapters(cal.getTime()).asRxObservable()
                // Group chapters by the date they were fetched on a ordered map.
                .flatMap(recents -> Observable.from(recents)
                        .toMultimap(
                                recent -> getMapKey(recent.chapter.date_fetch),
                                recent -> recent,
                                () -> new TreeMap<>((d1, d2) -> d2.compareTo(d1))))
                // Add every day and all its chapters to a single list.
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

    /**
     * Set the chapter status
     * @param mangaChapter MangaChapter which status gets updated
     */
    private void setChapterStatus(MangaChapter mangaChapter) {
        // Check if chapter in queue
        for (Download download : downloadManager.getQueue()) {
            if (mangaChapter.chapter.id.equals(download.chapter.id)) {
                mangaChapter.chapter.status = download.getStatus();
                return;
            }
        }

        // Get source of chapter
        Source source = sourceManager.get(mangaChapter.manga.source);

        // Check if chapter is downloaded
        if (downloadManager.isChapterDownloaded(source, mangaChapter.manga, mangaChapter.chapter)) {
            mangaChapter.chapter.status = Download.DOWNLOADED;
        } else {
            mangaChapter.chapter.status = Download.NOT_DOWNLOADED;
        }
    }

    /**
     * Get date as time key
     * @param date desired date
     * @return date as time key
     */
    private Date getMapKey(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(date));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Open chapter in reader
     * @param item chapter that is opened
     */
    public void onOpenChapter(MangaChapter item) {
        Source source = sourceManager.get(item.manga.source);
        EventBus.getDefault().postSticky(new ReaderEvent(source, item.manga, item.chapter));
    }

    /**
     * Download selected chapter
     * @param selectedChapter chapter that is selected
     * @param manga manga that belongs to chapter
     */
    public void downloadChapter(Observable<Chapter> selectedChapter, Manga manga) {
        add(selectedChapter
                .toList()
                .subscribe(chapters -> {
                    EventBus.getDefault().postSticky(new DownloadChaptersEvent(manga, chapters));
                }));
    }

    /**
     * Delete selected chapter
     * @param chapter chapter that is selected
     * @param manga manga that belongs to chapter
     */
    public void deleteChapter(Chapter chapter, Manga manga) {
        Source source = sourceManager.get(manga.source);
        downloadManager.deleteChapter(source, manga, chapter);
    }

    /**
     * Delete selected chapter observable
     * @param selectedChapters chapter that are selected
     */
    public void deleteChapters(Observable<Chapter> selectedChapters) {
        add(selectedChapters
                .subscribe(chapter -> {
                    downloadManager.getQueue().remove(chapter);
                }, error -> {
                    Timber.e(error.getMessage());
                }));
    }

    /**
     * Mark selected chapter as read
     * @param selectedChapters chapter that is selected
     * @param read read status
     */
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
