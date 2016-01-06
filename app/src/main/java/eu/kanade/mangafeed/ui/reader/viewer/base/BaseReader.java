package eu.kanade.mangafeed.ui.reader.viewer.base;

import android.view.MotionEvent;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;

public abstract class BaseReader extends BaseFragment {

    protected int currentPage;
    protected List<Page> pages;

    public void updatePageNumber() {
        getReaderActivity().onPageChanged(getCurrentPage(), getTotalPages());
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageForPosition(int position) {
        return position;
    }

    public int getPositionForPage(int page) {
        return page;
    }

    public void onPageChanged(int position) {
        currentPage = getPageForPosition(position);
        updatePageNumber();
    }

    public int getTotalPages() {
        return pages == null ? 0 : pages.size();
    }

    public abstract void setSelectedPage(int pageNumber);
    public abstract void onPageListReady(List<Page> pages, int currentPage);
    public abstract boolean onImageTouch(MotionEvent motionEvent);

    public ReaderActivity getReaderActivity() {
        return (ReaderActivity) getActivity();
    }
}
