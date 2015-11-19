package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import eu.kanade.mangafeed.ui.reader.ReaderActivity;

public class LeftToRightReader extends HorizontalReader {

    public LeftToRightReader(ReaderActivity activity) {
        super(activity);
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
