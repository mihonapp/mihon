package eu.kanade.tachiyomi.ui.reader.viewer.base;

import android.view.MotionEvent;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.RapidImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import java.util.List;

import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;

public abstract class BaseReader extends BaseFragment {

    protected int currentPage;
    protected List<Page> pages;
    protected Class<? extends ImageRegionDecoder> regionDecoderClass;

    public static final int RAPID_DECODER = 0;
    public static final int SKIA_DECODER = 1;

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

    public void setRegionDecoderClass(int value) {
        switch (value) {
            case RAPID_DECODER:
            default:
                regionDecoderClass = RapidImageRegionDecoder.class;
                break;
            case SKIA_DECODER:
                regionDecoderClass = SkiaImageRegionDecoder.class;
                break;
        }
    }

    public Class<? extends ImageRegionDecoder> getRegionDecoderClass() {
        return regionDecoderClass;
    }

    public ReaderActivity getReaderActivity() {
        return (ReaderActivity) getActivity();
    }
}
