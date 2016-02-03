package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.support.v4.view.PagerAdapter;
import android.view.ViewGroup;

import rx.functions.Action1;

public interface Pager {

    void setId(int id);
    void setLayoutParams(ViewGroup.LayoutParams layoutParams);

    void setOffscreenPageLimit(int limit);

    int getCurrentItem();
    void setCurrentItem(int item, boolean smoothScroll);

    int getWidth();
    int getHeight();

    PagerAdapter getAdapter();
    void setAdapter(PagerAdapter adapter);

    void setOnChapterBoundariesOutListener(OnChapterBoundariesOutListener listener);

    void setOnPageChangeListener(Action1<Integer> onPageChanged);
    void clearOnPageChangeListeners();
}
