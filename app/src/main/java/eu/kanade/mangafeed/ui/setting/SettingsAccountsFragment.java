package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.chaptersync.BaseChapterSync;
import eu.kanade.mangafeed.data.chaptersync.ChapterSyncManager;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.ui.setting.dialog.ChapterSyncLoginDialog;
import eu.kanade.mangafeed.ui.setting.dialog.SourceLoginDialog;
import rx.Observable;

public class SettingsAccountsFragment extends PreferenceFragment {

    @Inject PreferencesHelper preferences;
    @Inject SourceManager sourceManager;
    @Inject ChapterSyncManager syncManager;

    public static SettingsAccountsFragment newInstance() {
        return new SettingsAccountsFragment();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);

        addPreferencesFromResource(R.xml.pref_accounts);

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

        PreferenceCategory chapterSyncCategory = new PreferenceCategory(screen.getContext());
        chapterSyncCategory.setTitle("Sync");
        screen.addPreference(chapterSyncCategory);

        for (BaseChapterSync sync : syncManager.getChapterSyncServices()) {
            ChapterSyncLoginDialog dialog = new ChapterSyncLoginDialog(
                    screen.getContext(), preferences, sync);
            dialog.setTitle(sync.getName());

            chapterSyncCategory.addPreference(dialog);
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
