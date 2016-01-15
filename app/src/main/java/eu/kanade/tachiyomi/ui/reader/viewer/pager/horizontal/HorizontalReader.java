package eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader;

public abstract class HorizontalReader extends PagerReader {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        HorizontalPager pager = new HorizontalPager(getActivity());
        initializePager(pager);
        return pager;
    }

}
