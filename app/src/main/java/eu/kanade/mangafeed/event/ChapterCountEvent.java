package eu.kanade.mangafeed.event;

public class ChapterCountEvent {
    private int count;

    public ChapterCountEvent(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
