package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerReader;

public abstract class HorizontalReader extends ViewPagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        HorizontalViewPager pager = new HorizontalViewPager(getActivity());
        initializePager(pager);
        return pager;
    }

}
