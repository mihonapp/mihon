package eu.kanade.mangafeed.ui.reader.viewer.pager.vertical;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.mangafeed.ui.reader.viewer.pager.PagerReader;
import eu.kanade.mangafeed.ui.reader.viewer.pager.vertical.VerticalPager;

public class VerticalReader extends PagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        VerticalPager pager = new VerticalPager(getActivity());
        initializePager(pager);
        return pager;
    }

    @Override
    public void onFirstPageOut() {
        getReaderActivity().requestPreviousChapter();
    }

    @Override
    public void onLastPageOut() {
        getReaderActivity().requestNextChapter();
    }

}
