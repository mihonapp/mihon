package eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical;

import android.content.Context;
import android.view.MotionEvent;

import eu.kanade.tachiyomi.ui.reader.viewer.pager.OnChapterBoundariesOutListener;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager;
import rx.functions.Action1;

public class VerticalPager extends VerticalViewPagerImpl implements Pager {

    private OnChapterBoundariesOutListener onChapterBoundariesOutListener;

    private static final float SWIPE_TOLERANCE = 0.25f;
    private float startDragY;

    public VerticalPager(Context context) {
        super(context);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                if (getCurrentItem() == 0 || getCurrentItem() == getAdapter().getCount() - 1) {
                    startDragY = ev.getY();
                }
            }

            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            if (onChapterBoundariesOutListener != null) {
                if (getCurrentItem() == 0) {
                    if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                        float displacement = ev.getY() - startDragY;

                        if (ev.getY() > startDragY && displacement > getHeight() * SWIPE_TOLERANCE) {
                            onChapterBoundariesOutListener.onFirstPageOutEvent();
                            return true;
                        }

                        startDragY = 0;
                    }
                } else if (getCurrentItem() == getAdapter().getCount() - 1) {
                    if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                        float displacement = startDragY - ev.getY();

                        if (ev.getY() < startDragY && displacement > getHeight() * SWIPE_TOLERANCE) {
                            onChapterBoundariesOutListener.onLastPageOutEvent();
                            return true;
                        }

                        startDragY = 0;
                    }
                }
            }

            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void setOnChapterBoundariesOutListener(OnChapterBoundariesOutListener listener) {
        onChapterBoundariesOutListener = listener;
    }

    @Override
    public void setOnPageChangeListener(Action1<Integer> function) {
        addOnPageChangeListener(new SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                function.call(position);
            }
        });
    }
    
}
