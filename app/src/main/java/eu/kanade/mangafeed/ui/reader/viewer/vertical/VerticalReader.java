package eu.kanade.mangafeed.ui.reader.viewer.vertical;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.mangafeed.ui.reader.viewer.common.PagerReader;

public class VerticalReader extends PagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        VerticalPager pager = new VerticalPager(getActivity());
        initializePager(pager);
        return pager;
    }

    @Override
    public void onFirstPageOut() {
        requestPreviousChapter();
    }

    @Override
    public void onLastPageOut() {
        requestNextChapter();
    }

}
