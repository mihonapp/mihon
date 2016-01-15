package eu.kanade.tachiyomi.data.network;

public interface ProgressListener {
    void update(long bytesRead, long contentLength, boolean done);
}