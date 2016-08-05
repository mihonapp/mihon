package exh;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

//Performs an update check on press
public class CheckUpdatePref extends Preference {

    public CheckUpdatePref(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckUpdatePref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckUpdatePref(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();
        new Thread(new Runnable() {
            @Override public void run() {
                ActivityAskUpdate.checkAndDoUpdateIfNeeded(CheckUpdatePref.this.getContext(), false);
            }
        }).start();
    }
}