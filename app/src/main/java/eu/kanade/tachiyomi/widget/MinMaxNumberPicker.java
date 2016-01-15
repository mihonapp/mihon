package eu.kanade.tachiyomi.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.NumberPicker;

import eu.kanade.tachiyomi.R;

public class MinMaxNumberPicker extends NumberPicker{

    public MinMaxNumberPicker(Context context) {
        super(context);
    }

    public MinMaxNumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        processAttributeSet(context, attrs);
    }

    public MinMaxNumberPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        processAttributeSet(context, attrs);
    }

    private void processAttributeSet(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MinMaxNumberPicker, 0, 0);
        try {
            setMinValue(ta.getInt(R.styleable.MinMaxNumberPicker_min, 0));
            setMaxValue(ta.getInt(R.styleable.MinMaxNumberPicker_max, 0));
        } finally {
            ta.recycle();
        }
    }
}

