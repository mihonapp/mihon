package eu.kanade.mangafeed.event;

public class DownloadStatusEvent {

    private long chapterId;
    private int status;

    public DownloadStatusEvent(long chapterId, int status) {
        this.chapterId = chapterId;
        this.status = status;
    }

    public long getChapterId() {
        return chapterId;
    }

    public int getStatus() {
        return status;
    }

}
