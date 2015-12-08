package eu.kanade.mangafeed.ui.setting;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nononsenseapps.filepicker.AbstractFilePickerFragment;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.FilePickerFragment;
import com.nononsenseapps.filepicker.LogicHandler;

import java.io.File;

import eu.kanade.mangafeed.R;

public class SettingsDownloadsFragment extends SettingsNestedFragment {

    Preference downloadDirPref;

    public static final int DOWNLOAD_DIR_CODE = 1;

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsDownloadsFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        downloadDirPref = findPreference(getString(R.string.pref_download_directory_key));

        downloadDirPref.setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(getActivity(), CustomLayoutPickerActivity.class);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, preferences.getDownloadsDirectory());

            startActivityForResult(i, DOWNLOAD_DIR_CODE);
            return true;
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        downloadDirPref.setSummary(preferences.getDownloadsDirectory());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DOWNLOAD_DIR_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            preferences.setDownloadsDirectory(uri.getPath());
        }
    }

    public static class CustomLayoutPickerActivity extends FilePickerActivity {

        @Override
        protected AbstractFilePickerFragment<File> getFragment(
                String startPath, int mode, boolean allowMultiple, boolean allowCreateDir) {
            AbstractFilePickerFragment<File> fragment = new CustomLayoutFilePickerFragment();
            fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir);
            return fragment;
        }
    }

    public static class CustomLayoutFilePickerFragment extends FilePickerFragment {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            switch (viewType) {
                case LogicHandler.VIEWTYPE_DIR:
                    v = LayoutInflater.from(getActivity()).inflate(R.layout.listitem_dir,
                            parent, false);
                    return new DirViewHolder(v);
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
        }
    }

}
