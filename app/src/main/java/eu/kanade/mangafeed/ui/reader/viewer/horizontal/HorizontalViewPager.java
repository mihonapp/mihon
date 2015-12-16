package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import eu.kanade.mangafeed.ui.reader.viewer.common.OnChapterBoundariesOutListener;
import eu.kanade.mangafeed.ui.reader.viewer.common.OnChapterSingleTapListener;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerGestureListener;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerInterface;

public class HorizontalViewPager extends ViewPager implements ViewPagerInterface {

    private GestureDetector gestureDetector;

    private OnChapterBoundariesOutListener onChapterBoundariesOutListener;
    private OnChapterSingleTapListener onChapterSingleTapListener;

    private static final float SWIPE_TOLERANCE = 0.25f;
    private float startDragX;

    public HorizontalViewPager(Context context) {
        super(context);
        init(context);
    }

    public HorizontalViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, new ViewPagerGestureListener(this));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            if (getCurrentItem() == 0 || getCurrentItem() == getAdapter().getCount() - 1) {
                startDragX = ev.getX();
            }
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (onChapterBoundariesOutListener != null) {
            if (getCurrentItem() == 0) {
                if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    float displacement = ev.getX() - startDragX;

                    if (ev.getX() > startDragX && displacement > getWidth() * SWIPE_TOLERANCE) {
                        onChapterBoundariesOutListener.onFirstPageOutEvent();
                        return true;
                    }

                    startDragX = 0;
                }
            } else if (getCurrentItem() == getAdapter().getCount() - 1) {
                if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    float displacement = startDragX - ev.getX();

                    if (ev.getX() < startDragX && displacement > getWidth() * SWIPE_TOLERANCE) {
                        onChapterBoundariesOutListener.onLastPageOutEvent();
                        return true;
                    }

                    startDragX = 0;
                }
            }
        }

        return super.onTouchEvent(ev);
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

}