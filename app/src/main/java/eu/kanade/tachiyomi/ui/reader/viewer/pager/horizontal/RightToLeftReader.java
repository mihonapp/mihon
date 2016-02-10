package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader;

public class RightToLeftReader extends PagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        HorizontalPager pager = new HorizontalPager(getActivity());
        pager.setRotation(180);
        initializePager(pager);
        return pager;
    }

    @Override
    protected void onLeftSideTap() {
        moveToNext();
    }

    @Override
    protected void onRightSideTap() {
        moveToPrevious();
    }

}
