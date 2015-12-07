package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;

public class SettingsNestedFragment extends PreferenceFragment {

    protected PreferencesHelper preferences;

    private static final String RESOURCE_FILE = "resource_file";
    private static final String TOOLBAR_TITLE = "toolbar_title";

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsNestedFragment();
        fragment.setBundle(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSettingsActivity().preferences;
        addPreferencesFromResource(getArguments().getInt(RESOURCE_FILE));
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity) getActivity())
                .setToolbarTitle(getString(getArguments().getInt(TOOLBAR_TITLE)));
    }

    public void setBundle(int resourcePreference, int resourceTitle) {
        Bundle args = new Bundle();
        args.putInt(RESOURCE_FILE, resourcePreference);
        args.putInt(TOOLBAR_TITLE, resourceTitle);
        setArguments(args);
    }

    public SettingsActivity getSettingsActivity() {
        return (SettingsActivity) getActivity();
    }

}