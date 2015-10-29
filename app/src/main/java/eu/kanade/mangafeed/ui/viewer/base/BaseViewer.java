package eu.kanade.mangafeed.ui.viewer.base;

import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;

import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;

public abstract class BaseViewer {

    protected ReaderActivity activity;
    protected FrameLayout container;
    protected int currentPosition;

    public BaseViewer(ReaderActivity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
    }

    public void updatePageNumber() {
        activity.onPageChanged(getCurrentPageIndex(currentPosition), getTotalPages());
    }

    // Returns the page index given a position in the viewer. Useful por a right to left viewer,
    // where the current page is the inverse of the position
    public int getCurrentPageIndex(int viewerPosition) {
        return viewerPosition;
    }

    public int getCurrentPosition() {
        return getCurrentPageIndex(currentPosition);
    }

    public abstract int getTotalPages();
    public abstract void setSelectedPage(int pageNumber);
    public abstract void onPageListReady(List<Page> pages);
    public abstract boolean onImageTouch(MotionEvent motionEvent);
}
