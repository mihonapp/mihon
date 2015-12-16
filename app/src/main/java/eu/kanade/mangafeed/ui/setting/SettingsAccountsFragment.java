package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.ui.setting.preference.MangaSyncLoginDialog;
import eu.kanade.mangafeed.ui.setting.preference.SourceLoginDialog;
import rx.Observable;

public class SettingsAccountsFragment extends SettingsNestedFragment {

    @Inject SourceManager sourceManager;
    @Inject MangaSyncManager syncManager;

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsAccountsFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedSate) {
        View view = super.onCreateView(inflater, container, savedSate);

        PreferenceScreen screen = getPreferenceScreen();

        List<Source> sourceAccounts = getSourcesWithLogin();

        PreferenceCategory sourceCategory = new PreferenceCategory(screen.getContext());
        sourceCategory.setTitle("Sources");
        screen.addPreference(sourceCategory);

        for (Source source : sourceAccounts) {
            SourceLoginDialog dialog = new SourceLoginDialog(
                    screen.getContext(), preferences, source);
            dialog.setTitle(source.getName());

            sourceCategory.addPreference(dialog);
        }

        PreferenceCategory mangaSyncCategory = new PreferenceCategory(screen.getContext());
        mangaSyncCategory.setTitle("Sync");
        screen.addPreference(mangaSyncCategory);

        for (MangaSyncService sync : syncManager.getSyncServices()) {
            MangaSyncLoginDialog dialog = new MangaSyncLoginDialog(
                    screen.getContext(), preferences, sync);
            dialog.setTitle(sync.getName());

            mangaSyncCategory.addPreference(dialog);
        }

        return view;
    }

    private List<Source> getSourcesWithLogin() {
        return Observable.from(sourceManager.getSources())
                .filter(Source::isLoginRequired)
                .toList()
                .toBlocking()
                .single();
    }

}
