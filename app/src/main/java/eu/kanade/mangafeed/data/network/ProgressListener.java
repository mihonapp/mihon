package eu.kanade.mangafeed.data.network;

public interface ProgressListener {
    void update(long bytesRead, long contentLength, boolean done);
}