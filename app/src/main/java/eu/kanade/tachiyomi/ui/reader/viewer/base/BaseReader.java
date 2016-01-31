package eu.kanade.tachiyomi.ui.reader.viewer.base;

import android.view.MotionEvent;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.RapidImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import java.util.List;

import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;

public abstract class BaseReader extends BaseFragment {

    protected int currentPage;
    protected List<Page> pages;
    protected Class<? extends ImageRegionDecoder> regionDecoderClass;
    protected Class<? extends ImageDecoder> bitmapDecoderClass;

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
