package eu.kanade.tachiyomi.ui.setting;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity;

public class SettingsNestedFragment extends PreferenceFragment {

    protected PreferencesHelper preferences;

    private static final String RESOURCE_FILE = "resource_file";
    private static final String TOOLBAR_TITLE = "toolbar_title";

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsNestedFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getArguments().getInt(RESOURCE_FILE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        preferences = getSettingsActivity().preferences;
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity) getActivity())
                .setToolbarTitle(getString(getArguments().getInt(TOOLBAR_TITLE)));
    }

    public void setArgs(int resourcePreference, int resourceTitle) {
        Bundle args = new Bundle();
        args.putInt(RESOURCE_FILE, resourcePreference);
        args.putInt(TOOLBAR_TITLE, resourceTitle);
        setArguments(args);
    }

    public SettingsActivity getSettingsActivity() {
        return (SettingsActivity) getActivity();
    }

}