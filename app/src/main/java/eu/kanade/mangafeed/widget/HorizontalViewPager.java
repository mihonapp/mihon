package eu.kanade.mangafeed.widget;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class HorizontalViewPager extends ViewPager {

    private GestureDetector gestureDetector;

    private OnChapterBoundariesOutListener mOnChapterBoundariesOutListener;
    private OnChapterSingleTapListener mOnChapterSingleTapListener;

    private static final float LEFT_REGION = 0.33f;
    private static final float RIGHT_REGION = 0.66f;
    private static final float SWIPE_TOLERANCE = 0.25f;
    private float startDragX;

    public HorizontalViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(getContext(), new ReaderViewGestureListener());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            gestureDetector.onTouchEvent(ev);

            if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                if (this.getCurrentItem() == 0 || this.getCurrentItem() == this.getAdapter().getCount() - 1) {
                    startDragX = ev.getX();
                }
            }

            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            // Do Nothing.
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            if (mOnChapterBoundariesOutListener != null) {
                if (this.getCurrentItem() == 0) {
                    if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                        float displacement = ev.getX() - startDragX;

                        if (ev.getX() > startDragX && displacement > getWidth() * SWIPE_TOLERANCE) {
                            mOnChapterBoundariesOutListener.onFirstPageOutEvent();
                            return true;
                        }

                        startDragX = 0;
                    }
                } else if (this.getCurrentItem() == this.getAdapter().getCount() - 1) {
                    if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                        float displacement = startDragX - ev.getX();

                        if (ev.getX() < startDragX && displacement > getWidth() * SWIPE_TOLERANCE) {
                            mOnChapterBoundariesOutListener.onLastPageOutEvent();
                            return true;
                        }

                        startDragX = 0;
                    }
                }
            }

            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            // Do Nothing.
        }

        return false;
    }

    public boolean onImageTouch(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    public interface OnChapterBoundariesOutListener {
        public void onFirstPageOutEvent();

        public void onLastPageOutEvent();
    }

    public interface OnChapterSingleTapListener {
        public void onSingleTap();
    }

    public void setOnChapterBoundariesOutListener(OnChapterBoundariesOutListener onChapterBoundariesOutListener) {
        mOnChapterBoundariesOutListener = onChapterBoundariesOutListener;
    }

    public void setOnChapterSingleTapListener(OnChapterSingleTapListener onChapterSingleTapListener) {
        mOnChapterSingleTapListener = onChapterSingleTapListener;
    }


    private class ReaderViewGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            final int position = getCurrentItem();
            final float positionX = e.getX();

            if (positionX < getWidth() * LEFT_REGION) {
                if (position != 0) {
                    setCurrentItem(position - 1, true);
                } else {
                    if (mOnChapterBoundariesOutListener != null) {
                        mOnChapterBoundariesOutListener.onFirstPageOutEvent();
                    }
                }
            } else if (positionX > getWidth() * RIGHT_REGION) {
                if (position != getAdapter().getCount() - 1) {
                    setCurrentItem(position + 1, true);
                } else {
                    if (mOnChapterBoundariesOutListener != null) {
                        mOnChapterBoundariesOutListener.onLastPageOutEvent();
                    }
                }
            } else {
                if (mOnChapterSingleTapListener != null) {
                    mOnChapterSingleTapListener.onSingleTap();
                }
            }

            return true;
        }

    }

}