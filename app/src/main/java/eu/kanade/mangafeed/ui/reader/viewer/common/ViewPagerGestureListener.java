package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class ViewPagerGestureListener extends GestureDetector.SimpleOnGestureListener {

    private ViewPagerInterface viewPager;

    private static final float LEFT_REGION = 0.33f;
    private static final float RIGHT_REGION = 0.66f;

    public ViewPagerGestureListener(ViewPagerInterface viewPager) {
        this.viewPager = viewPager;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        final int position = viewPager.getCurrentItem();
        final float positionX = e.getX();

        if (positionX < viewPager.getWidth() * LEFT_REGION) {
            if (position != 0) {
                onLeftSideTap();
            } else {
                onFirstPageOut();
            }
        } else if (positionX > viewPager.getWidth() * RIGHT_REGION) {
            if (position != viewPager.getAdapter().getCount() - 1) {
                onRightSideTap();
            } else {
                onLastPageOut();
            }
        } else {
            onCenterTap();
        }

        return true;
    }

    private void onLeftSideTap() {
        if (viewPager.getChapterSingleTapListener() != null) {
            viewPager.getChapterSingleTapListener().onLeftSideTap();
        }
    }

    private void onRightSideTap() {
        if (viewPager.getChapterSingleTapListener() != null) {
            viewPager.getChapterSingleTapListener().onRightSideTap();
        }
    }

    private void onCenterTap() {
        if (viewPager.getChapterSingleTapListener() != null) {
            viewPager.getChapterSingleTapListener().onCenterTap();
        }
    }

    private void onFirstPageOut() {
        if (viewPager.getChapterBoundariesListener() != null) {
            viewPager.getChapterBoundariesListener().onFirstPageOutEvent();
        }
    }

    private void onLastPageOut() {
        if (viewPager.getChapterBoundariesListener() != null) {
            viewPager.getChapterBoundariesListener().onLastPageOutEvent();
        }
    }

}
