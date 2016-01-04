package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.mangafeed.ui.reader.viewer.common.PagerReader;

public abstract class HorizontalReader extends PagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        HorizontalPager pager = new HorizontalPager(getActivity());
        initializePager(pager);
        return pager;
    }

}
