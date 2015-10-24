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
        activity.onPageChanged(getCurrentPageFromPos(currentPosition), getTotalPages());
    }

    public int getCurrentPageFromPos(int position) {
        return position + 1;
    }

    public int getPosFromPage(Page page) {
        return page.getPageNumber();
    }

    public abstract int getTotalPages();
    public abstract void onImageReady(Page page);
    public abstract void onPageListReady(List<Page> pages);
    public abstract boolean onImageTouch(MotionEvent motionEvent);
}
