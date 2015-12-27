package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.ui.setting.preference.LibraryColumnsDialog;

public class SettingsGeneralFragment extends SettingsNestedFragment {

    private LibraryColumnsDialog columnsDialog;

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsGeneralFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = super.onCreateView(inflater, container, savedState);

        PreferencesHelper preferences = getSettingsActivity().preferences;

        columnsDialog = (LibraryColumnsDialog) findPreference(
                getString(R.string.pref_library_columns_dialog_key));

        columnsDialog.setPreferencesHelper(preferences);

        return view;
    }

}
