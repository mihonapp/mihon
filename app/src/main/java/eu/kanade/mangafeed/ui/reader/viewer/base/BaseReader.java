package eu.kanade.mangafeed.ui.reader.viewer.base;

import android.view.MotionEvent;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.ReaderPresenter;
import eu.kanade.mangafeed.util.ToastUtil;

public abstract class BaseReader {

    protected ReaderActivity activity;
    protected ReaderPresenter presenter;
    protected ViewGroup container;
    protected int currentPosition;

    public BaseReader(ReaderActivity activity) {
        this.activity = activity;
        this.container = activity.getContainer();
        this.presenter = activity.getPresenter();
    }

    public void updatePageNumber() {
        activity.onPageChanged(getCurrentPosition(), getTotalPages());
    }

    // Returns the page index given a position in the viewer. Useful por a right to left viewer,
    // where the current page is the inverse of the position
    public int getCurrentPageIndex(int viewerPosition) {
        return viewerPosition;
    }

    public int getCurrentPosition() {
        return getCurrentPageIndex(currentPosition);
    }

    public void requestNextChapter() {
        if (presenter.hasNextChapter()) {
            presenter.setCurrentPage(getCurrentPosition());
            presenter.loadNextChapter();
        } else {
            ToastUtil.showShort(activity, R.string.no_next_chapter);
        }

    }

    public void requestPreviousChapter() {
        if (presenter.hasPreviousChapter()) {
            presenter.setCurrentPage(getCurrentPosition());
            presenter.loadPreviousChapter();
        } else {
            ToastUtil.showShort(activity, R.string.no_previous_chapter);
        }
    }

    public void onPageChanged(int position) {
        currentPosition = position;
        updatePageNumber();
    }

    public void destroy() {}

    public abstract int getTotalPages();
    public abstract void setSelectedPage(int pageNumber);
    public abstract void onPageListReady(List<Page> pages);
    public abstract boolean onImageTouch(MotionEvent motionEvent);
}
