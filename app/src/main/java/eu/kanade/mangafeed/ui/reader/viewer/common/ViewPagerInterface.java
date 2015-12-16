package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.support.v4.view.PagerAdapter;
import android.view.MotionEvent;

public interface ViewPagerInterface {

    void setOffscreenPageLimit(int limit);

    int getCurrentItem();
    void setCurrentItem(int item, boolean smoothScroll);

    int getWidth();
    int getHeight();

    PagerAdapter getAdapter();
    void setAdapter(PagerAdapter adapter);

    boolean onImageTouch(MotionEvent motionEvent);

    void setOnChapterBoundariesOutListener(OnChapterBoundariesOutListener listener);
    void setOnChapterSingleTapListener(OnChapterSingleTapListener listener);

    OnChapterBoundariesOutListener getChapterBoundariesListener();
    OnChapterSingleTapListener getChapterSingleTapListener();

}
