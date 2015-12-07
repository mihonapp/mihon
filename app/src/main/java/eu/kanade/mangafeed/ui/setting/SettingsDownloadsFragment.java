package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.util.DiskUtils;

public class SettingsDownloadsFragment extends SettingsNestedFragment {

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsDownloadsFragment();
        fragment.setBundle(resourcePreference, resourceTitle);
        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

}
