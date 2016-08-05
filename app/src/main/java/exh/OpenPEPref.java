package exh;

import android.content.Context;
import android.content.Intent;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

public class OpenPEPref extends Preference {

    public OpenPEPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OpenPEPref(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OpenPEPref(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();

        Intent openPeIntent = new Intent(this.getContext(), ActivityPE.class);
        this.getContext().startActivity(openPeIntent);
    }
}