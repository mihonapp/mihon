package eu.kanade.mangafeed.ui.reader.viewer.vertical;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;

public class VerticalViewPager extends fr.castorflex.android.verticalviewpager.VerticalViewPager {

    private GestureDetector gestureDetector;

    public VerticalViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(getContext(), new ReaderViewGestureListener());
    }

    private class ReaderViewGestureListener extends GestureDetector.SimpleOnGestureListener {
        // TODO
    }
}
