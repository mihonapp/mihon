package eu.kanade.tachiyomi.widget;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public class PreCachingLayoutManager extends LinearLayoutManager {

    private static final int DEFAULT_EXTRA_LAYOUT_SPACE = 600;
    private int extraLayoutSpace = -1;

    public PreCachingLayoutManager(Context context) {
        super(context);
    }

    public void setExtraLayoutSpace(int extraLayoutSpace) {
        this.extraLayoutSpace = extraLayoutSpace;
    }

    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        if (extraLayoutSpace > 0) {
            return extraLayoutSpace;
        }
        return DEFAULT_EXTRA_LAYOUT_SPACE;
    }

}
