package eu.kanade.mangafeed.ui.preference;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.util.DiskUtils;

public class SettingsDownloadsFragment extends PreferenceFragment {

    @Inject PreferencesHelper preferences;

    public static SettingsDownloadsFragment newInstance() {
        return new SettingsDownloadsFragment();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);

        addPreferencesFromResource(R.xml.pref_downloads);

        PreferenceScreen screen = getPreferenceScreen();

        ListPreference directoriesPref = new ListPreference(getActivity(), null);

        String[] externalDirs = DiskUtils.getStorageDirectories(getActivity());
        directoriesPref.setKey(getString(R.string.pref_download_directory_key));
        directoriesPref.setTitle(R.string.pref_download_directory);
        directoriesPref.setEntryValues(externalDirs);
        directoriesPref.setEntries(externalDirs);
        directoriesPref.setSummary(preferences.getDownloadsDirectory());

        directoriesPref.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(newValue.toString());
            return true;
        });

        screen.addPreference(directoriesPref);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity)getActivity())
                .setToolbarTitle(getString(R.string.pref_category_downloads));
    }

}
