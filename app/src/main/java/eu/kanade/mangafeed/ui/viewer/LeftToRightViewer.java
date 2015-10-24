package eu.kanade.mangafeed.ui.viewer;

import android.widget.FrameLayout;

import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.viewer.base.ViewPagerViewer;

public class LeftToRightViewer extends ViewPagerViewer {

    public LeftToRightViewer(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
    }

    @Override
    public void onFirstPageOut() {

    }

    @Override
    public void onLastPageOut() {

    }

}
