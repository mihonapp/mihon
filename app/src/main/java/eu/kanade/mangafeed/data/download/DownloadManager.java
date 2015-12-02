package eu.kanade.mangafeed.data.download;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.download.model.Download;
import eu.kanade.mangafeed.data.download.model.DownloadQueue;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.event.DownloadChaptersEvent;
import eu.kanade.mangafeed.util.DiskUtils;
import eu.kanade.mangafeed.util.DynamicConcurrentMergeOperator;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class DownloadManager {

    private Context context;
    private SourceManager sourceManager;
    private PreferencesHelper preferences;
    private Gson gson;

    private PublishSubject<Download> downloadsQueueSubject;
    private BehaviorSubject<Integer> threadsNumber;
    private Subscription downloadsSubscription;
    private Subscription threadsNumberSubscription;

    private DownloadQueue queue;
    private volatile boolean isQueuePaused;
    private volatile boolean isRunning;

    public static final String PAGE_LIST_FILE = "index.json";

    public DownloadManager(Context context, SourceManager sourceManager, PreferencesHelper preferences) {
        this.context = context;
        this.sourceManager = sourceManager;
        this.preferences = preferences;

        gson = new Gson();
        queue = new DownloadQueue();

        downloadsQueueSubject = PublishSubject.create();
        threadsNumber = BehaviorSubject.create();
    }

    public void initializeSubscriptions() {
        if (downloadsSubscription != null && !downloadsSubscription.isUnsubscribed())
            downloadsSubscription.unsubscribe();

        if (threadsNumberSubscription != null && !threadsNumberSubscription.isUnsubscribed())
            threadsNumberSubscription.unsubscribe();

        threadsNumberSubscription = preferences.getDownloadTheadsObservable()
                .filter(n -> !isQueuePaused)
                .doOnNext(n -> isQueuePaused = (n == 0))
                .subscribe(threadsNumber::onNext);

        downloadsSubscription = downloadsQueueSubject
                .lift(new DynamicConcurrentMergeOperator<>(this::downloadChapter, threadsNumber))
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(finished -> {
                    if (finished) {
                        DownloadService.stop(context);
                    }
                }, e -> Timber.e(e.fillInStackTrace(), e.getMessage()));

        isRunning = true;
    }

    public void destroySubscriptions() {
        isRunning = false;

        if (downloadsSubscription != null && !downloadsSubscription.isUnsubscribed()) {
            downloadsSubscription.unsubscribe();
            downloadsSubscription = null;
        }

        if (threadsNumberSubscription != null && !threadsNumberSubscription.isUnsubscribed()) {
            threadsNumberSubscription.unsubscribe();
            threadsNumberSubscription = null;
        }
    }

    // Create a download object for every chapter in the event and add them to the downloads queue
    public void onDownloadChaptersEvent(DownloadChaptersEvent event) {
        final Manga manga = event.getManga();
        final Source source = sourceManager.get(manga.source);

        for (Chapter chapter : event.getChapters()) {
            Download download = new Download(source, manga, chapter);

            if (!prepareDownload(download)) {
                queue.add(download);
                if (isRunning) downloadsQueueSubject.onNext(download);
            }
        }
    }

    // Public method to check if a chapter is downloaded
    public boolean isChapterDownloaded(Source source, Manga manga, Chapter chapter) {
        File directory = getAbsoluteChapterDirectory(source, manga, chapter);
        if (!directory.exists())
            return false;

        List<Page> pages = getSavedPageList(source, manga, chapter);
        return isChapterDownloaded(directory, pages);
    }

    // Prepare the download. Returns true if the chapter is already downloaded
    private boolean prepareDownload(Download download) {
        // If the chapter is already queued, don't add it again
        for (Download queuedDownload : queue.get()) {
            if (download.chapter.id.equals(queuedDownload.chapter.id))
                return true;
        }

        // Add the directory to the download object for future access
        download.directory = getAbsoluteChapterDirectory(download);

        // If the directory doesn't exist, the chapter isn't downloaded.
        if (!download.directory.exists()) {
            return false;
        }

        // If the page list doesn't exist, the chapter isn't downloaded
        List<Page> savedPages = getSavedPageList(download);
        if (savedPages == null)
            return false;

        // Add the page list to the download object for future access
        download.pages = savedPages;

        // If the number of files matches the number of pages, the chapter is downloaded.
        // We have the index file, so we check one file more
        return isChapterDownloaded(download.directory, download.pages);
    }

    // Check that all the images are downloaded
    private boolean isChapterDownloaded(File directory, List<Page> pages) {
        return pages != null && pages.size() + 1 == directory.listFiles().length;
    }

    // Download the entire chapter
    private Observable<Boolean> downloadChapter(Download download) {
        try {
            DiskUtils.createDirectory(download.directory);
        } catch (IOException e) {
            Timber.e(e.getMessage());
        }

        Observable<List<Page>> pageListObservable = download.pages == null ?
                // Pull page list from network and add them to download object
                download.source
                        .pullPageListFromNetwork(download.chapter.url)
                        .doOnNext(pages -> download.pages = pages)
                        .doOnNext(pages -> savePageList(download)) :
                // Or if the page list already exists, start from the file
                Observable.just(download.pages);

        return pageListObservable
                .subscribeOn(Schedulers.io())
                .doOnNext(pages -> download.downloadedImages = 0)
                .doOnNext(pages -> download.setStatus(Download.DOWNLOADING))
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(pageList -> Observable.from(pageList)
                        .filter(page -> page.getImageUrl() != null)
                        .mergeWith(download.source.getRemainingImageUrlsFromPageList(pageList)))
                // Start downloading images, consider we can have downloaded images already
                .concatMap(page -> getDownloadedImage(page, download.source, download.directory))
                .doOnNext(p -> download.downloadedImages++)
                // Do after download completes
                .doOnCompleted(() -> onDownloadCompleted(download))
                .toList()
                .flatMap(pages -> Observable.just(areAllDownloadsFinished()));
    }

    // Get downloaded image if exists, otherwise download it with the method below
    public Observable<Page> getDownloadedImage(final Page page, Source source, File chapterDir) {
        Observable<Page> pageObservable = Observable.just(page);
        if (page.getImageUrl() == null)
            return pageObservable;

        String imageFilename = getImageFilename(page);
        File imagePath = new File(chapterDir, imageFilename);

        if (!isImageDownloaded(imagePath)) {
            page.setStatus(Page.DOWNLOAD_IMAGE);
            pageObservable = downloadImage(page, source, chapterDir, imageFilename);
        }

        return pageObservable
                // When the image is ready, set image path, progress (just in case) and status
                .doOnNext(p -> {
                    p.setImagePath(imagePath.getAbsolutePath());
                    p.setProgress(100);
                    p.setStatus(Page.READY);
                })
                // If the download fails, mark this page as error
                .doOnError(e -> page.setStatus(Page.ERROR))
                // Allow to download the remaining images
                .onErrorResumeNext(e -> Observable.just(page));
    }

    // Download the image and save it to the filesystem
    private Observable<Page> downloadImage(final Page page, Source source, File chapterDir, String imageFilename) {
        return source.getImageProgressResponse(page)
                .flatMap(resp -> {
                    try {
                        DiskUtils.saveBufferedSourceToDirectory(resp.body().source(), chapterDir, imageFilename);
                    } catch (IOException e) {
                        Timber.e(e.fillInStackTrace(), e.getMessage());
                        throw new IllegalStateException("Unable to save image");
                    }
                    return Observable.just(page);
                });
    }

    // Get the filename for an image given the page
    private String getImageFilename(Page page) {
        String url;
        try {
            url = new URL(page.getImageUrl()).getPath();
        } catch (MalformedURLException e) {
            url = page.getImageUrl();
        }
        return url.substring(
                url.lastIndexOf("/") + 1,
                url.length());
    }

    private boolean isImageDownloaded(File imagePath) {
        return imagePath.exists();
    }

    // Called when a download finishes. This doesn't mean the download was successful, so we check it
    private void onDownloadCompleted(final Download download) {
        checkDownloadIsSuccessful(download);
        savePageList(download);
    }

    private void checkDownloadIsSuccessful(final Download download) {
        int actualProgress = 0;
        int status = Download.DOWNLOADED;
        // If any page has an error, the download result will be error
        for (Page page : download.pages) {
            actualProgress += page.getProgress();
            if (page.getStatus() == Page.ERROR) status = Download.ERROR;
        }
        download.totalProgress = actualProgress;
        download.setStatus(status);
    }

    // Return the page list from the chapter's directory if it exists, null otherwise
    public List<Page> getSavedPageList(Source source, Manga manga, Chapter chapter) {
        List<Page> pages = null;
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        JsonReader reader = null;
        try {
            if (pagesFile.exists()) {
                reader = new JsonReader(new FileReader(pagesFile.getAbsolutePath()));
                Type collectionType = new TypeToken<List<Page>>() {}.getType();
                pages = gson.fromJson(reader, collectionType);
            }
        } catch (FileNotFoundException e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException e) { /* Do nothing */ }
        }
        return pages;
    }

    // Shortcut for the method above
    private List<Page> getSavedPageList(Download download) {
        return getSavedPageList(download.source, download.manga, download.chapter);
    }

    // Save the page list to the chapter's directory
    public void savePageList(Source source, Manga manga, Chapter chapter, List<Page> pages) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(pagesFile);
            out.write(gson.toJson(pages).getBytes());
            out.flush();
        } catch (IOException e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        } finally {
            if (out != null) try { out.close(); } catch (IOException e) { /* Do nothing */ }
        }
    }

    // Shortcut for the method above
    private void savePageList(Download download) {
        savePageList(download.source, download.manga, download.chapter, download.pages);
    }

    // Get the absolute path to the chapter directory
    public File getAbsoluteChapterDirectory(Source source, Manga manga, Chapter chapter) {
        String chapterRelativePath = source.getName() +
                File.separator +
                manga.title.replaceAll("[^a-zA-Z0-9.-]", "_") +
                File.separator +
                chapter.name.replaceAll("[^a-zA-Z0-9.-]", "_");

        return new File(preferences.getDownloadsDirectory(), chapterRelativePath);
    }

    // Shortcut for the method above
    private File getAbsoluteChapterDirectory(Download download) {
        return getAbsoluteChapterDirectory(download.source, download.manga, download.chapter);
    }

    public void deleteChapter(Source source, Manga manga, Chapter chapter) {
        File path = getAbsoluteChapterDirectory(source, manga, chapter);
        DiskUtils.deleteFiles(path);
        queue.remove(chapter);
    }

    public DownloadQueue getQueue() {
        return queue;
    }

    public boolean areAllDownloadsFinished() {
        for (Download download : queue.get()) {
            if (download.getStatus() <= Download.DOWNLOADING)
                return false;
        }
        return true;
    }

    public void resumeDownloads() {
        isQueuePaused = false;
        threadsNumber.onNext(preferences.getDownloadThreads());
    }

    public void pauseDownloads() {
        threadsNumber.onNext(0);
    }

    public boolean startDownloads() {
        boolean hasPendingDownloads = false;
        if (downloadsSubscription == null || threadsNumberSubscription == null)
            initializeSubscriptions();

        for (Download download : queue.get()) {
            if (download.getStatus() != Download.DOWNLOADED) {
                download.setStatus(Download.QUEUE);
                if (!hasPendingDownloads) hasPendingDownloads = true;
                downloadsQueueSubject.onNext(download);
            }
        }
        return hasPendingDownloads;
    }

    public void stopDownloads() {
        destroySubscriptions();
        for (Download download : queue.get()) {
            if (download.getStatus() == Download.DOWNLOADING) {
                download.setStatus(Download.ERROR);
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

}
