package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class PagerGestureListener extends GestureDetector.SimpleOnGestureListener {

    private Pager pager;

    private static final float LEFT_REGION = 0.33f;
    private static final float RIGHT_REGION = 0.66f;

    public PagerGestureListener(Pager pager) {
        this.pager = pager;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        final int position = pager.getCurrentItem();
        final float positionX = e.getX();

        if (positionX < pager.getWidth() * LEFT_REGION) {
            if (position != 0) {
                onLeftSideTap();
            } else {
                onFirstPageOut();
            }
        } else if (positionX > pager.getWidth() * RIGHT_REGION) {
            if (position != pager.getAdapter().getCount() - 1) {
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
        if (pager.getChapterSingleTapListener() != null) {
            pager.getChapterSingleTapListener().onLeftSideTap();
        }
    }

    private void onRightSideTap() {
        if (pager.getChapterSingleTapListener() != null) {
            pager.getChapterSingleTapListener().onRightSideTap();
        }
    }

    private void onCenterTap() {
        if (pager.getChapterSingleTapListener() != null) {
            pager.getChapterSingleTapListener().onCenterTap();
        }
    }

    private void onFirstPageOut() {
        if (pager.getChapterBoundariesListener() != null) {
            pager.getChapterBoundariesListener().onFirstPageOutEvent();
        }
    }

    private void onLastPageOut() {
        if (pager.getChapterBoundariesListener() != null) {
            pager.getChapterBoundariesListener().onLastPageOutEvent();
        }
    }

}
