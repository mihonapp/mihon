package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import eu.kanade.mangafeed.ui.activity.base.BaseActivity;

public class SettingsNestedFragment extends PreferenceFragment {

    private static final String RESOURCE_FILE = "resource_file";
    private static final String TOOLBAR_TITLE = "toolbar_title";

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsNestedFragment();
        Bundle args = new Bundle();
        args.putInt(RESOURCE_FILE, resourcePreference);
        args.putInt(TOOLBAR_TITLE, resourceTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getArguments().getInt(RESOURCE_FILE));

    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity)getActivity())
                .setToolbarTitle(getString(getArguments().getInt(TOOLBAR_TITLE)));
    }

}