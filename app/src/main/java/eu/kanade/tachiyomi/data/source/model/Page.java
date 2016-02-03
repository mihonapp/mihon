package eu.kanade.tachiyomi.data.source.model;

import java.io.Serializable;
import java.util.List;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.network.ProgressListener;
import rx.subjects.PublishSubject;

public class Page implements ProgressListener {

    private int pageNumber;
    private String url;
    private String imageUrl;
    private transient Chapter chapter;
    private transient String imagePath;
    private transient volatile int status;
    private transient volatile int progress;

    private transient PublishSubject<Integer> statusSubject;

    public static final int QUEUE = 0;
    public static final int LOAD_PAGE = 1;
    public static final int DOWNLOAD_IMAGE = 2;
    public static final int READY = 3;
    public static final int ERROR = 4;

    public Page(int pageNumber, String url, String imageUrl, String imagePath) {
        this.pageNumber = pageNumber;
        this.url = url;
        this.imageUrl = imageUrl;
        this.imagePath = imagePath;
    }

    public Page(int pageNumber, String url) {
        this(pageNumber, url, null, null);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        if (statusSubject != null)
            statusSubject.onNext(status);
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int value) {
        progress = value;
    }

    @Override
    public void update(long bytesRead, long contentLength, boolean done) {
        progress = (int) ((100 * bytesRead) / contentLength);
    }

    public void setStatusSubject(PublishSubject<Integer> subject) {
        this.statusSubject = subject;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public void setChapter(Chapter chapter) {
        this.chapter = chapter;
    }

    public boolean isLastPage() {
        List<Page> chapterPages = chapter.getPages();
        return chapterPages.size() -1 == pageNumber;
    }
}
