package eu.kanade.mangafeed.data.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.sources.base.Source;

public class PreferencesHelper {

    private static SharedPreferences mPref;
    private Context context;

    private static final String SOURCE_ACCOUNT_USERNAME = "pref_source_username_";
    private static final String SOURCE_ACCOUNT_PASSWORD = "pref_source_password_";

    public PreferencesHelper(Context context) {
        this.context = context;
        PreferenceManager.setDefaultValues(context, R.xml.pref_reader, false);

        mPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private String getKey(int keyResource) {
        return context.getString(keyResource);
    }

    public void clear() {
        mPref.edit().clear().apply();
    }

    public boolean useFullscreenSet() {
        return mPref.getBoolean(getKey(R.string.pref_fullscreen_key), false);
    }

    public int getDefaultViewer() {
        return Integer.parseInt(mPref.getString(getKey(R.string.pref_default_viewer_key), "1"));
    }

    public String getSourceUsername(Source source) {
        return mPref.getString(SOURCE_ACCOUNT_USERNAME + source.getSourceId(), "");
    }

    public String getSourcePassword(Source source) {
        return mPref.getString(SOURCE_ACCOUNT_PASSWORD + source.getSourceId(), "");
    }

    public void setSourceCredentials(Source source, String username, String password) {
        mPref.edit()
                .putString(SOURCE_ACCOUNT_USERNAME + source.getSourceId(), username)
                .putString(SOURCE_ACCOUNT_PASSWORD + source.getSourceId(), password)
                .apply();
    }

}
