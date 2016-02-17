package eu.kanade.tachiyomi.ui.setting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.library.LibraryUpdateAlarm;
import eu.kanade.tachiyomi.widget.preference.IntListPreference;
import eu.kanade.tachiyomi.widget.preference.LibraryColumnsDialog;

public class SettingsGeneralFragment extends SettingsNestedFragment {

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsGeneralFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = super.onCreateView(inflater, container, savedState);

        PreferencesHelper preferences = getSettingsActivity().preferences;

        LibraryColumnsDialog columnsDialog = (LibraryColumnsDialog) findPreference(
                getString(R.string.pref_library_columns_dialog_key));

        columnsDialog.setPreferencesHelper(preferences);

        IntListPreference updateInterval = (IntListPreference) findPreference(
                getString(R.string.pref_library_update_interval_key));

        updateInterval.setOnPreferenceChangeListener((preference, newValue) -> {
            LibraryUpdateAlarm.startAlarm(getActivity(), Integer.parseInt((String) newValue));
            return true;
        });

        return view;
    }

}
