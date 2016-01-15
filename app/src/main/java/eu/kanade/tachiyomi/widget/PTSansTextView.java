package eu.kanade.tachiyomi.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

import eu.kanade.tachiyomi.R;


public class PTSansTextView extends TextView {
    private final static int PTSANS_NARROW = 0;
    private final static int PTSANS_NARROW_BOLD = 1;


    public PTSansTextView(Context c) {
        super(c);
    }

    public PTSansTextView(Context c, AttributeSet attrs) {
        super(c, attrs);
        parseAttributes(c, attrs);
    }

    public PTSansTextView(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);
        parseAttributes(c, attrs);
    }

    private void parseAttributes(Context c, AttributeSet attrs) {
        TypedArray values = c.obtainStyledAttributes(attrs, R.styleable.PTSansTextView);

        //The value 0 is a default, but shouldn't ever be used since the attr is an enum
        int typeface = values.getInt(R.styleable.PTSansTextView_typeface, 0);

        switch(typeface) {
            case PTSANS_NARROW:
                //You can instantiate your typeface anywhere, I would suggest as a
                //singleton somewhere to avoid unnecessary copies
                setTypeface(Typeface.createFromAsset(c.getAssets(), "fonts/PTSans-Narrow.ttf"));
                break;
            case PTSANS_NARROW_BOLD:
                setTypeface(Typeface.createFromAsset(c.getAssets(), "fonts/PTSans-NarrowBold.ttf"));
                break;
            default:
                throw new IllegalArgumentException("Font not found " + typeface);
        }

        values.recycle();
    }

    @Override
    public void draw(Canvas canvas) {
        // Draw two times for a more visible shadow around the text
        super.draw(canvas);
        super.draw(canvas);
    }
}
