package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.ui.activity.base.BaseActivity;
import eu.kanade.mangafeed.ui.dialog.LoginDialogPreference;
import rx.Observable;

public class SettingsAccountsFragment extends PreferenceFragment {

    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;

    public static SettingsAccountsFragment newInstance() {
        return new SettingsAccountsFragment();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);

        addPreferencesFromResource(R.xml.pref_accounts);

        PreferenceScreen screen = getPreferenceScreen();

        List<Source> sourceAccounts = getSourcesWithLogin();

        for (Source source : sourceAccounts) {
            LoginDialogPreference dialog = new LoginDialogPreference(
                    screen.getContext(), preferences, source);
            dialog.setTitle(source.getName());

            screen.addPreference(dialog);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity)getActivity())
                .setToolbarTitle(getString(R.string.pref_category_accounts));
    }

    private List<Source> getSourcesWithLogin() {
        return Observable.from(sourceManager.getSources())
                .filter(Source::isLoginRequired)
                .toList()
                .toBlocking()
                .single();
    }

}
