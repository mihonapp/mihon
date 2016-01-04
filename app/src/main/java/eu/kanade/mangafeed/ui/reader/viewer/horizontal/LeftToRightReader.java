package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

public class LeftToRightReader extends HorizontalReader {

    @Override
    public void onFirstPageOut() {
        requestPreviousChapter();
    }

    @Override
    public void onLastPageOut() {
        requestNextChapter();
    }

}
