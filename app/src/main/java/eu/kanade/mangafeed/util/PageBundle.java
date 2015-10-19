package eu.kanade.mangafeed.util;

public class PageBundle<T> {

    public final int page;
    public final T data;

    public PageBundle(int page, T data) {
        this.page = page;
        this.data = data;
    }
}