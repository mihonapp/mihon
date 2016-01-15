package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.kanade.tachiyomi.data.source.model.Page;

public class RightToLeftReader extends HorizontalReader {

    @Override
    public void onPageListReady(List<Page> pages, int currentPage) {
        ArrayList<Page> inversedPages = new ArrayList<>(pages);
        Collections.reverse(inversedPages);
        super.onPageListReady(inversedPages, currentPage);
    }

    @Override
    public int getPageForPosition(int position) {
        return (getTotalPages() - 1) - position;
    }

    @Override
    public int getPositionForPage(int page) {
        return (getTotalPages() - 1) - page;
    }

    @Override
    public void onFirstPageOut() {
        getReaderActivity().requestNextChapter();
    }

    @Override
    public void onLastPageOut() {
        getReaderActivity().requestPreviousChapter();
    }

}
