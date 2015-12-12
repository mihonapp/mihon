package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import eu.kanade.mangafeed.BuildConfig;
import eu.kanade.mangafeed.R;

public class SettingsAboutFragment extends SettingsNestedFragment {

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsAboutFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {

        Preference version = findPreference(getString(R.string.pref_version));
        Preference buildTime = findPreference(getString(R.string.pref_build_time));

        version.setSummary(BuildConfig.DEBUG ? "r" + BuildConfig.COMMIT_COUNT :
                BuildConfig.VERSION_NAME);

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
}
