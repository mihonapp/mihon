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
    private BehaviorSubject<Boolean> runningSubject;
    private Subscription downloadsSubscription;
    private Subscription threadsNumberSubscription;

    private DownloadQueue queue;
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
        runningSubject = BehaviorSubject.create();
    }

    private void initializeSubscriptions() {
        if (downloadsSubscription != null && !downloadsSubscription.isUnsubscribed())
            downloadsSubscription.unsubscribe();

        if (threadsNumberSubscription != null && !threadsNumberSubscription.isUnsubscribed())
            threadsNumberSubscription.unsubscribe();

        threadsNumberSubscription = preferences.downloadThreads().asObservable()
                .subscribe(threadsNumber::onNext);

        downloadsSubscription = downloadsQueueSubject
                .lift(new DynamicConcurrentMergeOperator<>(this::downloadChapter, threadsNumber))
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .map(download -> areAllDownloadsFinished())
                .subscribe(finished -> {
                    if (finished) {
                        DownloadService.stop(context);
                    }
                }, e -> Timber.e(e.getCause(), e.getMessage()));

        if (!isRunning) {
            isRunning = true;
            runningSubject.onNext(true);
        }
    }

    public void destroySubscriptions() {
        if (isRunning) {
            isRunning = false;
            runningSubject.onNext(false);
        }

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
        for (Download queuedDownload : queue) {
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
    private Observable<Download> downloadChapter(Download download) {
        try {
            DiskUtils.createDirectory(download.directory);
        } catch (IOException e) {
            return Observable.error(e);
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
                .doOnNext(pages -> {
                    download.downloadedImages = 0;
                    download.setStatus(Download.DOWNLOADING);
                })
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(download.source::getAllImageUrlsFromPageList)
                // Start downloading images, consider we can have downloaded images already
                .concatMap(page -> getOrDownloadImage(page, download))
                // Do after download completes
                .doOnCompleted(() -> onDownloadCompleted(download))
                .toList()
                .map(pages -> download)
                // If the page list threw, it will resume here
                .onErrorResumeNext(error -> {
                    download.setStatus(Download.ERROR);
                    return Observable.just(download);
                });
    }

    // Get the image from the filesystem if it exists or download from network
    private Observable<Page> getOrDownloadImage(final Page page, Download download) {
        // If the image URL is empty, do nothing
        if (page.getImageUrl() == null)
            return Observable.just(page);

        String filename = getImageFilename(page);
        File imagePath = new File(download.directory, filename);

        // If the image is already downloaded, do nothing. Otherwise download from network
        Observable<Page> pageObservable = isImageDownloaded(imagePath) ?
                Observable.just(page) :
                downloadImage(page, download.source, download.directory, filename);

        return pageObservable
                // When the image is ready, set image path, progress (just in case) and status
                .doOnNext(p -> {
                    page.setImagePath(imagePath.getAbsolutePath());
                    page.setProgress(100);
                    download.downloadedImages++;
                    page.setStatus(Page.READY);
                })
                // Mark this page as error and allow to download the remaining
                .onErrorResumeNext(e -> {
                    page.setProgress(0);
                    page.setStatus(Page.ERROR);
                    return Observable.just(page);
                });
    }

    // Save image on disk
    private Observable<Page> downloadImage(Page page, Source source, File directory, String filename) {
        page.setStatus(Page.DOWNLOAD_IMAGE);
        return source.getImageProgressResponse(page)
                .flatMap(resp -> {
                    try {
                        DiskUtils.saveBufferedSourceToDirectory(resp.body().source(), directory, filename);
                    } catch (Exception e) {
                        Timber.e(e.getCause(), e.getMessage());
                        return Observable.error(e);
                    }
                    return Observable.just(page);
                })
                .retry(2);
    }

    // Public method to get the image from the filesystem. It does NOT provide any way to download the image
    public Observable<Page> getDownloadedImage(final Page page, File chapterDir) {
        if (page.getImageUrl() == null) {
            page.setStatus(Page.ERROR);
            return Observable.just(page);
        }

        File imagePath = new File(chapterDir, getImageFilename(page));

        // When the image is ready, set image path, progress (just in case) and status
        if (isImageDownloaded(imagePath)) {
            page.setImagePath(imagePath.getAbsolutePath());
            page.setProgress(100);
            page.setStatus(Page.READY);
        } else {
            page.setStatus(Page.ERROR);
        }
        return Observable.just(page);
    }

    // Get the filename for an image given the page
    private String getImageFilename(Page page) {
        String url = page.getImageUrl();
        return url.substring(url.lastIndexOf("/") + 1, url.length());
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
            if (page.getStatus() != Page.READY) status = Download.ERROR;
        }
        // Ensure that the chapter folder has all the images
        if (!isChapterDownloaded(download.directory, download.pages)) {
            status = Download.ERROR;
        }
        download.totalProgress = actualProgress;
        download.setStatus(status);
        // Delete successful downloads from queue after notifying
        if (status == Download.DOWNLOADED) {
            queue.remove(download);
        }
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
            Timber.e(e.getCause(), e.getMessage());
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
            Timber.e(e.getCause(), e.getMessage());
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
    }

    public DownloadQueue getQueue() {
        return queue;
    }

    public boolean areAllDownloadsFinished() {
        for (Download download : queue) {
            if (download.getStatus() <= Download.DOWNLOADING)
                return false;
        }
        return true;
    }

    public boolean startDownloads() {
        if (queue.isEmpty())
            return false;

        boolean hasPendingDownloads = false;
        if (downloadsSubscription == null || threadsNumberSubscription == null)
            initializeSubscriptions();

        for (Download download : queue) {
            if (download.getStatus() != Download.DOWNLOADED) {
                if (download.getStatus() != Download.QUEUE) download.setStatus(Download.QUEUE);
                if (!hasPendingDownloads) hasPendingDownloads = true;
                downloadsQueueSubject.onNext(download);
            }
        }
        return hasPendingDownloads;
    }

    public void stopDownloads() {
        destroySubscriptions();
        for (Download download : queue) {
            if (download.getStatus() == Download.DOWNLOADING) {
                download.setStatus(Download.ERROR);
            }
        }
    }

    public BehaviorSubject<Boolean> getRunningSubject() {
        return runningSubject;
    }

}
