package eu.kanade.mangafeed.data.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.f2prateek.rx.preferences.RxSharedPreferences;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.util.DiskUtils;
import rx.Observable;

public class PreferencesHelper {

    private Context context;
    private SharedPreferences prefs;
    private RxSharedPreferences rxPrefs;

    private static final String SOURCE_ACCOUNT_USERNAME = "pref_source_username_";
    private static final String SOURCE_ACCOUNT_PASSWORD = "pref_source_password_";

    public PreferencesHelper(Context context) {
        this.context = context;
        PreferenceManager.setDefaultValues(context, R.xml.pref_reader, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        rxPrefs = RxSharedPreferences.create(prefs);
    }

    private String getKey(int keyResource) {
        return context.getString(keyResource);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public boolean useFullscreenSet() {
        return prefs.getBoolean(getKey(R.string.pref_fullscreen_key), false);
    }

    public int getDefaultViewer() {
        return Integer.parseInt(prefs.getString(getKey(R.string.pref_default_viewer_key), "1"));
    }

    public String getSourceUsername(Source source) {
        return prefs.getString(SOURCE_ACCOUNT_USERNAME + source.getSourceId(), "");
    }

    public String getSourcePassword(Source source) {
        return prefs.getString(SOURCE_ACCOUNT_PASSWORD + source.getSourceId(), "");
    }

    public void setSourceCredentials(Source source, String username, String password) {
        prefs.edit()
                .putString(SOURCE_ACCOUNT_USERNAME + source.getSourceId(), username)
                .putString(SOURCE_ACCOUNT_PASSWORD + source.getSourceId(), password)
                .apply();
    }

    public String getDownloadsDirectory() {
        return prefs.getString(getKey(R.string.pref_download_directory_key),
                DiskUtils.getStorageDirectories(context)[0]);
    }

    public int getDownloadThreads() {
        return Integer.parseInt(prefs.getString(getKey(R.string.pref_download_threads_key), "1"));
    }

    public Observable<Integer> getDownloadTheadsObservable() {
        return rxPrefs.getString(getKey(R.string.pref_download_threads_key), "1")
                .asObservable().map(Integer::parseInt);
    }

}
