package eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import eu.kanade.tachiyomi.ui.reader.viewer.base.OnChapterBoundariesOutListener;
import eu.kanade.tachiyomi.ui.reader.viewer.base.OnChapterSingleTapListener;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerGestureListener;
import rx.functions.Action1;

public class VerticalPager extends VerticalViewPagerImpl implements Pager {

    private GestureDetector gestureDetector;

    private OnChapterBoundariesOutListener onChapterBoundariesOutListener;
    private OnChapterSingleTapListener onChapterSingleTapListener;

    private static final float SWIPE_TOLERANCE = 0.25f;
    private float startDragY;

    public VerticalPager(Context context) {
        super(context);
        init(context);
    }

    public VerticalPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, new VerticalPagerGestureListener(this));
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
            return true;
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
            return true;
        }
    }

    @Override
    public boolean onImageTouch(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public void setOnChapterBoundariesOutListener(OnChapterBoundariesOutListener listener) {
        onChapterBoundariesOutListener = listener;
    }

    @Override
    public void setOnChapterSingleTapListener(OnChapterSingleTapListener listener) {
        onChapterSingleTapListener = listener;
    }

    @Override
    public OnChapterBoundariesOutListener getChapterBoundariesListener() {
        return onChapterBoundariesOutListener;
    }

    @Override
    public OnChapterSingleTapListener getChapterSingleTapListener() {
        return onChapterSingleTapListener;
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

    private static class VerticalPagerGestureListener extends PagerGestureListener {

        public VerticalPagerGestureListener(Pager pager) {
            super(pager);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            // Vertical view pager ignores scrolling events sometimes.
            // Returning true here fixes it, but we lose touch events on the image like
            // double tap to zoom
            return true;
        }
    }
    
}
