package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.activity.base.BaseActivity;

public class SettingsMainFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_main);

        registerSubpreference(R.string.pref_category_reader_key,
                SettingsNestedFragment.newInstance(
                        R.xml.pref_reader, R.string.pref_category_reader));

        registerSubpreference(R.string.pref_category_accounts_key,
                SettingsAccountsFragment.newInstance());
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity)getActivity())
                .setToolbarTitle(getString(R.string.settings_title));
    }

    private void registerSubpreference(int preferenceResource, PreferenceFragment fragment) {
        findPreference(getString(preferenceResource))
                .setOnPreferenceClickListener(preference -> {
                    getFragmentManager().beginTransaction()
                            .replace(R.id.settings_content, fragment)
                            .addToBackStack(fragment.getClass().getSimpleName()).commit();
                    return true;
                });
    }

}