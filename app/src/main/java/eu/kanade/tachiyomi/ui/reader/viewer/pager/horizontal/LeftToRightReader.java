package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal;

public class LeftToRightReader extends HorizontalReader {

    @Override
    public void onFirstPageOut() {
        getReaderActivity().requestPreviousChapter();
    }

    @Override
    public void onLastPageOut() {
        getReaderActivity().requestNextChapter();
    }

}
