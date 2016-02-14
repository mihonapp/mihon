package eu.kanade.tachiyomi.ui.reader.viewer.base;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.RapidImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;

public abstract class BaseReader extends BaseFragment {

    protected int currentPage;
    protected List<Page> pages;
    protected List<Chapter> chapters;
    protected Class<? extends ImageRegionDecoder> regionDecoderClass;
    protected Class<? extends ImageDecoder> bitmapDecoderClass;

    private boolean hasRequestedNextChapter;

    public static final int RAPID_DECODER = 0;
    public static final int SKIA_DECODER = 1;

    public void updatePageNumber() {
        getReaderActivity().onPageChanged(getCurrentPage().getPageNumber(), getCurrentPage().getChapter().getPages().size());
    }

    public Page getCurrentPage() {
        return pages.get(currentPage);
    }

    public void onPageChanged(int position) {
        Page oldPage = pages.get(currentPage);
        Page newPage = pages.get(position);
        newPage.getChapter().last_page_read = newPage.getPageNumber();

        if (getReaderActivity().getPresenter().isSeamlessMode()) {
            Chapter oldChapter = oldPage.getChapter();
            Chapter newChapter = newPage.getChapter();
            if (!hasRequestedNextChapter && position > pages.size() - 5) {
                hasRequestedNextChapter = true;
                getReaderActivity().getPresenter().appendNextChapter();
            }
            if (!oldChapter.id.equals(newChapter.id)) {
                onChapterChanged(newPage.getChapter(), newPage);
            }
        }
        currentPage = position;
        updatePageNumber();
    }

    private void onChapterChanged(Chapter chapter, Page currentPage) {
        getReaderActivity().onEnterChapter(chapter, currentPage.getPageNumber());
    }

    public void setSelectedPage(Page page) {
        setSelectedPage(getPageIndex(page));
    }

    public int getPageIndex(Page search) {
        // search for the index of a page in the current list without requiring them to be the same object
        for (Page page : pages) {
            if (page.getPageNumber() == search.getPageNumber() &&
                    page.getChapter().id.equals(search.getChapter().id)) {
                return pages.indexOf(page);
            }
        }
        return 0;
    }

    public void onPageListReady(Chapter chapter, Page currentPage) {
        if (chapters == null || !chapters.contains(chapter)) {
            // if we reset the loaded page we also need to reset the loaded chapters
            chapters = new ArrayList<>();
            chapters.add(chapter);
            onSetChapter(chapter, currentPage);
        } else {
            setSelectedPage(currentPage);
        }
    }

    public void onPageListAppendReady(Chapter chapter) {
        if (!chapters.contains(chapter)) {
            hasRequestedNextChapter = false;
            chapters.add(chapter);
            onAppendChapter(chapter);
        }
    }

    public abstract void setSelectedPage(int pageNumber);
    public abstract void onSetChapter(Chapter chapter, Page currentPage);
    public abstract void onAppendChapter(Chapter chapter);
    public abstract void moveToNext();
    public abstract void moveToPrevious();

    public void setDecoderClass(int value) {
        switch (value) {
            case RAPID_DECODER:
            default:
                regionDecoderClass = RapidImageRegionDecoder.class;
                bitmapDecoderClass = SkiaImageDecoder.class;
                // Using Skia because Rapid isn't stable. Rapid is still used for region decoding.
                // https://github.com/inorichi/tachiyomi/issues/97
                //bitmapDecoderClass = RapidImageDecoder.class;
                break;
            case SKIA_DECODER:
                regionDecoderClass = SkiaImageRegionDecoder.class;
                bitmapDecoderClass = SkiaImageDecoder.class;
                break;
        }
    }

    public Class<? extends ImageRegionDecoder> getRegionDecoderClass() {
        return regionDecoderClass;
    }

    public Class<? extends ImageDecoder> getBitmapDecoderClass() {
        return bitmapDecoderClass;
    }

    public ReaderActivity getReaderActivity() {
        return (ReaderActivity) getActivity();
    }
}
