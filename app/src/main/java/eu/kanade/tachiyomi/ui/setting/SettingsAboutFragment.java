package eu.kanade.tachiyomi.ui.setting;

import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import eu.kanade.tachiyomi.BuildConfig;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.updater.GithubUpdateChecker;
import eu.kanade.tachiyomi.data.updater.UpdateDownloader;
import eu.kanade.tachiyomi.util.ToastUtil;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class SettingsAboutFragment extends SettingsNestedFragment {
    /**
     * Checks for new releases
     */
    private GithubUpdateChecker updateChecker;

    /**
     * The subscribtion service of the obtained release object
     */
    private Subscription releaseSubscription;

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsAboutFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Check for update
        updateChecker = new GithubUpdateChecker(getActivity());

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        if (releaseSubscription != null)
            releaseSubscription.unsubscribe();

        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        Preference version = findPreference(getString(R.string.pref_version));
        Preference buildTime = findPreference(getString(R.string.pref_build_time));

        version.setSummary(BuildConfig.DEBUG ? "r" + BuildConfig.COMMIT_COUNT :
                BuildConfig.VERSION_NAME);

        //Set onClickListener to check for new version
        version.setOnPreferenceClickListener(preference -> {
            if (!BuildConfig.DEBUG && BuildConfig.INCLUDE_UPDATER)
                checkVersion();
            return true;
        });

        buildTime.setSummary(getFormattedBuildTime());

        return super.onCreateView(inflater, container, savedState);
    }

    private String getFormattedBuildTime() {
        try {
            DateFormat inputDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            inputDf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputDf.parse(BuildConfig.BUILD_TIME);

            DateFormat outputDf = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
            outputDf.setTimeZone(TimeZone.getDefault());

            return outputDf.format(date);
        } catch (ParseException e) {
            // Do nothing
        }
        return "";
    }

    /**
     * Checks version and shows a user prompt when update available.
     */
    private void checkVersion() {
        releaseSubscription = updateChecker.checkForApplicationUpdate()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(release -> {
                    //Get version of latest release
                    String newVersion = release.getVersion();
                    newVersion = newVersion.replaceAll("[^\\d.]", "");

                    //Check if latest version is different from current version
                    if (!newVersion.equals(BuildConfig.VERSION_NAME)) {
                        String downloadLink = release.getDownloadLink();
                        String body = release.getChangeLog();

                        //Create confirmation window
                        new MaterialDialog.Builder(getActivity())
                                .title(getString(R.string.update_check_title))
                                .content(body)
                                .positiveText(getString(R.string.update_check_confirm))
                                .negativeText(getString(R.string.update_check_ignore))
                                .onPositive((dialog, which) -> {
                                    // User output that download has started
                                    ToastUtil.showShort(getActivity(), getString(R.string.update_check_download_started));
                                    // Start download
                                    new UpdateDownloader(getActivity().getApplicationContext()).execute(downloadLink);
                                })
                                .show();
                    } else {
                        ToastUtil.showShort(getActivity(), getString(R.string.update_check_no_new_updates));
                    }
                }, Throwable::printStackTrace);
    }
}
