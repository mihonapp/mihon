package eu.kanade.mangafeed.util.events;

public class ChapterCountEvent {
    private int count;

    public ChapterCountEvent(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
