package eu.kanade.mangafeed.data.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.kanade.mangafeed.R;

public class PreferencesHelper {

    private static SharedPreferences mPref;

    private static final String PREF_HIDE_STATUS_BAR = "hide_status_bar";

    public PreferencesHelper(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

        mPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void clear() {
        mPref.edit().clear().apply();
    }

    public boolean hideStatusBarSet() {
        return mPref.getBoolean(PREF_HIDE_STATUS_BAR, false);
    }

}
