package eu.kanade.tachiyomi.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

public class AutofitRecyclerView extends RecyclerView {

    private GridLayoutManager manager;
    private int columnWidth = -1;
    private int spanCount = 0;

    public AutofitRecyclerView(Context context) {
        super(context);
        init(context, null);
    }

    public AutofitRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AutofitRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            int[] attrsArray = {
                    android.R.attr.columnWidth
            };
            TypedArray array = context.obtainStyledAttributes(attrs, attrsArray);
            columnWidth = array.getDimensionPixelSize(0, -1);
            array.recycle();
        }

        manager = new GridLayoutManager(getContext(), 1);
        setLayoutManager(manager);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (spanCount == 0 && columnWidth > 0) {
            int spanCount = Math.max(1, getMeasuredWidth() / columnWidth);
            manager.setSpanCount(spanCount);
        }
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = spanCount;
        if (spanCount > 0) {
            manager.setSpanCount(spanCount);
        }
    }

    public int getSpanCount() {
        return manager.getSpanCount();
    }

    public int getItemWidth() {
        return getMeasuredWidth() / getSpanCount();
    }

}